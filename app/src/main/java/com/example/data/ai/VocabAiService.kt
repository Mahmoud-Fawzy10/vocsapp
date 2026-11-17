package com.example.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiVocabularySuggestion(
    val suggestionType: String,
    val summaryArabic: String,
    val payloadJson: String
)

class VocabAiService {
    private val suggestionSchema = Schema.obj(
        properties = mapOf(
            "summaryArabic" to Schema.string(description = "Brief Arabic summary of the linguistic suggestion"),
            "suggestedPartOfSpeech" to Schema.string(description = "Suggested English part of speech enum name: NOUN, VERB, ADJECTIVE, ADVERB, PHRASAL_VERB, IDIOM, OTHER"),
            "meanings" to Schema.array(
                items = Schema.string(),
                description = "Suggested Arabic meanings"
            ),
            "synonyms" to Schema.array(
                items = Schema.string(),
                description = "Suggested English synonyms with brief Arabic gloss"
            ),
            "antonyms" to Schema.array(
                items = Schema.string(),
                description = "Suggested English antonyms with brief Arabic gloss"
            ),
            "derivatives" to Schema.array(
                items = Schema.string(),
                description = "Suggested word family derivatives (e.g. abandonment (noun))"
            ),
            "collocations" to Schema.array(
                items = Schema.string(),
                description = "Suggested collocations, phrasal verbs, or idioms"
            ),
            "exampleSentences" to Schema.array(
                items = Schema.string(),
                description = "Natural educational English example sentences"
            ),
            "comparisonNote" to Schema.string(description = "Arabic explanation of differences between similar words if applicable")
        ),
        optionalProperties = listOf("comparisonNote", "suggestedPartOfSpeech")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = suggestionSchema
            }
        )
    }

    suspend fun generateVocabularySuggestion(
        word: String,
        currentMeaning: String,
        focusArea: String,
        compareWithWord: String = ""
    ): Result<AiVocabularySuggestion> {
        return try {
            val prompt = buildString {
                appendLine("You are an educational English-Arabic linguistics assistant.")
                appendLine("Analyze the English vocabulary word: \"$word\".")
                if (currentMeaning.isNotBlank()) {
                    appendLine("Current primary Arabic meaning: \"$currentMeaning\".")
                }
                appendLine("Requested focus: $focusArea.")
                if (compareWithWord.isNotBlank()) {
                    appendLine("Explain the semantic and usage differences between \"$word\" and \"$compareWithWord\" in clear Arabic inside comparisonNote.")
                }
                appendLine("CRITICAL RULES:")
                appendLine("1. Do NOT invent official school curriculum facts, exam questions, page numbers, or textbook citations.")
                appendLine("2. Provide accurate, educational English vocabulary suggestions and clear Arabic explanations.")
                appendLine("3. Return clean JSON strictly matching the schema.")
            }

            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("لم يُرجع النموذج استجابة نصية.")
            val json = JSONObject(rawJson)
            val summary = json.optString("summaryArabic").ifBlank {
                "اقتراحات لغوية بالذكاء الاصطناعي لكلمة $word ($focusArea)"
            }
            Result.success(
                AiVocabularySuggestion(
                    suggestionType = focusArea,
                    summaryArabic = summary,
                    payloadJson = rawJson
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI suggestion timed out", e)
            Result.failure(Exception("انتهت مهلة الاتصال بالذكاء الاصطناعي (60 ثانية). يرجى المحاولة لاحقاً."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "تعذر إنشاء اقتراح الذكاء الاصطناعي."))
        }
    }

    private companion object {
        const val TAG = "VocabAiService"
    }
}
