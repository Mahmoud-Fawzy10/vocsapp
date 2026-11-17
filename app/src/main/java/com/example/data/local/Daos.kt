package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY updatedAt DESC")
    fun observeWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    fun observeWorkspace(id: String): Flow<WorkspaceEntity?>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceById(id: String): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspace(workspace: WorkspaceEntity)

    @Query("SELECT * FROM workspace_members WHERE workspaceId = :workspaceId ORDER BY createdAt ASC")
    fun observeMembers(workspaceId: String): Flow<List<WorkspaceMemberEntity>>

    @Query("SELECT * FROM workspace_members WHERE workspaceId = :workspaceId AND userId = :userId LIMIT 1")
    suspend fun getMember(workspaceId: String, userId: String): WorkspaceMemberEntity?

    @Query("SELECT * FROM workspace_members WHERE workspaceId = :workspaceId")
    suspend fun getMembersSync(workspaceId: String): List<WorkspaceMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: WorkspaceMemberEntity)

    @Query("DELETE FROM workspace_members WHERE workspaceId = :workspaceId AND userId = :userId")
    suspend fun deleteMember(workspaceId: String, userId: String)
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE workspaceId = :workspaceId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActiveWords(workspaceId: String): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE workspaceId = :workspaceId AND deletedAt IS NULL")
    suspend fun getActiveWordsSync(workspaceId: String): List<WordEntity>

    @Query("SELECT * FROM words WHERE id = :wordId LIMIT 1")
    fun observeWord(wordId: String): Flow<WordEntity?>

    @Query("SELECT * FROM words WHERE id = :wordId LIMIT 1")
    suspend fun getWordById(wordId: String): WordEntity?

    @Query("SELECT * FROM words WHERE workspaceId = :workspaceId AND normalizedWord = :normalized AND deletedAt IS NULL")
    suspend fun findByNormalizedWord(workspaceId: String, normalized: String): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWord(word: WordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWords(words: List<WordEntity>)

    @Query("UPDATE words SET deletedAt = :deletedAt, updatedAt = :updatedAt, updatedBy = :updatedBy, version = version + 1, syncStatus = 'PENDING' WHERE id = :wordId")
    suspend fun softDeleteWord(wordId: String, deletedAt: Long, updatedAt: Long, updatedBy: String)

    @Query("DELETE FROM words WHERE id = :wordId")
    suspend fun hardDeleteWord(wordId: String)
}

@Dao
interface BookAndCurriculumDao {
    @Query("SELECT * FROM books WHERE workspaceId = :workspaceId AND deletedAt IS NULL ORDER BY title ASC")
    fun observeBooks(workspaceId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE workspaceId = :workspaceId AND deletedAt IS NULL")
    suspend fun getBooksSync(workspaceId: String): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun hardDeleteBook(bookId: String)

    @Query("SELECT * FROM word_locations WHERE workspaceId = :workspaceId AND deletedAt IS NULL")
    fun observeAllLocations(workspaceId: String): Flow<List<WordLocationEntity>>

    @Query("SELECT * FROM word_locations WHERE wordId = :wordId AND deletedAt IS NULL ORDER BY bookName ASC")
    fun observeLocationsForWord(wordId: String): Flow<List<WordLocationEntity>>

    @Query("SELECT * FROM word_locations WHERE workspaceId = :workspaceId AND deletedAt IS NULL")
    suspend fun getLocationsSync(workspaceId: String): List<WordLocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocation(location: WordLocationEntity)

    @Query("DELETE FROM word_locations WHERE id = :locationId")
    suspend fun deleteLocation(locationId: String)

    @Query("SELECT * FROM curriculum_entries WHERE workspaceId = :workspaceId AND deletedAt IS NULL")
    fun observeAllCurriculum(workspaceId: String): Flow<List<CurriculumEntryEntity>>

    @Query("SELECT * FROM curriculum_entries WHERE wordId = :wordId AND deletedAt IS NULL ORDER BY entryType ASC")
    fun observeCurriculumForWord(wordId: String): Flow<List<CurriculumEntryEntity>>

    @Query("SELECT * FROM curriculum_entries WHERE workspaceId = :workspaceId AND deletedAt IS NULL")
    suspend fun getCurriculumSync(workspaceId: String): List<CurriculumEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCurriculum(entry: CurriculumEntryEntity)

    @Query("DELETE FROM curriculum_entries WHERE id = :entryId")
    suspend fun deleteCurriculum(entryId: String)
}

@Dao
interface SyncAndGovernanceDao {
    @Query("SELECT * FROM sync_operations WHERE workspaceId = :workspaceId ORDER BY createdAt ASC")
    fun observeSyncOperations(workspaceId: String): Flow<List<SyncOperationEntity>>

    @Query("SELECT * FROM sync_operations WHERE workspaceId = :workspaceId AND status != 'SYNCED' ORDER BY createdAt ASC")
    suspend fun getPendingSyncOperations(workspaceId: String): List<SyncOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncOperation(op: SyncOperationEntity)

    @Query("DELETE FROM sync_operations WHERE operationId = :operationId")
    suspend fun deleteSyncOperation(operationId: String)

    @Query("DELETE FROM sync_operations WHERE workspaceId = :workspaceId AND status = 'SYNCED'")
    suspend fun clearSyncedOperations(workspaceId: String)

    @Query("SELECT * FROM conflict_records WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun observeConflicts(workspaceId: String): Flow<List<ConflictRecordEntity>>

    @Query("SELECT * FROM conflict_records WHERE id = :id LIMIT 1")
    suspend fun getConflictById(id: String): ConflictRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflict(conflict: ConflictRecordEntity)

    @Query("SELECT * FROM import_jobs WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun observeImportJobs(workspaceId: String): Flow<List<ImportJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportJob(job: ImportJobEntity)

    @Query("SELECT * FROM review_queue WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun observeReviewQueue(workspaceId: String): Flow<List<ReviewQueueItemEntity>>

    @Query("SELECT * FROM review_queue WHERE id = :id LIMIT 1")
    suspend fun getReviewItemById(id: String): ReviewQueueItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviewItem(item: ReviewQueueItemEntity)

    @Query("SELECT * FROM audit_logs WHERE workspaceId = :workspaceId ORDER BY createdAt DESC LIMIT 300")
    fun observeAuditLogs(workspaceId: String): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)
}
