package com.example.data.importexport

import android.util.Xml
import com.example.data.local.WordEntity
import com.example.domain.DuplicateDetector
import com.example.domain.DuplicateMatch
import com.example.domain.WordNormalizer
import com.example.model.DuplicateStrategy
import com.example.model.ImportColumnMapping
import com.example.model.ImportPreviewRow
import com.example.model.PartOfSpeech
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class ParsedTabularData(
    val headers: List<String>,
    val rows: List<Map<String, String>>,
    val sourceFormat: String
)

object ImportParser {
    fun parseCsv(content: String): ParsedTabularData {
        val cleaned = content.removePrefix("\uFEFF")
        val records = parseCsvRecords(cleaned)
        if (records.isEmpty()) return ParsedTabularData(emptyList(), emptyList(), "CSV")
        val headers = records.first().mapIndexed { idx, h ->
            h.trim().ifBlank { "col_${idx + 1}" }
        }
        val rows = records.drop(1)
            .filter { row -> row.any { it.isNotBlank() } }
            .map { row ->
                headers.mapIndexed { idx, header ->
                    header to (row.getOrNull(idx)?.trim() ?: "")
                }.toMap()
            }
        return ParsedTabularData(headers, rows, "CSV")
    }

    fun parsePastedText(content: String): ParsedTabularData {
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParsedTabularData(emptyList(), emptyList(), "TEXT")
        val delimiter = when {
            lines.first().contains('\t') -> '\t'
            lines.first().contains(',') -> ','
            lines.first().contains(';') -> ';'
            lines.first().contains('|') -> '|'
            else -> ','
        }
        if (delimiter == ',') {
            return parseCsv(content).copy(sourceFormat = "TEXT")
        }
        val firstCells = lines.first().split(delimiter).map { it.trim() }
        val looksLikeHeader = firstCells.any { cell ->
            val lower = cell.lowercase(Locale.ROOT)
            lower in setOf("word", "english", "meaning", "arabic", "pos", "partofspeech", "الكلمة", "المعنى")
        }
        val headers = if (looksLikeHeader) {
            firstCells.mapIndexed { idx, h -> h.ifBlank { "col_${idx + 1}" } }
        } else {
            listOf("word", "primaryMeaning", "partOfSpeech", "examples").take(maxOf(2, firstCells.size))
        }
        val dataLines = if (looksLikeHeader) lines.drop(1) else lines
        val rows = dataLines.map { line ->
            val cells = line.split(delimiter).map { it.trim() }
            headers.mapIndexed { idx, h -> h to (cells.getOrNull(idx) ?: "") }.toMap()
        }
        return ParsedTabularData(headers, rows, "TEXT")
    }

