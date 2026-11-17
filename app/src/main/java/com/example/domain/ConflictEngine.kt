package com.example.domain

import com.example.data.local.ConflictRecordEntity
import com.example.data.local.WordEntity
import com.example.model.ConflictChoice
import com.example.model.ConflictFieldDiff
import org.json.JSONObject
import java.util.UUID

sealed class MergeOutcome {
    data class AutoMerged(val mergedWord: WordEntity, val mergedFields: List<String>) : MergeOutcome()
    data class ConflictDetected(
        val conflictRecord: ConflictRecordEntity,
        val diffs: List<ConflictFieldDiff>
    ) : MergeOutcome()
    data class NoChangesNeeded(val currentWord: WordEntity) : MergeOutcome()
}

object ConflictEngine {
    private val trackedFields = listOf(
        "word" to "الكلمة الإنجليزية",
        "partOfSpeech" to "قسم الكلام",
        "primaryMeaning" to "المعنى الأساسي",
        "englishDefinition" to "التعريف الإنجليزي",
        "arabicExplanation" to "الشرح العربي",
        "phoneticText" to "النطق الصوتي",
        "exampleSentencesJson" to "جمل الأمثلة",
        "meaningsJson" to "المعاني الإضافية",
        "synonymsJson" to "المرادفات",
        "antonymsJson" to "المتضادات",
        "derivativesJson" to "المشتقات",
        "expressionsJson" to "التعبيرات والمتلازمات",
        "notesJson" to "الملاحظات"
    )

    fun snapshotToJson(word: WordEntity): String {
        return JSONObject().apply {
            put("word", word.word)
            put("partOfSpeech", word.partOfSpeech)
            put("primaryMeaning", word.primaryMeaning)
            put("englishDefinition", word.englishDefinition)
            put("arabicExplanation", word.arabicExplanation)
            put("phoneticText", word.phoneticText)
            put("exampleSentencesJson", word.exampleSentencesJson)
            put("meaningsJson", word.meaningsJson)
            put("synonymsJson", word.synonymsJson)
            put("antonymsJson", word.antonymsJson)
            put("derivativesJson", word.derivativesJson)
            put("expressionsJson", word.expressionsJson)
            put("notesJson", word.notesJson)
        }.toString()
    }

    private fun fieldMapFromWord(word: WordEntity): Map<String, String> = mapOf(
        "word" to word.word,
        "partOfSpeech" to word.partOfSpeech,
        "primaryMeaning" to word.primaryMeaning,
        "englishDefinition" to word.englishDefinition,
        "arabicExplanation" to word.arabicExplanation,
        "phoneticText" to word.phoneticText,
        "exampleSentencesJson" to word.exampleSentencesJson,
        "meaningsJson" to word.meaningsJson,
        "synonymsJson" to word.synonymsJson,
        "antonymsJson" to word.antonymsJson,
        "derivativesJson" to word.derivativesJson,
        "expressionsJson" to word.expressionsJson,
        "notesJson" to word.notesJson
    )

    private fun fieldMapFromJson(jsonStr: String, fallback: WordEntity): Map<String, String> {
        if (jsonStr.isBlank()) return fieldMapFromWord(fallback)
        return runCatching {
            val obj = JSONObject(jsonStr)
            trackedFields.associate { (key, _) -> key to obj.optString(key, "") }
        }.getOrDefault(fieldMapFromWord(fallback))
    }

    /**
     * Performs a 3-way merge between [baseWordSnapshotJson], [localWord], and [remoteWord].
     * Non-conflicting edits on distinct fields are merged automatically and safely.
     * Conflicting edits on the same field preserve both versions and produce a [ConflictRecordEntity].
     */
    fun evaluateThreeWayMerge(
        localWord: WordEntity,
        remoteWord: WordEntity,
        actorUserId: String
    ): MergeOutcome {
        val baseMap = fieldMapFromJson(localWord.lastSyncedSnapshotJson, localWord)
        val localMap = fieldMapFromWord(localWord)
        val remoteMap = fieldMapFromWord(remoteWord)

        val diffs = mutableListOf<ConflictFieldDiff>()
        val mergedValues = mutableMapOf<String, String>()
        val autoMergedFieldNames = mutableListOf<String>()

        for ((key, label) in trackedFields) {
            val baseVal = baseMap[key].orEmpty()
            val localVal = localMap[key].orEmpty()
            val remoteVal = remoteMap[key].orEmpty()

            val localChanged = localVal != baseVal
            val remoteChanged = remoteVal != baseVal

            when {
                !localChanged && !remoteChanged -> mergedValues[key] = localVal
                localChanged && !remoteChanged -> {
                    mergedValues[key] = localVal
                    autoMergedFieldNames.add(label)
                }
                !localChanged && remoteChanged -> {
                    mergedValues[key] = remoteVal
                    autoMergedFieldNames.add(label)
                }
                localVal == remoteVal -> {
                    mergedValues[key] = localVal
                }
                else -> {
                    // True conflict: both edited same field differently
                    mergedValues[key] = localVal
                    diffs.add(
                        ConflictFieldDiff(
                            fieldKey = key,
                            fieldArabicLabel = label,
                            baseValue = baseVal,
                            localValue = localVal,
                            remoteValue = remoteVal,
                            isConflicting = true
                        )
                    )
                }
            }
        }

        if (diffs.isEmpty()) {
            val merged = applyFieldMap(
                base = remoteWord,
                fields = mergedValues,
                newVersion = maxOf(localWord.version, remoteWord.version) + 1,
                updatedBy = actorUserId
            )
            return if (autoMergedFieldNames.isEmpty() && localWord.version == remoteWord.version) {
                MergeOutcome.NoChangesNeeded(remoteWord)
            } else {
                MergeOutcome.AutoMerged(merged, autoMergedFieldNames)
            }
        }

        val conflictRecord = ConflictRecordEntity(
            id = "cnf_${UUID.randomUUID().toString().take(12)}",
            workspaceId = localWord.workspaceId,
            wordId = localWord.id,
            humanId = localWord.humanId,
            wordText = localWord.word,
            localVersion = localWord.version,
            remoteVersion = remoteWord.version,
            baseSnapshotJson = snapshotToJson(localWord).takeIf { localWord.lastSyncedSnapshotJson.isBlank() }
                ?: localWord.lastSyncedSnapshotJson,
            localSnapshotJson = snapshotToJson(localWord),
            remoteSnapshotJson = snapshotToJson(remoteWord),
            conflictingFieldsCsv = diffs.joinToString(",") { it.fieldKey },
            status = "OPEN",
            createdBy = actorUserId
        )

        return MergeOutcome.ConflictDetected(conflictRecord, diffs)
    }

