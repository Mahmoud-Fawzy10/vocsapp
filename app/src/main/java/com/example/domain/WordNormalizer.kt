package com.example.domain

import com.example.model.AntonymItem
import com.example.model.DerivativeForm
import com.example.model.DerivativeItem
import com.example.model.ExpressionItem
import com.example.model.ExpressionType
import com.example.model.MeaningItem
import com.example.model.NoteCategory
import com.example.model.SynonymItem
import com.example.model.WordNoteItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object WordNormalizer {
    private val multiSpaceRegex = Regex("\\s+")

    fun cleanDisplayWord(raw: String): String {
        return raw.trim().replace(multiSpaceRegex, " ")
    }

    fun normalizeWord(raw: String): String {
        return cleanDisplayWord(raw).lowercase(Locale.ROOT)
    }

    fun formatHumanId(sequenceNumber: Int): String {
        return "VOC-${sequenceNumber.toString().padStart(6, '0')}"
    }
}

object WordJsonCodec {
    fun encodeStringList(list: List<String>): String {
        val arr = JSONArray()
        list.filter { it.isNotBlank() }.forEach { arr.put(it.trim()) }
        return arr.toString()
    }

    fun decodeStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx).takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeMeanings(items: List<MeaningItem>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("arabicMeaning", it.arabicMeaning)
                put("englishNuance", it.englishNuance)
                put("contextExample", it.contextExample)
                put("isPrimary", it.isPrimary)
            })
        }
        return arr.toString()
    }

    fun decodeMeanings(json: String?): List<MeaningItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                MeaningItem(
                    id = obj.optString("id"),
                    arabicMeaning = obj.optString("arabicMeaning"),
                    englishNuance = obj.optString("englishNuance"),
                    contextExample = obj.optString("contextExample"),
                    isPrimary = obj.optBoolean("isPrimary", false)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeSynonyms(items: List<SynonymItem>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("word", it.word)
                put("meaning", it.meaning)
                put("notes", it.notes)
            })
        }
        return arr.toString()
    }

    fun decodeSynonyms(json: String?): List<SynonymItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                SynonymItem(
                    id = obj.optString("id"),
                    word = obj.optString("word"),
                    meaning = obj.optString("meaning"),
                    notes = obj.optString("notes")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeAntonyms(items: List<AntonymItem>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("word", it.word)
                put("meaning", it.meaning)
                put("notes", it.notes)
            })
        }
        return arr.toString()
    }

    fun decodeAntonyms(json: String?): List<AntonymItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                AntonymItem(
                    id = obj.optString("id"),
                    word = obj.optString("word"),
                    meaning = obj.optString("meaning"),
                    notes = obj.optString("notes")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeDerivatives(items: List<DerivativeItem>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("word", it.word)
                put("formType", it.formType.name)
                put("relationshipNote", it.relationshipNote)
                put("arabicMeaning", it.arabicMeaning)
            })
        }
        return arr.toString()
    }

    fun decodeDerivatives(json: String?): List<DerivativeItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                DerivativeItem(
                    id = obj.optString("id"),
                    word = obj.optString("word"),
                    formType = DerivativeForm.safeValueOf(obj.optString("formType")),
                    relationshipNote = obj.optString("relationshipNote"),
                    arabicMeaning = obj.optString("arabicMeaning")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeExpressions(items: List<ExpressionItem>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("type", it.type.name)
                put("expression", it.expression)
                put("exampleUsage", it.exampleUsage)
                put("arabicExplanation", it.arabicExplanation)
            })
        }
        return arr.toString()
    }

    fun decodeExpressions(json: String?): List<ExpressionItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                ExpressionItem(
                    id = obj.optString("id"),
                    type = ExpressionType.safeValueOf(obj.optString("type")),
                    expression = obj.optString("expression"),
                    exampleUsage = obj.optString("exampleUsage"),
                    arabicExplanation = obj.optString("arabicExplanation")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeNotes(items: List<WordNoteItem>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("category", it.category.name)
                put("content", it.content)
            })
        }
        return arr.toString()
    }

    fun decodeNotes(json: String?): List<WordNoteItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                WordNoteItem(
                    id = obj.optString("id"),
                    category = NoteCategory.safeValueOf(obj.optString("category")),
                    content = obj.optString("content")
                )
            }
        }.getOrDefault(emptyList())
    }
}