    fun parseJson(content: String): ParsedTabularData {
        val trimmed = content.trim().removePrefix("\uFEFF")
        val jsonArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("words") ?: JSONArray().apply { put(root) }
            }
            else -> throw IllegalArgumentException("صيغة JSON غير صالحة للاستيراد.")
        }

        val headerSet = linkedSetOf<String>()
        val rawObjects = mutableListOf<JSONObject>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            rawObjects.add(obj)
            val keys = obj.keys()
            while (keys.hasNext()) {
                headerSet.add(keys.next())
            }
        }
        val headers = headerSet.toList()
        val rows = rawObjects.map { obj ->
            headers.associateWith { key ->
                when (val v = obj.opt(key)) {
                    null, JSONObject.NULL -> ""
                    is JSONArray -> (0 until v.length()).joinToString("; ") { idx -> v.optString(idx) }
                    else -> v.toString().trim()
                }
            }
        }
        return ParsedTabularData(headers, rows, "JSON")
    }

    fun parseXlsx(inputStream: InputStream): ParsedTabularData {
        var sharedStrings = emptyList<String>()
        var sheetXmlBytes: ByteArray? = null

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> {
                        sharedStrings = parseSharedStrings(zis.readBytes())
                    }
                    "xl/worksheets/sheet1.xml" -> {
                        sheetXmlBytes = zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sheetBytes = sheetXmlBytes
            ?: throw IllegalArgumentException("ملف XLSX لا يحتوي على ورقة عمل أولى صالحة (sheet1.xml).")
        val grid = parseSheetRows(sheetBytes, sharedStrings)
        if (grid.isEmpty()) return ParsedTabularData(emptyList(), emptyList(), "XLSX")
        val maxCols = grid.maxOf { it.size }
        val headers = (0 until maxCols).map { idx ->
            grid.first().getOrNull(idx)?.trim().orEmpty().ifBlank { "col_${idx + 1}" }
        }
        val rows = grid.drop(1)
            .filter { row -> row.any { it.isNotBlank() } }
            .map { row ->
                headers.mapIndexed { idx, header ->
                    header to (row.getOrNull(idx)?.trim() ?: "")
                }.toMap()
            }
        return ParsedTabularData(headers, rows, "XLSX")
    }

    fun detectColumnMapping(headers: List<String>): ImportColumnMapping {
        fun match(vararg candidates: String): String? {
            return headers.firstOrNull { h ->
                val norm = h.trim().lowercase(Locale.ROOT).replace("_", "").replace(" ", "")
                candidates.any { c -> norm == c || norm.contains(c) }
            }
        }
        return ImportColumnMapping(
            wordColumn = match("word", "english", "term", "vocab", "الكلمة", "المفردة"),
            partOfSpeechColumn = match("partofspeech", "pos", "type", "قسمالكلام", "النوع"),
            primaryMeaningColumn = match("primarymeaning", "meaning", "arabicmeaning", "translation", "arabic", "المعنىالأساسي", "المعنى"),
            additionalMeaningsColumn = match("additionalmeanings", "othermeanings", "meanings", "معانيإضافية"),
            englishDefinitionColumn = match("englishdefinition", "definition", "def", "التعريف"),
            arabicExplanationColumn = match("arabicexplanation", "explanation", "شرح", "الشرح"),
            phoneticColumn = match("phonetic", "ipa", "pronunciation", "النطق"),
            examplesColumn = match("examples", "sentences", "example", "الأمثلة", "مثال"),
            synonymsColumn = match("synonyms", "synonym", "المرادفات"),
            antonymsColumn = match("antonyms", "antonym", "المتضادات"),
            derivativesColumn = match("derivatives", "derivative", "المشتقات"),
            expressionsColumn = match("expressions", "collocations", "idioms", "التعبيرات"),
            bookNameColumn = match("book", "bookname", "source", "الكتاب"),
            unitColumn = match("unit", "الوحدة"),
            lessonColumn = match("lesson", "الدرس"),
            pageColumn = match("page", "pagenumber", "الصفحة")
        )
    }

    fun buildPreviewRows(
        tabular: ParsedTabularData,
        mapping: ImportColumnMapping,
        existingWords: List<WordEntity>
    ): List<ImportPreviewRow> {
        val seenInBatch = mutableSetOf<String>()
        return tabular.rows.mapIndexed { idx, row ->
            val rawWord = mapping.wordColumn?.let { row[it] }.orEmpty()
            val cleanedWord = WordNormalizer.cleanDisplayWord(rawWord)
            val normalized = WordNormalizer.normalizeWord(cleanedWord)
            val pos = PartOfSpeech.safeValueOf(mapping.partOfSpeechColumn?.let { row[it] })
            val primaryMeaning = WordNormalizer.cleanDisplayWord(
                mapping.primaryMeaningColumn?.let { row[it] }.orEmpty()
            )

            fun splitList(col: String?): List<String> =
                col?.let { row[it] }
                    ?.split(";", "|", "،")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

            val validationError = when {
                cleanedWord.isBlank() -> "حقل الكلمة الإنجليزية مطلوب"
                primaryMeaning.isBlank() -> "حقل المعنى الأساسي مطلوب"
                else -> null
            }

            val dbMatches = if (validationError == null) {
                DuplicateDetector.findMatches(cleanedWord, pos, existingWords)
            } else emptyList()

            val exactMatch = dbMatches.firstOrNull {
                it.matchType == DuplicateMatch.MatchType.EXACT_WORD_AND_POS
            } ?: dbMatches.firstOrNull()

            val batchKey = "${normalized}__${pos.name}"
            val isBatchDup = normalized.isNotBlank() && !seenInBatch.add(batchKey)

            val matchedReason = when {
                exactMatch != null -> exactMatch.reasonArabic
                isBatchDup -> "مكرر داخل نفس ملف الاستيراد بنفس قسم الكلام"
                else -> null
            }

            ImportPreviewRow(
                rowIndex = idx + 1,
                rawWord = cleanedWord,
                normalizedWord = normalized,
                partOfSpeech = pos,
                primaryMeaning = primaryMeaning,
                additionalMeanings = splitList(mapping.additionalMeaningsColumn),
                englishDefinition = mapping.englishDefinitionColumn?.let { row[it] }.orEmpty().trim(),
                arabicExplanation = mapping.arabicExplanationColumn?.let { row[it] }.orEmpty().trim(),
                phoneticText = mapping.phoneticColumn?.let { row[it] }.orEmpty().trim(),
                exampleSentences = splitList(mapping.examplesColumn),
                synonyms = splitList(mapping.synonymsColumn),
                antonyms = splitList(mapping.antonymsColumn),
                derivatives = splitList(mapping.derivativesColumn),
                expressions = splitList(mapping.expressionsColumn),
                bookName = mapping.bookNameColumn?.let { row[it] }.orEmpty().trim(),
                unit = mapping.unitColumn?.let { row[it] }.orEmpty().trim(),
                lesson = mapping.lessonColumn?.let { row[it] }.orEmpty().trim(),
                pageNumber = mapping.pageColumn?.let { row[it] }.orEmpty().trim(),
                matchedWordId = exactMatch?.existingWord?.id,
                matchedHumanId = exactMatch?.existingWord?.humanId,
                matchedReason = matchedReason,
                validationError = validationError,
                strategy = if (exactMatch != null || isBatchDup) DuplicateStrategy.SKIP else DuplicateStrategy.MERGE
            )
        }
    }

    private fun parseCsvRecords(input: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < input.length && input[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    currentRow.add(field.toString())
                    field.clear()
                }
                c == '\r' -> {
                    if (i + 1 < input.length && input[i + 1] == '\n') i++
                    currentRow.add(field.toString())
                    field.clear()
                    records.add(currentRow)
                    currentRow = mutableListOf()
                }
                c == '\n' -> {
                    currentRow.add(field.toString())
                    field.clear()
                    records.add(currentRow)
                    currentRow = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            records.add(currentRow)
        }
        return records
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(xmlBytes.inputStream(), "UTF-8")
        var insideSi = false
        val sb = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        insideSi = true
                        sb.clear()
                    } else if (insideSi && parser.name == "t") {
                        sb.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        list.add(sb.toString())
                        insideSi = false
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseSheetRows(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(xmlBytes.inputStream(), "UTF-8")
        var currentRow: MutableMap<Int, String>? = null
        var currentColIdx = 0
        var currentType = ""
        var inInlineStr = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableMapOf()
                        "c" -> {
                            val ref = parser.getAttributeValue(null, "r") ?: ""
                            currentColIdx = columnRefToIndex(ref, currentRow?.size ?: 0)
                            currentType = parser.getAttributeValue(null, "t") ?: ""
                        }
                        "is" -> inInlineStr = true
                        "t" -> if (inInlineStr && currentRow != null) {
                            currentRow[currentColIdx] = parser.nextText()
                        }
                        "v" -> if (currentRow != null) {
                            val raw = parser.nextText()
                            val value = if (currentType == "s") {
                                raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                            } else raw
                            currentRow[currentColIdx] = value
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "is" -> inInlineStr = false
                        "row" -> {
                            val rowMap = currentRow
                            if (rowMap != null) {
                                val max = (rowMap.keys.maxOrNull() ?: -1) + 1
                                rows.add((0 until max).map { rowMap[it] ?: "" })
                            }
                            currentRow = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun columnRefToIndex(cellRef: String, fallback: Int): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
        if (letters.isEmpty()) return fallback
        var index = 0
        for (ch in letters) {
            index = index * 26 + (ch - 'A' + 1)
        }
        return index - 1
    }
}
