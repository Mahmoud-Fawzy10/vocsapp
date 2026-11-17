package com.example.domain

import com.example.data.local.WordEntity
import com.example.model.PartOfSpeech

data class DuplicateMatch(
    val existingWord: WordEntity,
    val matchType: MatchType,
    val reasonArabic: String
) {
    enum class MatchType {
        EXACT_WORD_AND_POS,
        EXACT_NORMALIZED_DIFFERENT_POS
    }
}

object DuplicateDetector {
    /**
     * Detects potential duplicates across existing vocabulary words without wrongly
     * treating different parts of speech or distinct meanings as identical.
     * Example: "Abandon", "abandon", and " ABANDON " all normalize to "abandon".
     */
    fun findMatches(
        candidateWord: String,
        candidatePos: PartOfSpeech,
        existingWords: List<WordEntity>,
        ignoreWordId: String? = null
    ): List<DuplicateMatch> {
        val normalized = WordNormalizer.normalizeWord(candidateWord)
        if (normalized.isBlank()) return emptyList()

        return existingWords
            .filter { it.id != ignoreWordId && it.deletedAt == null }
            .filter { it.normalizedWord == normalized }
            .map { existing ->
                val existingPos = PartOfSpeech.safeValueOf(existing.partOfSpeech)
                if (existingPos == candidatePos) {
                    DuplicateMatch(
                        existingWord = existing,
                        matchType = DuplicateMatch.MatchType.EXACT_WORD_AND_POS,
                        reasonArabic = "تطابق تام للكلمة (${existing.humanId}) بنفس قسم الكلام (${existingPos.arabicLabel})"
                    )
                } else {
                    DuplicateMatch(
                        existingWord = existing,
                        matchType = DuplicateMatch.MatchType.EXACT_NORMALIZED_DIFFERENT_POS,
                        reasonArabic = "الكلمة موجودة (${existing.humanId}) بقسم كلام مختلف (${existingPos.arabicLabel})"
                    )
                }
            }
    }
}
