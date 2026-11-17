package com.example.data.importexport

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.example.data.local.CurriculumEntryEntity
import com.example.data.local.WordEntity
import com.example.data.local.WordLocationEntity
import com.example.domain.WordJsonCodec
import com.example.model.CurriculumEntryType
import com.example.model.ExportOptions
import com.example.model.PartOfSpeech
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportGenerator {

    fun writeCsv(
        output: OutputStream,
        words: List<WordEntity>,
        locationsByWord: Map<String, List<WordLocationEntity>>,
        curriculumByWord: Map<String, List<CurriculumEntryEntity>>,
        options: ExportOptions
    ) {
        // UTF-8 BOM for proper Arabic display in Microsoft Excel & Google Sheets
        output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val headers = mutableListOf("ID", "Word", "PartOfSpeech", "PrimaryMeaning", "EnglishDefinition", "ArabicExplanation", "Phonetic")
        if (options.includeMeanings) headers.add("AdditionalMeanings")
        if (options.includeSynonymsAntonyms) {
            headers.add("Synonyms")
            headers.add("Antonyms")
        }
        if (options.includeDerivatives) headers.add("Derivatives")
        if (options.includeExpressions) headers.add("Expressions")
        if (options.includeBookLocations) headers.add("BookReferences")
        if (options.includeCurriculum) headers.add("CurriculumAndObservations")
        if (options.includeNotes) headers.add("Notes")

        val sb = StringBuilder()
        sb.appendLine(headers.joinToString(",") { csvEscape(it) })

        for (w in words) {
            val row = mutableListOf(
                w.humanId,
                w.word,
                PartOfSpeech.safeValueOf(w.partOfSpeech).arabicLabel,
                w.primaryMeaning,
                w.englishDefinition,
                w.arabicExplanation,
                w.phoneticText
            )
            if (options.includeMeanings) {
                row.add(WordJsonCodec.decodeMeanings(w.meaningsJson).joinToString(" | ") { it.arabicMeaning })
            }
            if (options.includeSynonymsAntonyms) {
                row.add(WordJsonCodec.decodeSynonyms(w.synonymsJson).joinToString(" | ") { "${it.word} (${it.meaning})" })
                row.add(WordJsonCodec.decodeAntonyms(w.antonymsJson).joinToString(" | ") { "${it.word} (${it.meaning})" })
            }
            if (options.includeDerivatives) {
                row.add(WordJsonCodec.decodeDerivatives(w.derivativesJson).joinToString(" | ") { "${it.word} [${it.formType.arabicLabel}]" })
            }
            if (options.includeExpressions) {
                row.add(WordJsonCodec.decodeExpressions(w.expressionsJson).joinToString(" | ") { "${it.expression}: ${it.arabicExplanation}" })
            }
            if (options.includeBookLocations) {
                val locs = locationsByWord[w.id].orEmpty()
                row.add(locs.joinToString(" | ") { "${it.bookName} - وحدة ${it.unit} درس ${it.lesson} ص ${it.pageNumber}" })
            }
            if (options.includeCurriculum) {
                val cur = curriculumByWord[w.id].orEmpty()
                row.add(cur.joinToString(" | ") { "[${CurriculumEntryType.safeValueOf(it.entryType).arabicLabel}] ${it.curriculumDistinction} ${it.importantExamNotes}" })
            }
            if (options.includeNotes) {
                row.add(WordJsonCodec.decodeNotes(w.notesJson).joinToString(" | ") { it.content })
            }
            sb.appendLine(row.joinToString(",") { csvEscape(it) })
        }
        output.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    fun writeJson(
        output: OutputStream,
        words: List<WordEntity>,
        locationsByWord: Map<String, List<WordLocationEntity>>,
        curriculumByWord: Map<String, List<CurriculumEntryEntity>>
    ) {
        val arr = JSONArray()
        for (w in words) {
            arr.put(JSONObject().apply {
                put("id", w.id)
                put("humanId", w.humanId)
                put("word", w.word)
                put("normalizedWord", w.normalizedWord)
                put("partOfSpeech", w.partOfSpeech)
                put("primaryMeaning", w.primaryMeaning)
                put("englishDefinition", w.englishDefinition)
                put("arabicExplanation", w.arabicExplanation)
                put("phoneticText", w.phoneticText)
                put("exampleSentences", JSONArray(WordJsonCodec.decodeStringList(w.exampleSentencesJson)))
                put("meanings", JSONArray(w.meaningsJson))
                put("synonyms", JSONArray(w.synonymsJson))
                put("antonyms", JSONArray(w.antonymsJson))
                put("derivatives", JSONArray(w.derivativesJson))
                put("expressions", JSONArray(w.expressionsJson))
                put("notes", JSONArray(w.notesJson))
                put("version", w.version)
                put("bookLocations", JSONArray().apply {
                    locationsByWord[w.id].orEmpty().forEach { loc ->
                        put(JSONObject().apply {
                            put("bookId", loc.bookId)
                            put("bookName", loc.bookName)
                            put("unit", loc.unit)
                            put("lesson", loc.lesson)
                            put("pageNumber", loc.pageNumber)
                            put("exerciseNumber", loc.exerciseNumber)
                            put("quotation", loc.quotation)
                        })
                    }
                })
                put("curriculumEntries", JSONArray().apply {
                    curriculumByWord[w.id].orEmpty().forEach { cur ->
                        put(JSONObject().apply {
                            put("entryType", cur.entryType)
                            put("verificationStatus", cur.verificationStatus)
                            put("curriculumDistinction", cur.curriculumDistinction)
                            put("importantExamNotes", cur.importantExamNotes)
                            put("grade", cur.grade)
                            put("unit", cur.unit)
                            put("lesson", cur.lesson)
                        })
                    }
                })
            })
        }
        val root = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("count", words.size)
            put("words", arr)
        }
        output.write(root.toString(2).toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    /**
     * Writes a multi-sheet OpenXML (.xlsx) workbook compatible with Microsoft Excel and Google Sheets.
     * Preserves stable VOC IDs and relational links across separate sheets.
     */
    fun writeXlsx(
        output: OutputStream,
        words: List<WordEntity>,
        locationsByWord: Map<String, List<WordLocationEntity>>,
        curriculumByWord: Map<String, List<CurriculumEntryEntity>>,
        options: ExportOptions
    ) {
        val sheets = mutableListOf<Pair<String, List<List<String>>>>()

        // Sheet 1: Words
        val wordsRows = mutableListOf(
            listOf("HumanID", "Word", "Normalized", "PartOfSpeech", "PrimaryMeaning", "EnglishDefinition", "ArabicExplanation", "Phonetic", "Examples", "Version")
        )
        words.forEach { w ->
            wordsRows.add(
                listOf(
                    w.humanId,
                    w.word,
                    w.normalizedWord,
                    w.partOfSpeech,
                    w.primaryMeaning,
                    w.englishDefinition,
                    w.arabicExplanation,
                    w.phoneticText,
                    WordJsonCodec.decodeStringList(w.exampleSentencesJson).joinToString("; "),
                    w.version.toString()
                )
            )
        }
        sheets.add("Words" to wordsRows)

        if (options.includeMeanings) {
            val rows = mutableListOf(listOf("WordHumanID", "Word", "ArabicMeaning", "EnglishNuance", "Example"))
            words.forEach { w ->
                WordJsonCodec.decodeMeanings(w.meaningsJson).forEach { m ->
                    rows.add(listOf(w.humanId, w.word, m.arabicMeaning, m.englishNuance, m.contextExample))
                }
            }
            sheets.add("Meanings" to rows)
        }

        if (options.includeSynonymsAntonyms) {
            val synRows = mutableListOf(listOf("WordHumanID", "Word", "Synonym", "Meaning", "Notes"))
            val antRows = mutableListOf(listOf("WordHumanID", "Word", "Antonym", "Meaning", "Notes"))
            words.forEach { w ->
                WordJsonCodec.decodeSynonyms(w.synonymsJson).forEach { s ->
                    synRows.add(listOf(w.humanId, w.word, s.word, s.meaning, s.notes))
                }
                WordJsonCodec.decodeAntonyms(w.antonymsJson).forEach { a ->
                    antRows.add(listOf(w.humanId, w.word, a.word, a.meaning, a.notes))
                }
            }
            sheets.add("Synonyms" to synRows)
            sheets.add("Antonyms" to antRows)
        }

        if (options.includeDerivatives) {
            val rows = mutableListOf(listOf("WordHumanID", "Word", "Derivative", "Form", "ArabicMeaning", "Relationship"))
            words.forEach { w ->
                WordJsonCodec.decodeDerivatives(w.derivativesJson).forEach { d ->
                    rows.add(listOf(w.humanId, w.word, d.word, d.formType.englishLabel, d.arabicMeaning, d.relationshipNote))
                }
            }
            sheets.add("Derivatives" to rows)
        }

        if (options.includeExpressions) {
            val rows = mutableListOf(listOf("WordHumanID", "Word", "Type", "Expression", "Example", "ArabicExplanation"))
            words.forEach { w ->
                WordJsonCodec.decodeExpressions(w.expressionsJson).forEach { e ->
                    rows.add(listOf(w.humanId, w.word, e.type.englishLabel, e.expression, e.exampleUsage, e.arabicExplanation))
                }
            }
            sheets.add("Expressions" to rows)
        }

        if (options.includeBookLocations) {
            val rows = mutableListOf(listOf("WordHumanID", "Word", "BookName", "Publisher", "Edition", "Unit", "Lesson", "Page", "Exercise", "Quotation"))
            words.forEach { w ->
                locationsByWord[w.id].orEmpty().forEach { l ->
                    rows.add(listOf(w.humanId, w.word, l.bookName, l.publisher, l.editionOrYear, l.unit, l.lesson, l.pageNumber, l.exerciseNumber, l.quotation))
                }
            }
            sheets.add("BookLocations" to rows)
        }

        if (options.includeCurriculum) {
            val rows = mutableListOf(listOf("WordHumanID", "Word", "EntryType", "VerificationStatus", "Grade", "Unit", "Lesson", "Distinction", "ExamNotes", "GrammarRules"))
            words.forEach { w ->
                curriculumByWord[w.id].orEmpty().forEach { c ->
                    rows.add(listOf(w.humanId, w.word, c.entryType, c.verificationStatus, c.grade, c.unit, c.lesson, c.curriculumDistinction, c.importantExamNotes, c.relatedGrammarRules))
                }
            }
            sheets.add("Curriculum" to rows)
        }

        ZipOutputStream(output).use { zos ->
            writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml(sheets.size))
            writeZipEntry(zos, "_rels/.rels", ROOT_RELS_XML)
            writeZipEntry(zos, "xl/workbook.xml", buildWorkbookXml(sheets.map { it.first }))
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheets.size))
            sheets.forEachIndexed { idx, (_, rows) ->
                writeZipEntry(zos, "xl/worksheets/sheet${idx + 1}.xml", buildWorksheetXml(rows))
            }
        }
        output.flush()
    }

    /**
     * Generates a clean bilingual Arabic-RTL / English-LTR PDF using Android PdfDocument & StaticLayout.
     */
    fun writePdf(
        output: OutputStream,
        workspaceName: String,
        words: List<WordEntity>,
        locationsByWord: Map<String, List<WordLocationEntity>>,
        curriculumByWord: Map<String, List<CurriculumEntryEntity>>,
        options: ExportOptions
    ) {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val contentWidth = (pageWidth - margin * 2).toInt()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F4C5C")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val englishWordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F4C5C")
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val arabicBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D2224")
            textSize = 11.5f
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5C6468")
            textSize = 9.5f
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#D6D3CA")
            strokeWidth = 1f
        }

        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin

        fun newPage() {
            pdf.finishPage(page)
            pageNum++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = page.canvas
            y = margin
        }

        fun drawStaticLayout(text: String, paint: TextPaint, isRtl: Boolean) {
            if (text.isBlank()) return
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(
                    if (isRtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.LTR
                )
                .setLineSpacing(2f, 1.15f)
                .build()
            if (y + layout.height > pageHeight - margin) {
                newPage()
            }
            canvas.save()
            canvas.translate(margin, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 6f
        }

        drawStaticLayout("تقرير المفردات التعليمية — $workspaceName (${words.size} كلمة)", titlePaint, true)
        y += 6f
        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 14f

        for (w in words) {
            val pos = PartOfSpeech.safeValueOf(w.partOfSpeech)
            val headerLine = "${w.humanId}  |  ${w.word}  (${pos.englishLabel} / ${pos.arabicLabel}) ${w.phoneticText}"
            drawStaticLayout(headerLine, englishWordPaint, false)
            drawStaticLayout("المعنى الأساسي: ${w.primaryMeaning}", arabicBodyPaint, true)

            if (w.englishDefinition.isNotBlank()) {
                drawStaticLayout("Definition: ${w.englishDefinition}", metaPaint, false)
            }
            if (w.arabicExplanation.isNotBlank()) {
                drawStaticLayout("الشرح: ${w.arabicExplanation}", arabicBodyPaint, true)
            }
            val examples = WordJsonCodec.decodeStringList(w.exampleSentencesJson)
            if (examples.isNotEmpty()) {
                drawStaticLayout("Examples: ${examples.joinToString(" • ")}", metaPaint, false)
            }
            if (options.includeSynonymsAntonyms) {
                val syns = WordJsonCodec.decodeSynonyms(w.synonymsJson)
                if (syns.isNotEmpty()) {
                    drawStaticLayout("المرادفات: ${syns.joinToString("، ") { "${it.word} (${it.meaning})" }}", arabicBodyPaint, true)
                }
                val ants = WordJsonCodec.decodeAntonyms(w.antonymsJson)
                if (ants.isNotEmpty()) {
                    drawStaticLayout("المتضادات: ${ants.joinToString("، ") { "${it.word} (${it.meaning})" }}", arabicBodyPaint, true)
                }
            }
            if (options.includeDerivatives) {
                val ders = WordJsonCodec.decodeDerivatives(w.derivativesJson)
                if (ders.isNotEmpty()) {
                    drawStaticLayout("المشتقات: ${ders.joinToString("، ") { "${it.word} (${it.formType.arabicLabel})" }}", arabicBodyPaint, true)
                }
            }
            if (options.includeExpressions) {
                val exps = WordJsonCodec.decodeExpressions(w.expressionsJson)
                if (exps.isNotEmpty()) {
                    drawStaticLayout("التعبيرات: ${exps.joinToString(" | ") { "${it.expression}: ${it.arabicExplanation}" }}", arabicBodyPaint, true)
                }
            }
            if (options.includeBookLocations) {
                val locs = locationsByWord[w.id].orEmpty()
                if (locs.isNotEmpty()) {
                    drawStaticLayout(
                        "المراجع: ${locs.joinToString(" | ") { "${it.bookName} (وحدة ${it.unit}، درس ${it.lesson}، ص ${it.pageNumber})" }}",
                        metaPaint,
                        true
                    )
                }
            }
            if (options.includeCurriculum) {
                val cur = curriculumByWord[w.id].orEmpty()
                if (cur.isNotEmpty()) {
                    drawStaticLayout(
                        "المنهج: ${cur.joinToString(" | ") { "${CurriculumEntryType.safeValueOf(it.entryType).arabicLabel}: ${it.curriculumDistinction} ${it.importantExamNotes}" }}",
                        metaPaint,
                        true
                    )
                }
            }

            y += 6f
            if (y < pageHeight - margin) {
                canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
                y += 12f
            }
        }

        pdf.finishPage(page)
        pdf.writeTo(output)
        pdf.close()
        output.flush()
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    private fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun buildContentTypesXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""</Types>""")
    }

    private const val ROOT_RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun buildWorkbookXml(sheetNames: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheetNames.forEachIndexed { idx, name ->
            append("""<sheet name="${xmlEscape(name)}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
        }
        append("""</sheets></workbook>""")
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""</Relationships>""")
    }

    private fun buildWorksheetXml(rows: List<List<String>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rIdx, cells ->
            append("""<row r="${rIdx + 1}">""")
            cells.forEachIndexed { cIdx, cellVal ->
                val ref = "${colIndexToLetters(cIdx)}${rIdx + 1}"
                append("""<c r="$ref" t="inlineStr"><is><t>${xmlEscape(cellVal)}</t></is></c>""")
            }
            append("""</row>""")
        }
        append("""</sheetData></worksheet>""")
    }

    private fun colIndexToLetters(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }
}