    fun buildDiffsForRecord(record: ConflictRecordEntity, fallbackWord: WordEntity? = null): List<ConflictFieldDiff> {
        val baseObj = runCatching { JSONObject(record.baseSnapshotJson) }.getOrNull()
        val localObj = runCatching { JSONObject(record.localSnapshotJson) }.getOrNull()
        val remoteObj = runCatching { JSONObject(record.remoteSnapshotJson) }.getOrNull()
        val conflictingKeys = record.conflictingFieldsCsv.split(",").filter { it.isNotBlank() }.toSet()

        return trackedFields.mapNotNull { (key, label) ->
            val baseVal = baseObj?.optString(key, "") ?: ""
            val localVal = localObj?.optString(key, "") ?: fallbackWord?.let { fieldMapFromWord(it)[key] }.orEmpty()
            val remoteVal = remoteObj?.optString(key, "") ?: ""
            if (key in conflictingKeys || localVal != remoteVal) {
                ConflictFieldDiff(
                    fieldKey = key,
                    fieldArabicLabel = label,
                    baseValue = baseVal,
                    localValue = localVal,
                    remoteValue = remoteVal,
                    isConflicting = key in conflictingKeys || localVal != remoteVal
                )
            } else null
        }
    }

    fun resolveConflict(
        currentWord: WordEntity,
        record: ConflictRecordEntity,
        choices: Map<String, ConflictChoice>,
        resolvedByUid: String
    ): WordEntity {
        val localObj = runCatching { JSONObject(record.localSnapshotJson) }.getOrNull()
        val remoteObj = runCatching { JSONObject(record.remoteSnapshotJson) }.getOrNull()
        val baseFields = fieldMapFromWord(currentWord)

        val resolvedMap = trackedFields.associate { (key, _) ->
            val choice = choices[key] ?: ConflictChoice.KEEP_LOCAL
            val value = when (choice) {
                ConflictChoice.KEEP_LOCAL -> localObj?.optString(key) ?: baseFields[key].orEmpty()
                ConflictChoice.USE_REMOTE -> remoteObj?.optString(key) ?: baseFields[key].orEmpty()
            }
            key to value
        }

        val nextVer = maxOf(record.localVersion, record.remoteVersion, currentWord.version) + 1
        return applyFieldMap(
            base = currentWord,
            fields = resolvedMap,
            newVersion = nextVer,
            updatedBy = resolvedByUid
        )
    }

    private fun applyFieldMap(
        base: WordEntity,
        fields: Map<String, String>,
        newVersion: Long,
        updatedBy: String
    ): WordEntity {
        val newWordText = WordNormalizer.cleanDisplayWord(fields["word"] ?: base.word)
        return base.copy(
            word = newWordText,
            normalizedWord = WordNormalizer.normalizeWord(newWordText),
            partOfSpeech = fields["partOfSpeech"] ?: base.partOfSpeech,
            primaryMeaning = fields["primaryMeaning"] ?: base.primaryMeaning,
            englishDefinition = fields["englishDefinition"] ?: base.englishDefinition,
            arabicExplanation = fields["arabicExplanation"] ?: base.arabicExplanation,
            phoneticText = fields["phoneticText"] ?: base.phoneticText,
            exampleSentencesJson = fields["exampleSentencesJson"] ?: base.exampleSentencesJson,
            meaningsJson = fields["meaningsJson"] ?: base.meaningsJson,
            synonymsJson = fields["synonymsJson"] ?: base.synonymsJson,
            antonymsJson = fields["antonymsJson"] ?: base.antonymsJson,
            derivativesJson = fields["derivativesJson"] ?: base.derivativesJson,
            expressionsJson = fields["expressionsJson"] ?: base.expressionsJson,
            notesJson = fields["notesJson"] ?: base.notesJson,
            version = newVersion,
            updatedBy = updatedBy,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING"
        )
    }
}
