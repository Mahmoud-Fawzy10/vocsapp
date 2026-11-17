package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val ownerId: String,
    val memberUidsCsv: String,
    val allowViewerExport: Boolean = false,
    val allowEditorImport: Boolean = true,
    val nextVocabSequence: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "workspace_members",
    indices = [Index("workspaceId"), Index("userId")]
)
data class WorkspaceMemberEntity(
    @PrimaryKey val id: String, // "${workspaceId}_${userId}"
    val workspaceId: String,
    val ownerId: String,
    val userId: String,
    val displayName: String,
    val email: String = "",
    val role: String, // WorkspaceRole name
    val status: String, // MemberStatus name
    val invitedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "words",
    indices = [
        Index("workspaceId"),
        Index("normalizedWord"),
        Index("humanId"),
        Index("partOfSpeech")
    ]
)
data class WordEntity(
    @PrimaryKey val id: String,
    val humanId: String,
    val workspaceId: String,
    val word: String,
    val normalizedWord: String,
    val partOfSpeech: String,
    val primaryMeaning: String,
    val englishDefinition: String = "",
    val arabicExplanation: String = "",
    val phoneticText: String = "",
    val exampleSentencesJson: String = "[]",
    val meaningsJson: String = "[]",
    val synonymsJson: String = "[]",
    val antonymsJson: String = "[]",
    val derivativesJson: String = "[]",
    val expressionsJson: String = "[]",
    val notesJson: String = "[]",
    val version: Long = 1L,
    val lastSyncedSnapshotJson: String = "",
    val createdBy: String,
    val updatedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "PENDING"
)

@Entity(
    tableName = "books",
    indices = [Index("workspaceId")]
)
data class BookEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String,
    val publisher: String = "",
    val editionOrYear: String = "",
    val grade: String = "",
    val notes: String = "",
    val version: Long = 1L,
    val createdBy: String,
    val updatedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "PENDING"
)

@Entity(
    tableName = "word_locations",
    indices = [Index("workspaceId"), Index("wordId"), Index("bookId")]
)
data class WordLocationEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val wordId: String,
    val bookId: String,
    val bookName: String,
    val publisher: String = "",
    val editionOrYear: String = "",
    val unit: String = "",
    val lesson: String = "",
    val pageNumber: String = "",
    val exerciseNumber: String = "",
    val section: String = "",
    val quotation: String = "",
    val sourceType: String = "كتاب مدرسي",
    val notes: String = "",
    val version: Long = 1L,
    val createdBy: String,
    val updatedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "PENDING"
)

@Entity(
    tableName = "curriculum_entries",
    indices = [Index("workspaceId"), Index("wordId"), Index("entryType")]
)
data class CurriculumEntryEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val wordId: String,
    val entryType: String, // OFFICIAL_CURRICULUM | USER_OBSERVATION
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
    val verificationStatus: String = "PERSONAL_NOTE",
    val version: Long = 1L,
    val createdBy: String,
    val updatedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = "PENDING"
)

@Entity(
    tableName = "sync_operations",
    indices = [Index("workspaceId"), Index("status")]
)
data class SyncOperationEntity(
    @PrimaryKey val operationId: String,
    val workspaceId: String,
    val entityType: String, // WORD, BOOK, LOCATION, CURRICULUM, REVIEW, WORKSPACE, MEMBER
    val entityId: String,
    val opType: String, // CREATE, UPDATE, DELETE
    val payloadJson: String,
    val baseVersion: Long = 1L,
    val status: String = "PENDING", // PENDING, ERROR, SYNCED
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conflict_records",
    indices = [Index("workspaceId"), Index("status")]
)
data class ConflictRecordEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val wordId: String,
    val humanId: String,
    val wordText: String,
    val localVersion: Long,
    val remoteVersion: Long,
    val baseSnapshotJson: String,
    val localSnapshotJson: String,
    val remoteSnapshotJson: String,
    val conflictingFieldsCsv: String,
    val status: String = "OPEN", // OPEN | RESOLVED
    val createdBy: String,
    val resolvedBy: String? = null,
    val resolvedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "import_jobs",
    indices = [Index("workspaceId")]
)
data class ImportJobEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val fileName: String,
    val sourceFormat: String,
    val userId: String,
    val userName: String,
    val totalRows: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val mergedCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val status: String,
    val errorsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "review_queue",
    indices = [Index("workspaceId"), Index("status")]
)
data class ReviewQueueItemEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val targetWordId: String,
    val targetWordText: String,
    val sourceType: String, // AI_SUGGESTION | IMPORT_PROPOSAL
    val suggestionType: String,
    val proposedPayload: String,
    val summaryArabic: String,
    val aiGenerated: Boolean,
    val verified: Boolean = false,
    val status: String = "PENDING", // PENDING | APPROVED | REJECTED
    val createdBy: String,
    val reviewedBy: String? = null,
    val reviewNotes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

@Entity(
    tableName = "audit_logs",
    indices = [Index("workspaceId"), Index("createdAt")]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val actorUserId: String,
    val actorName: String,
    val actorRole: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val summary: String,
    val detailsJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)
