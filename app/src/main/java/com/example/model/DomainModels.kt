package com.example.model

data class MeaningItem(
    val id: String = "",
    val arabicMeaning: String = "",
    val englishNuance: String = "",
    val contextExample: String = "",
    val isPrimary: Boolean = false
)

data class SynonymItem(
    val id: String = "",
    val word: String = "",
    val meaning: String = "",
    val notes: String = ""
)

data class AntonymItem(
    val id: String = "",
    val word: String = "",
    val meaning: String = "",
    val notes: String = ""
)

data class DerivativeItem(
    val id: String = "",
    val word: String = "",
    val formType: DerivativeForm = DerivativeForm.OTHER,
    val relationshipNote: String = "",
    val arabicMeaning: String = ""
)

data class ExpressionItem(
    val id: String = "",
    val type: ExpressionType = ExpressionType.COMMON_EXPRESSION,
    val expression: String = "",
    val exampleUsage: String = "",
    val arabicExplanation: String = ""
)

data class WordNoteItem(
    val id: String = "",
    val category: NoteCategory = NoteCategory.PERSONAL,
    val content: String = ""
)

data class WordDraft(
    val existingId: String? = null,
    val humanId: String = "",
    val word: String = "",
    val partOfSpeech: PartOfSpeech = PartOfSpeech.NOUN,
    val primaryMeaning: String = "",
    val englishDefinition: String = "",
    val arabicExplanation: String = "",
    val phoneticText: String = "",
    val exampleSentences: List<String> = emptyList(),
    val meanings: List<MeaningItem> = emptyList(),
    val synonyms: List<SynonymItem> = emptyList(),
    val antonyms: List<AntonymItem> = emptyList(),
    val derivatives: List<DerivativeItem> = emptyList(),
    val expressions: List<ExpressionItem> = emptyList(),
    val notes: List<WordNoteItem> = emptyList(),
    val version: Long = 1L
)

data class CurriculumEntryDraft(
    val existingId: String? = null,
    val wordId: String = "",
    val entryType: CurriculumEntryType = CurriculumEntryType.USER_OBSERVATION,
    val curriculumDistinction: String = "",
    val importantExamNotes: String = "",
    val commonExamPatterns: String = "",
    val teacherNotes: String = "",
    val relatedGrammarRules: String = "",
    val grade: String = "",
    val academicYear: String = "",
    val unit: String = "",
    val lesson: String = "",
    val source: String = "",
    val verificationStatus: VerificationStatus = VerificationStatus.PERSONAL_NOTE
)

data class WordLocationDraft(
    val existingId: String? = null,
    val wordId: String = "",
    val bookId: String = "",
    val bookName: String = "",
    val publisher: String = "",
    val editionOrYear: String = "",
    val unit: String = "",
    val lesson: String = "",
    val pageNumber: String = "",
    val exerciseNumber: String = "",
    val section: String = "",
    val quotation: String = "",
    val sourceType: String = "كتاب مدرسي",
    val notes: String = ""
)

data class VocabularyFilter(
    val query: String = "",
    val partOfSpeech: PartOfSpeech? = null,
    val bookId: String? = null,
    val unit: String? = null,
    val lesson: String? = null,
    val page: String? = null,
    val curriculumDistinctionOnly: Boolean = false,
    val sortOrder: WordSortOrder = WordSortOrder.UPDATED_DESC
)

data class DashboardStats(
    val totalWords: Int = 0,
    val totalMeanings: Int = 0,
    val totalBooks: Int = 0,
    val pendingSyncCount: Int = 0,
    val openConflictsCount: Int = 0,
    val pendingReviewCount: Int = 0,
    val recentImportCount: Int = 0
)

data class ImportColumnMapping(
    val wordColumn: String? = null,
    val partOfSpeechColumn: String? = null,
    val primaryMeaningColumn: String? = null,
    val additionalMeaningsColumn: String? = null,
    val englishDefinitionColumn: String? = null,
    val arabicExplanationColumn: String? = null,
    val phoneticColumn: String? = null,
    val examplesColumn: String? = null,
    val synonymsColumn: String? = null,
    val antonymsColumn: String? = null,
    val derivativesColumn: String? = null,
    val expressionsColumn: String? = null,
    val bookNameColumn: String? = null,
    val unitColumn: String? = null,
    val lessonColumn: String? = null,
    val pageColumn: String? = null
)

data class ImportPreviewRow(
    val rowIndex: Int,
    val rawWord: String,
    val normalizedWord: String,
    val partOfSpeech: PartOfSpeech,
    val primaryMeaning: String,
    val additionalMeanings: List<String> = emptyList(),
    val englishDefinition: String = "",
    val arabicExplanation: String = "",
    val phoneticText: String = "",
    val exampleSentences: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val derivatives: List<String> = emptyList(),
    val expressions: List<String> = emptyList(),
    val bookName: String = "",
    val unit: String = "",
    val lesson: String = "",
    val pageNumber: String = "",
    val matchedWordId: String? = null,
    val matchedHumanId: String? = null,
    val matchedReason: String? = null,
    val validationError: String? = null,
    val strategy: DuplicateStrategy = DuplicateStrategy.SKIP
)

data class ImportReport(
    val jobId: String,
    val fileName: String,
    val totalRows: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val mergedCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val errors: List<String> = emptyList()
)

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val scope: ExportScope = ExportScope.ALL,
    val selectedWordIds: Set<String> = emptySet(),
    val filterBookId: String? = null,
    val filterUnit: String? = null,
    val filterLesson: String? = null,
    val filterPartOfSpeech: PartOfSpeech? = null,
    val includeMeanings: Boolean = true,
    val includeSynonymsAntonyms: Boolean = true,
    val includeDerivatives: Boolean = true,
    val includeExpressions: Boolean = true,
    val includeCurriculum: Boolean = true,
    val includeBookLocations: Boolean = true,
    val includeNotes: Boolean = false
)

data class ConflictFieldDiff(
    val fieldKey: String,
    val fieldArabicLabel: String,
    val baseValue: String,
    val localValue: String,
    val remoteValue: String,
    val isConflicting: Boolean
)

enum class ConflictChoice {
    KEEP_LOCAL,
    USE_REMOTE
}

data class UserPreferences(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val aiEnabled: Boolean = false,
    val ttsSpeed: Float = 1.0f,
    val preferredAccent: String = "US",
    val preferredVoiceName: String = "",
    val activeWorkspaceId: String = "",
    val lastSyncTimestamp: Long = 0L
)
