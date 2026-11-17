package com.example.data.repo

import android.content.Context
import com.example.data.ai.VocabAiService
import com.example.data.importexport.ImportParser
import com.example.data.local.AppDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.BookEntity
import com.example.data.local.ConflictRecordEntity
import com.example.data.local.CurriculumEntryEntity
import com.example.data.local.ImportJobEntity
import com.example.data.local.ReviewQueueItemEntity
import com.example.data.local.SyncOperationEntity
import com.example.data.local.WordEntity
import com.example.data.local.WordLocationEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceMemberEntity
import com.example.data.sync.SyncEngine
import com.example.domain.ConflictEngine
import com.example.domain.RbacGuard
import com.example.domain.WordJsonCodec
import com.example.domain.WordNormalizer
import com.example.model.ConflictChoice
import com.example.model.CurriculumEntryDraft
import com.example.model.CurriculumEntryType
import com.example.model.DuplicateStrategy
import com.example.model.ImportPreviewRow
import com.example.model.ImportReport
import com.example.model.MeaningItem
import com.example.model.MemberStatus
import com.example.model.PartOfSpeech
import com.example.model.ReviewSourceType
import com.example.model.ReviewStatus
import com.example.model.SyncOpType
import com.example.model.SynonymItem
import com.example.model.AntonymItem
import com.example.model.DerivativeItem
import com.example.model.ExpressionItem
import com.example.model.VerificationStatus
import com.example.model.WordDraft
import com.example.model.WordLocationDraft
import com.example.model.WorkspaceRole
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class VocabularyRepository(
    context: Context,
    private val db: AppDatabase,
    val syncEngine: SyncEngine,
    private val aiService: VocabAiService,
    private val auth: FirebaseAuth
) {
    private val workspaceDao = db.workspaceDao()
    private val wordDao = db.wordDao()
    private val bookDao = db.bookAndCurriculumDao()
    private val govDao = db.syncAndGovernanceDao()

    fun observeWorkspaces(): Flow<List<WorkspaceEntity>> = workspaceDao.observeWorkspaces()
    fun observeWorkspace(id: String): Flow<WorkspaceEntity?> = workspaceDao.observeWorkspace(id)
    fun observeMembers(workspaceId: String): Flow<List<WorkspaceMemberEntity>> = workspaceDao.observeMembers(workspaceId)
    fun observeWords(workspaceId: String): Flow<List<WordEntity>> = wordDao.observeActiveWords(workspaceId)
    fun observeWord(wordId: String): Flow<WordEntity?> = wordDao.observeWord(wordId)
    fun observeBooks(workspaceId: String): Flow<List<BookEntity>> = bookDao.observeBooks(workspaceId)
    fun observeAllLocations(workspaceId: String): Flow<List<WordLocationEntity>> = bookDao.observeAllLocations(workspaceId)
    fun observeLocationsForWord(wordId: String): Flow<List<WordLocationEntity>> = bookDao.observeLocationsForWord(wordId)
    fun observeAllCurriculum(workspaceId: String): Flow<List<CurriculumEntryEntity>> = bookDao.observeAllCurriculum(workspaceId)
    fun observeCurriculumForWord(wordId: String): Flow<List<CurriculumEntryEntity>> = bookDao.observeCurriculumForWord(wordId)
    fun observeSyncOperations(workspaceId: String): Flow<List<SyncOperationEntity>> = govDao.observeSyncOperations(workspaceId)
    fun observeConflicts(workspaceId: String): Flow<List<ConflictRecordEntity>> = govDao.observeConflicts(workspaceId)
    fun observeImportJobs(workspaceId: String): Flow<List<ImportJobEntity>> = govDao.observeImportJobs(workspaceId)
    fun observeReviewQueue(workspaceId: String): Flow<List<ReviewQueueItemEntity>> = govDao.observeReviewQueue(workspaceId)
    fun observeAuditLogs(workspaceId: String): Flow<List<AuditLogEntity>> = govDao.observeAuditLogs(workspaceId)

    private fun requireUid(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً.")
    }

    suspend fun ensureWorkspaceForUser(
        uid: String,
        displayName: String,
        email: String,
        preferredWorkspaceId: String
    ): WorkspaceEntity {
        val existingPreferred = if (preferredWorkspaceId.isNotBlank()) {
            workspaceDao.getWorkspaceById(preferredWorkspaceId)
        } else null
        if (existingPreferred != null) {
            ensureMemberExists(existingPreferred, uid, displayName, email)
            return existingPreferred
        }

        val wsId = "ws_${uid.take(12)}"
        val existing = workspaceDao.getWorkspaceById(wsId)
        if (existing != null) {
            ensureMemberExists(existing, uid, displayName, email)
            return existing
        }

        val now = System.currentTimeMillis()
        val workspace = WorkspaceEntity(
            id = wsId,
            name = "مكتبة المفردات التعليمية",
            description = "مساحة عمل تعليمية لإدارة المفردات الإنجليزية والمناهج والكتب",
            ownerId = uid,
            memberUidsCsv = uid,
            allowViewerExport = false,
            allowEditorImport = true,
            nextVocabSequence = 1,
            createdAt = now,
            updatedAt = now
        )
        workspaceDao.upsertWorkspace(workspace)
        val ownerMember = WorkspaceMemberEntity(
            id = "${wsId}_$uid",
            workspaceId = wsId,
            ownerId = uid,
            userId = uid,
            displayName = displayName.ifBlank { "المالك" },
            email = email,
            role = WorkspaceRole.OWNER.name,
            status = MemberStatus.ACTIVE.name,
            invitedBy = uid,
            createdAt = now,
            updatedAt = now
        )
        workspaceDao.upsertMember(ownerMember)
        syncEngine.enqueueOperation(wsId, "WORKSPACE", wsId, SyncOpType.CREATE, "{}", 1L)
        return workspace
    }

    private suspend fun ensureMemberExists(
        ws: WorkspaceEntity,
        uid: String,
        displayName: String,
        email: String
    ) {
        if (workspaceDao.getMember(ws.id, uid) == null && ws.ownerId == uid) {
            val now = System.currentTimeMillis()
            workspaceDao.upsertMember(
                WorkspaceMemberEntity(
                    id = "${ws.id}_$uid",
                    workspaceId = ws.id,
                    ownerId = ws.ownerId,
                    userId = uid,
                    displayName = displayName.ifBlank { "المالك" },
                    email = email,
                    role = WorkspaceRole.OWNER.name,
                    status = MemberStatus.ACTIVE.name,
                    invitedBy = uid,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun saveWord(workspaceId: String, draft: WordDraft): Result<WordEntity> {
        return try {
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            RbacGuard.requireEditVocab(member)

            val cleanedWord = WordNormalizer.cleanDisplayWord(draft.word)
            val cleanedMeaning = WordNormalizer.cleanDisplayWord(draft.primaryMeaning)
            require(cleanedWord.isNotBlank()) { "الكلمة الإنجليزية مطلوبة." }
            require(cleanedMeaning.isNotBlank()) { "المعنى الأساسي مطلوب." }

            val now = System.currentTimeMillis()
            val ws = workspaceDao.getWorkspaceById(workspaceId)
                ?: throw IllegalStateException("مساحة العمل غير موجودة.")

            val isNew = draft.existingId.isNullOrBlank()
            val existing = if (!isNew) wordDao.getWordById(draft.existingId!!) else null

            val wordId = existing?.id ?: "w_${UUID.randomUUID().toString().take(16)}"
            val humanId = if (existing != null) {
                existing.humanId
            } else {
                val seq = ws.nextVocabSequence
                workspaceDao.upsertWorkspace(ws.copy(nextVocabSequence = seq + 1, updatedAt = now))
                syncEngine.enqueueOperation(workspaceId, "WORKSPACE", workspaceId, SyncOpType.UPDATE, "{}", 1L)
                WordNormalizer.formatHumanId(seq)
            }

            val nextVersion = if (existing != null) existing.version + 1L else 1L
            val entity = WordEntity(
                id = wordId,
                humanId = humanId,
                workspaceId = workspaceId,
                word = cleanedWord,
                normalizedWord = WordNormalizer.normalizeWord(cleanedWord),
                partOfSpeech = draft.partOfSpeech.name,
                primaryMeaning = cleanedMeaning,
                englishDefinition = draft.englishDefinition.trim(),
                arabicExplanation = draft.arabicExplanation.trim(),
                phoneticText = draft.phoneticText.trim(),
                exampleSentencesJson = WordJsonCodec.encodeStringList(draft.exampleSentences),
                meaningsJson = WordJsonCodec.encodeMeanings(draft.meanings),
                synonymsJson = WordJsonCodec.encodeSynonyms(draft.synonyms),
                antonymsJson = WordJsonCodec.encodeAntonyms(draft.antonyms),
                derivativesJson = WordJsonCodec.encodeDerivatives(draft.derivatives),
                expressionsJson = WordJsonCodec.encodeExpressions(draft.expressions),
                notesJson = WordJsonCodec.encodeNotes(draft.notes),
                version = nextVersion,
                lastSyncedSnapshotJson = existing?.lastSyncedSnapshotJson.orEmpty(),
                createdBy = existing?.createdBy ?: uid,
                updatedBy = uid,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
                syncStatus = "PENDING"
            )

            wordDao.upsertWord(entity)
            syncEngine.enqueueOperation(
                workspaceId = workspaceId,
                entityType = "WORD",
                entityId = wordId,
                opType = if (existing == null) SyncOpType.CREATE else SyncOpType.UPDATE,
                payloadJson = ConflictEngine.snapshotToJson(entity),
                baseVersion = existing?.version ?: 1L
            )
            logAudit(
                workspaceId = workspaceId,
                member = member!!,
                action = if (existing == null) "CREATE_WORD" else "UPDATE_WORD",
                entityType = "WORD",
                entityId = wordId,
                summary = if (existing == null) {
                    "إضافة المفردة ${entity.humanId}: ${entity.word}"
                } else {
                    "تعديل المفردة ${entity.humanId}: ${entity.word} (نسخة ${entity.version})"
                }
            )
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteWords(workspaceId: String, wordIds: Set<String>): Result<Int> {
        return try {
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            RbacGuard.requireDeleteVocab(member)

            val now = System.currentTimeMillis()
            var count = 0
            for (id in wordIds) {
                val word = wordDao.getWordById(id) ?: continue
                wordDao.softDeleteWord(id, now, now, uid)
                syncEngine.enqueueOperation(workspaceId, "WORD", id, SyncOpType.DELETE, "{}", word.version)
                logAudit(
                    workspaceId = workspaceId,
                    member = member!!,
                    action = "DELETE_WORD",
                    entityType = "WORD",
                    entityId = id,
                    summary = "حذف المفردة ${word.humanId}: ${word.word}"
                )
                count++
            }
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveBook(
        workspaceId: String,
        existingId: String?,
        title: String,
        publisher: String,
        editionOrYear: String,
        grade: String,
        notes: String
    ): Result<BookEntity> {
        return try {
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            RbacGuard.requireManageBooks(member)
            require(title.trim().isNotBlank()) { "عنوان الكتاب مطلوب." }

            val now = System.currentTimeMillis()
            val existing = existingId?.let { bookDao.getBookById(it) }
            val bookId = existing?.id ?: "bk_${UUID.randomUUID().toString().take(12)}"
            val entity = BookEntity(
                id = bookId,
                workspaceId = workspaceId,
                title = title.trim(),
                publisher = publisher.trim(),
                editionOrYear = editionOrYear.trim(),
                grade = grade.trim(),
                notes = notes.trim(),
                version = if (existing != null) existing.version + 1 else 1L,
                createdBy = existing?.createdBy ?: uid,
                updatedBy = uid,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                syncStatus = "PENDING"
            )
            bookDao.upsertBook(entity)
            syncEngine.enqueueOperation(
                workspaceId,
                "BOOK",
                bookId,
                if (existing == null) SyncOpType.CREATE else SyncOpType.UPDATE,
                "{}",
                existing?.version ?: 1L
            )
            logAudit(workspaceId, member!!, "SAVE_BOOK", "BOOK", bookId, "حفظ بيانات الكتاب: ${entity.title}")
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveWordLocation(workspaceId: String, draft: WordLocationDraft): Result<WordLocationEntity> {
        return try {
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            RbacGuard.requireEditVocab(member)
            require(draft.bookName.isNotBlank()) { "اسم الكتاب مطلوب." }

            val now = System.currentTimeMillis()
            val locId = draft.existingId ?: "loc_${UUID.randomUUID().toString().take(12)}"
            val existing = bookDao.getLocationsSync(workspaceId).firstOrNull { it.id == locId }
            val entity = WordLocationEntity(
                id = locId,
                workspaceId = workspaceId,
                wordId = draft.wordId,
                bookId = draft.bookId.ifBlank { "bk_manual" },
                bookName = draft.bookName.trim(),
                publisher = draft.publisher.trim(),
                editionOrYear = draft.editionOrYear.trim(),
                unit = draft.unit.trim(),
                lesson = draft.lesson.trim(),
                pageNumber = draft.pageNumber.trim(),
                exerciseNumber = draft.exerciseNumber.trim(),
                section = draft.section.trim(),
                quotation = draft.quotation.trim(),
                sourceType = draft.sourceType.trim(),
                notes = draft.notes.trim(),
                version = if (existing != null) existing.version + 1 else 1L,
                createdBy = existing?.createdBy ?: uid,
                updatedBy = uid,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                syncStatus = "PENDING"
            )
            bookDao.upsertLocation(entity)
            syncEngine.enqueueOperation(
                workspaceId,
                "LOCATION",
                locId,
                if (existing == null) SyncOpType.CREATE else SyncOpType.UPDATE,
                "{}",
                existing?.version ?: 1L
            )
            logAudit(workspaceId, member!!, "SAVE_LOCATION", "WORD_LOCATION", locId, "تحديث مرجع الكتاب: ${entity.bookName} ص ${entity.pageNumber}")
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveCurriculumEntry(workspaceId: String, draft: CurriculumEntryDraft): Result<CurriculumEntryEntity> {
        return try {
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            RbacGuard.requireEditVocab(member)

            // Never allow user-created observations to be labeled as official verified curriculum
            val enforcedVerification = if (draft.entryType == CurriculumEntryType.USER_OBSERVATION) {
                VerificationStatus.PERSONAL_NOTE
            } else {
                val role = WorkspaceRole.safeValueOf(member?.role)
                if (draft.verificationStatus == VerificationStatus.VERIFIED_OFFICIAL && !role.canManageBooks()) {
                    VerificationStatus.PENDING_REVIEW
                } else {
                    draft.verificationStatus
                }
            }

            val now = System.currentTimeMillis()
            val curId = draft.existingId ?: "cur_${UUID.randomUUID().toString().take(12)}"
            val existing = bookDao.getCurriculumSync(workspaceId).firstOrNull { it.id == curId }
            val entity = CurriculumEntryEntity(
                id = curId,
                workspaceId = workspaceId,
                wordId = draft.wordId,
                entryType = draft.entryType.name,
                curriculumDistinction = draft.curriculumDistinction.trim(),
                importantExamNotes = draft.importantExamNotes.trim(),
                commonExamPatterns = draft.commonExamPatterns.trim(),
                teacherNotes = draft.teacherNotes.trim(),
                relatedGrammarRules = draft.relatedGrammarRules.trim(),
                grade = draft.grade.trim(),
                academicYear = draft.academicYear.trim(),
                unit = draft.unit.trim(),
                lesson = draft.lesson.trim(),
                source = draft.source.trim(),
                verificationStatus = enforcedVerification.name,
                version = if (existing != null) existing.version + 1 else 1L,
                createdBy = existing?.createdBy ?: uid,
                updatedBy = uid,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                syncStatus = "PENDING"
            )
            bookDao.upsertCurriculum(entity)
            syncEngine.enqueueOperation(
                workspaceId,
                "CURRICULUM",
                curId,
                if (existing == null) SyncOpType.CREATE else SyncOpType.UPDATE,
                "{}",
                existing?.version ?: 1L
            )
            logAudit(
                workspaceId,
                member!!,
                "SAVE_CURRICULUM",
                "CURRICULUM",
                curId,
                "حفظ ${draft.entryType.arabicLabel} (${enforcedVerification.arabicLabel})"
            )
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestAiSuggestionToReviewQueue(
        workspaceId: String,
        word: WordEntity,
        focusArea: String,
        compareWithWord: String = "",
        aiEnabled: Boolean
    ): Result<ReviewQueueItemEntity> {
        return try {
            if (!aiEnabled) {
                throw IllegalStateException("ميزة الذكاء الاصطناعي الاختيارية معطّلة افتراضياً. يمكنك تفعيلها من إعدادات المساحة.")
            }
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            RbacGuard.requireEditVocab(member)

            val suggestion = aiService.generateVocabularySuggestion(
                word = word.word,
                currentMeaning = word.primaryMeaning,
                focusArea = focusArea,
                compareWithWord = compareWithWord
            ).getOrThrow()

            val now = System.currentTimeMillis()
            val item = ReviewQueueItemEntity(
                id = "rq_${UUID.randomUUID().toString().take(12)}",
                workspaceId = workspaceId,
                targetWordId = word.id,
                targetWordText = word.word,
                sourceType = ReviewSourceType.AI_SUGGESTION.name,
                suggestionType = suggestion.suggestionType,
                proposedPayload = suggestion.payloadJson,
                summaryArabic = suggestion.summaryArabic,
                aiGenerated = true,
                verified = false, // Never verified automatically
                status = ReviewStatus.PENDING.name,
                createdBy = uid,
                createdAt = now,
                updatedAt = now,
                syncStatus = "PENDING"
            )
            govDao.upsertReviewItem(item)
            syncEngine.enqueueOperation(workspaceId, "REVIEW", item.id, SyncOpType.CREATE, "{}", 1L)
            logAudit(
                workspaceId,
                member!!,
                "AI_SUGGESTION_QUEUED",
                "REVIEW_QUEUE",
                item.id,
                "إضافة اقتراح ذكاء اصطناعي غير موثق للكلمة ${word.word} إلى قائمة المراجعة"
            )
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun decideReviewItem(
        workspaceId: String,
        itemId: String,
        approve: Boolean,
        reviewNotes: String
    ): Result<Unit> {
        return try {
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            RbacGuard.requireReview(member)

            val item = govDao.getReviewItemById(itemId)
                ?: throw IllegalStateException("عنصر المراجعة غير موجود.")
            val now = System.currentTimeMillis()
            val newStatus = if (approve) ReviewStatus.APPROVED else ReviewStatus.REJECTED

            if (approve) {
                applyApprovedSuggestionToWord(workspaceId, item, uid)
            }

            govDao.upsertReviewItem(
                item.copy(
                    status = newStatus.name,
                    verified = approve,
                    reviewedBy = uid,
                    reviewNotes = reviewNotes.trim(),
                    updatedAt = now,
                    syncStatus = "PENDING"
                )
            )
            syncEngine.enqueueOperation(workspaceId, "REVIEW", item.id, SyncOpType.UPDATE, "{}", 1L)
            logAudit(
                workspaceId,
                member!!,
                if (approve) "APPROVE_REVIEW" else "REJECT_REVIEW",
                "REVIEW_QUEUE",
                item.id,
                "${if (approve) "اعتماد" else "رفض"} مقترح للكلمة ${item.targetWordText}"
            )
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun applyApprovedSuggestionToWord(
        workspaceId: String,
        item: ReviewQueueItemEntity,
        uid: String
    ) {
        val word = wordDao.getWordById(item.targetWordId) ?: return
        val json = runCatching { JSONObject(item.proposedPayload) }.getOrNull() ?: return

        val existingMeanings = WordJsonCodec.decodeMeanings(word.meaningsJson).toMutableList()
        val aiMeanings = json.optJSONArray("meanings")
        if (aiMeanings != null) {
            for (i in 0 until aiMeanings.length()) {
                val m = aiMeanings.optString(i).trim()
                if (m.isNotBlank() && existingMeanings.none { it.arabicMeaning == m } && m != word.primaryMeaning) {
                    existingMeanings.add(
                        MeaningItem(
                            id = UUID.randomUUID().toString().take(8),
                            arabicMeaning = m,
                            englishNuance = "معتمد من اقتراح ذكاء اصطناعي"
                        )
                    )
                }
            }
        }

        val existingSyns = WordJsonCodec.decodeSynonyms(word.synonymsJson).toMutableList()
        val aiSyns = json.optJSONArray("synonyms")
        if (aiSyns != null) {
            for (i in 0 until aiSyns.length()) {
                val s = aiSyns.optString(i).trim()
                if (s.isNotBlank() && existingSyns.none { it.word.equals(s, ignoreCase = true) }) {
                    existingSyns.add(SynonymItem(id = UUID.randomUUID().toString().take(8), word = s, meaning = "مرادف مقترح"))
                }
            }
        }

        val existingAnts = WordJsonCodec.decodeAntonyms(word.antonymsJson).toMutableList()
        val aiAnts = json.optJSONArray("antonyms")
        if (aiAnts != null) {
            for (i in 0 until aiAnts.length()) {
                val a = aiAnts.optString(i).trim()
                if (a.isNotBlank() && existingAnts.none { it.word.equals(a, ignoreCase = true) }) {
                    existingAnts.add(AntonymItem(id = UUID.randomUUID().toString().take(8), word = a, meaning = "مضاد مقترح"))
                }
            }
        }

        val existingExps = WordJsonCodec.decodeExpressions(word.expressionsJson).toMutableList()
        val aiCols = json.optJSONArray("collocations")
        if (aiCols != null) {
            for (i in 0 until aiCols.length()) {
                val c = aiCols.optString(i).trim()
                if (c.isNotBlank() && existingExps.none { it.expression.equals(c, ignoreCase = true) }) {
                    existingExps.add(ExpressionItem(id = UUID.randomUUID().toString().take(8), expression = c))
                }
            }
        }

        val existingExamples = WordJsonCodec.decodeStringList(word.exampleSentencesJson).toMutableList()
        val aiEx = json.optJSONArray("exampleSentences")
        if (aiEx != null) {
            for (i in 0 until aiEx.length()) {
                val ex = aiEx.optString(i).trim()
                if (ex.isNotBlank() && existingExamples.none { it.equals(ex, ignoreCase = true) }) {
                    existingExamples.add(ex)
                }
            }
        }

        val updated = word.copy(
            meaningsJson = WordJsonCodec.encodeMeanings(existingMeanings),
            synonymsJson = WordJsonCodec.encodeSynonyms(existingSyns),
            antonymsJson = WordJsonCodec.encodeAntonyms(existingAnts),
            expressionsJson = WordJsonCodec.encodeExpressions(existingExps),
            exampleSentencesJson = WordJsonCodec.encodeStringList(existingExamples),
            version = word.version + 1L,
            updatedBy = uid,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING"
        )
        wordDao.upsertWord(updated)
        syncEngine.enqueueOperation(workspaceId, "WORD", updated.id, SyncOpType.UPDATE, "{}", word.version)
    }

    suspend fun commitImportBatch(
        workspaceId: String,
        fileName: String,
        sourceFormat: String,
        rows: List<ImportPreviewRow>,
        onProgress: (Float) -> Unit
    ): Result<ImportReport> {
        return try {
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            val ws = workspaceDao.getWorkspaceById(workspaceId)
                ?: throw IllegalStateException("مساحة العمل غير موجودة.")
            RbacGuard.requireImport(member, ws)

            var created = 0
            var updated = 0
            var merged = 0
            var skipped = 0
            var errors = 0
            val errorLogs = mutableListOf<String>()
            var nextSeq = ws.nextVocabSequence
            val now = System.currentTimeMillis()

            val total = rows.size.coerceAtLeast(1)
            rows.forEachIndexed { index, row ->
                onProgress((index + 1).toFloat() / total.toFloat())
                if (row.validationError != null) {
                    errors++
                    errorLogs.add("صف ${row.rowIndex}: ${row.validationError}")
                    return@forEachIndexed
                }

                val existing = row.matchedWordId?.let { wordDao.getWordById(it) }
                if (existing != null) {
                    when (row.strategy) {
                        DuplicateStrategy.SKIP -> {
                            skipped++
                        }
                        DuplicateStrategy.MERGE -> {
                            val mergedSyns = (WordJsonCodec.decodeSynonyms(existing.synonymsJson) +
                                row.synonyms.map { SynonymItem(UUID.randomUUID().toString().take(8), it) })
                                .distinctBy { it.word.lowercase() }
                            val mergedAnts = (WordJsonCodec.decodeAntonyms(existing.antonymsJson) +
                                row.antonyms.map { AntonymItem(UUID.randomUUID().toString().take(8), it) })
                                .distinctBy { it.word.lowercase() }
                            val mergedMeanings = (WordJsonCodec.decodeMeanings(existing.meaningsJson) +
                                row.additionalMeanings.map { MeaningItem(UUID.randomUUID().toString().take(8), it) })
                                .distinctBy { it.arabicMeaning }
                            val mergedExamples = (WordJsonCodec.decodeStringList(existing.exampleSentencesJson) + row.exampleSentences).distinct()

                            val nextWord = existing.copy(
                                englishDefinition = existing.englishDefinition.ifBlank { row.englishDefinition },
                                arabicExplanation = existing.arabicExplanation.ifBlank { row.arabicExplanation },
                                phoneticText = existing.phoneticText.ifBlank { row.phoneticText },
                                meaningsJson = WordJsonCodec.encodeMeanings(mergedMeanings),
                                synonymsJson = WordJsonCodec.encodeSynonyms(mergedSyns),
                                antonymsJson = WordJsonCodec.encodeAntonyms(mergedAnts),
                                exampleSentencesJson = WordJsonCodec.encodeStringList(mergedExamples),
                                version = existing.version + 1,
                                updatedBy = uid,
                                updatedAt = now,
                                syncStatus = "PENDING"
                            )
                            wordDao.upsertWord(nextWord)
                            syncEngine.enqueueOperation(workspaceId, "WORD", nextWord.id, SyncOpType.UPDATE, "{}", existing.version)
                            createBookLocationIfPresent(workspaceId, nextWord.id, row, uid, now)
                            merged++
                        }
                        DuplicateStrategy.UPDATE_EXISTING -> {
                            val nextWord = existing.copy(
                                word = row.rawWord,
                                normalizedWord = row.normalizedWord,
                                partOfSpeech = row.partOfSpeech.name,
                                primaryMeaning = row.primaryMeaning,
                                englishDefinition = row.englishDefinition.ifBlank { existing.englishDefinition },
                                arabicExplanation = row.arabicExplanation.ifBlank { existing.arabicExplanation },
                                phoneticText = row.phoneticText.ifBlank { existing.phoneticText },
                                version = existing.version + 1,
                                updatedBy = uid,
                                updatedAt = now,
                                syncStatus = "PENDING"
                            )
                            wordDao.upsertWord(nextWord)
                            syncEngine.enqueueOperation(workspaceId, "WORD", nextWord.id, SyncOpType.UPDATE, "{}", existing.version)
                            createBookLocationIfPresent(workspaceId, nextWord.id, row, uid, now)
                            updated++
                        }
                    }
                } else {
                    val wordId = "w_${UUID.randomUUID().toString().take(16)}"
                    val humanId = WordNormalizer.formatHumanId(nextSeq++)
                    val newWord = WordEntity(
                        id = wordId,
                        humanId = humanId,
                        workspaceId = workspaceId,
                        word = row.rawWord,
                        normalizedWord = row.normalizedWord,
                        partOfSpeech = row.partOfSpeech.name,
                        primaryMeaning = row.primaryMeaning,
                        englishDefinition = row.englishDefinition,
                        arabicExplanation = row.arabicExplanation,
                        phoneticText = row.phoneticText,
                        exampleSentencesJson = WordJsonCodec.encodeStringList(row.exampleSentences),
                        meaningsJson = WordJsonCodec.encodeMeanings(
                            row.additionalMeanings.map { MeaningItem(UUID.randomUUID().toString().take(8), it) }
                        ),
                        synonymsJson = WordJsonCodec.encodeSynonyms(
                            row.synonyms.map { SynonymItem(UUID.randomUUID().toString().take(8), it) }
                        ),
                        antonymsJson = WordJsonCodec.encodeAntonyms(
                            row.antonyms.map { AntonymItem(UUID.randomUUID().toString().take(8), it) }
                        ),
                        derivativesJson = WordJsonCodec.encodeDerivatives(
                            row.derivatives.map { DerivativeItem(UUID.randomUUID().toString().take(8), it) }
                        ),
                        expressionsJson = WordJsonCodec.encodeExpressions(
                            row.expressions.map { ExpressionItem(UUID.randomUUID().toString().take(8), expression = it) }
                        ),
                        version = 1L,
                        createdBy = uid,
                        updatedBy = uid,
                        createdAt = now,
                        updatedAt = now,
                        syncStatus = "PENDING"
                    )
                    wordDao.upsertWord(newWord)
                    syncEngine.enqueueOperation(workspaceId, "WORD", wordId, SyncOpType.CREATE, "{}", 1L)
                    createBookLocationIfPresent(workspaceId, wordId, row, uid, now)
                    created++
                }
            }

            workspaceDao.upsertWorkspace(ws.copy(nextVocabSequence = nextSeq, updatedAt = now))
            val jobId = "imp_${UUID.randomUUID().toString().take(12)}"
            val job = ImportJobEntity(
                id = jobId,
                workspaceId = workspaceId,
                fileName = fileName,
                sourceFormat = sourceFormat,
                userId = uid,
                userName = member!!.displayName,
                totalRows = rows.size,
                createdCount = created,
                updatedCount = updated,
                mergedCount = merged,
                skippedCount = skipped,
                errorCount = errors,
                status = if (errors > 0 && created + updated + merged == 0) "FAILED" else "COMPLETED",
                errorsJson = JSONArray(errorLogs).toString(),
                createdAt = now,
                completedAt = System.currentTimeMillis()
            )
            govDao.upsertImportJob(job)
            logAudit(
                workspaceId,
                member,
                "IMPORT_BATCH",
                "IMPORT",
                jobId,
                "استيراد $fileName: إضافة $created، دمج $merged، تحديث $updated، تخطي $skipped، أخطاء $errors"
            )
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(
                ImportReport(
                    jobId = jobId,
                    fileName = fileName,
                    totalRows = rows.size,
                    createdCount = created,
                    updatedCount = updated,
                    mergedCount = merged,
                    skippedCount = skipped,
                    errorCount = errors,
                    errors = errorLogs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createBookLocationIfPresent(
        workspaceId: String,
        wordId: String,
        row: ImportPreviewRow,
        uid: String,
        now: Long
    ) {
        if (row.bookName.isBlank()) return
        val existingBook = bookDao.getBooksSync(workspaceId).firstOrNull {
            it.title.equals(row.bookName, ignoreCase = true)
        }
        val bookId = existingBook?.id ?: "bk_${UUID.randomUUID().toString().take(10)}".also { newId ->
            bookDao.upsertBook(
                BookEntity(
                    id = newId,
                    workspaceId = workspaceId,
                    title = row.bookName,
                    createdBy = uid,
                    updatedBy = uid,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = "PENDING"
                )
            )
            syncEngine.enqueueOperation(workspaceId, "BOOK", newId, SyncOpType.CREATE, "{}", 1L)
        }
        val locId = "loc_${UUID.randomUUID().toString().take(12)}"
        bookDao.upsertLocation(
            WordLocationEntity(
                id = locId,
                workspaceId = workspaceId,
                wordId = wordId,
                bookId = bookId,
                bookName = row.bookName,
                unit = row.unit,
                lesson = row.lesson,
                pageNumber = row.pageNumber,
                createdBy = uid,
                updatedBy = uid,
                createdAt = now,
                updatedAt = now,
                syncStatus = "PENDING"
            )
        )
        syncEngine.enqueueOperation(workspaceId, "LOCATION", locId, SyncOpType.CREATE, "{}", 1L)
    }

    suspend fun resolveConflictRecord(
        workspaceId: String,
        conflictId: String,
        choices: Map<String, ConflictChoice>
    ): Result<WordEntity> {
        return try {
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            val role = RbacGuard.requireActiveMember(member)
            if (!role.canResolveConflicts()) {
                throw SecurityException("غير مصرح: حل التعارضات متاح للمالك أو المحرر فقط.")
            }

            val conflict = govDao.getConflictById(conflictId)
                ?: throw IllegalStateException("سجل التعارض غير موجود.")
            val word = wordDao.getWordById(conflict.wordId)
                ?: throw IllegalStateException("المفردة المرتبطة غير موجودة.")

            val resolvedWord = ConflictEngine.resolveConflict(word, conflict, choices, uid)
            wordDao.upsertWord(resolvedWord)
            govDao.upsertConflict(
                conflict.copy(
                    status = "RESOLVED",
                    resolvedBy = uid,
                    resolvedAt = System.currentTimeMillis()
                )
            )
            syncEngine.enqueueOperation(
                workspaceId,
                "WORD",
                resolvedWord.id,
                SyncOpType.UPDATE,
                ConflictEngine.snapshotToJson(resolvedWord),
                maxOf(conflict.localVersion, conflict.remoteVersion)
            )
            logAudit(
                workspaceId,
                member!!,
                "RESOLVE_CONFLICT",
                "CONFLICT",
                conflictId,
                "حل تعارض التعديل للمفردة ${conflict.humanId} (${conflict.wordText})"
            )
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(resolvedWord)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addOrUpdateMember(
        workspaceId: String,
        targetUid: String,
        displayName: String,
        email: String,
        role: WorkspaceRole,
        status: MemberStatus
    ): Result<Unit> {
        return try {
            val actorUid = requireUid()
            val actor = workspaceDao.getMember(workspaceId, actorUid)
            RbacGuard.requireManageUsers(actor)
            require(targetUid.isNotBlank() && displayName.isNotBlank()) { "معرّف المستخدم والاسم مطلوبان." }

            val ws = workspaceDao.getWorkspaceById(workspaceId)
                ?: throw IllegalStateException("مساحة العمل غير موجودة.")
            val now = System.currentTimeMillis()
            val existing = workspaceDao.getMember(workspaceId, targetUid)

            val member = WorkspaceMemberEntity(
                id = "${workspaceId}_$targetUid",
                workspaceId = workspaceId,
                ownerId = ws.ownerId,
                userId = targetUid.trim(),
                displayName = displayName.trim(),
                email = email.trim(),
                role = role.name,
                status = status.name,
                invitedBy = existing?.invitedBy ?: actorUid,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            workspaceDao.upsertMember(member)

            val updatedUids = (ws.memberUidsCsv.split(",").filter { it.isNotBlank() } + targetUid.trim()).distinct()
            workspaceDao.upsertWorkspace(ws.copy(memberUidsCsv = updatedUids.joinToString(","), updatedAt = now))
            syncEngine.enqueueOperation(workspaceId, "WORKSPACE", workspaceId, SyncOpType.UPDATE, "{}", 1L)
            syncEngine.enqueueOperation(
                workspaceId,
                "MEMBER",
                targetUid.trim(),
                if (existing == null) SyncOpType.CREATE else SyncOpType.UPDATE,
                "{}",
                1L
            )
            logAudit(
                workspaceId,
                actor!!,
                "MANAGE_MEMBER",
                "MEMBER",
                targetUid,
                "تحديث عضوية ${displayName.trim()} بدور ${role.arabicLabel} (${status.arabicLabel})"
            )
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeMember(workspaceId: String, targetUid: String): Result<Unit> {
        return try {
            val actorUid = requireUid()
            val actor = workspaceDao.getMember(workspaceId, actorUid)
            RbacGuard.requireManageUsers(actor)
            require(targetUid != actorUid) { "لا يمكن إزالة مالك المساحة." }

            workspaceDao.deleteMember(workspaceId, targetUid)
            val ws = workspaceDao.getWorkspaceById(workspaceId)
            if (ws != null) {
                val updatedUids = ws.memberUidsCsv.split(",").filter { it.isNotBlank() && it != targetUid }
                workspaceDao.upsertWorkspace(ws.copy(memberUidsCsv = updatedUids.joinToString(","), updatedAt = System.currentTimeMillis()))
                syncEngine.enqueueOperation(workspaceId, "WORKSPACE", workspaceId, SyncOpType.UPDATE, "{}", 1L)
            }
            syncEngine.enqueueOperation(workspaceId, "MEMBER", targetUid, SyncOpType.DELETE, "{}", 1L)
            logAudit(workspaceId, actor!!, "REMOVE_MEMBER", "MEMBER", targetUid, "إزالة عضو من مساحة العمل")
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateWorkspaceSettings(
        workspaceId: String,
        name: String,
        description: String,
        allowViewerExport: Boolean,
        allowEditorImport: Boolean
    ): Result<Unit> {
        return try {
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            RbacGuard.requireManageSettings(member)
            require(name.isNotBlank()) { "اسم مساحة العمل مطلوب." }

            val ws = workspaceDao.getWorkspaceById(workspaceId)
                ?: throw IllegalStateException("مساحة العمل غير موجودة.")
            workspaceDao.upsertWorkspace(
                ws.copy(
                    name = name.trim(),
                    description = description.trim(),
                    allowViewerExport = allowViewerExport,
                    allowEditorImport = allowEditorImport,
                    updatedAt = System.currentTimeMillis()
                )
            )
            syncEngine.enqueueOperation(workspaceId, "WORKSPACE", workspaceId, SyncOpType.UPDATE, "{}", 1L)
            logAudit(workspaceId, member!!, "UPDATE_SETTINGS", "WORKSPACE", workspaceId, "تحديث إعدادات مساحة العمل")
            syncEngine.synchronizeWorkspace(workspaceId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun simulateConflictForTesting(workspaceId: String, wordId: String): Result<Unit> {
        return try {
            val uid = requireUid()
            val member = workspaceDao.getMember(workspaceId, uid)
            RbacGuard.requireEditVocab(member)
            val local = wordDao.getWordById(wordId)
                ?: throw IllegalStateException("اختر كلمة موجودة لمحاكاة تعارض التعديل.")

            val simulatedRemote = local.copy(
                primaryMeaning = "${local.primaryMeaning} (تعديل مستخدم آخر)",
                arabicExplanation = "تحديث متزامن من جهاز آخر للمراجعة",
                version = local.version + 1,
                updatedBy = "collaborator_remote"
            )
            val baseWord = local.copy(
                primaryMeaning = "${local.primaryMeaning} (النسخة الأصلية)",
                lastSyncedSnapshotJson = ConflictEngine.snapshotToJson(local)
            )
            val outcome = ConflictEngine.evaluateThreeWayMerge(
                localWord = local.copy(lastSyncedSnapshotJson = ConflictEngine.snapshotToJson(baseWord)),
                remoteWord = simulatedRemote,
                actorUserId = uid
            )
            if (outcome is com.example.domain.MergeOutcome.ConflictDetected) {
                govDao.upsertConflict(outcome.conflictRecord)
                wordDao.upsertWord(local.copy(syncStatus = "CONFLICT"))
                logAudit(
                    workspaceId,
                    member!!,
                    "CONFLICT_DETECTED",
                    "CONFLICT",
                    outcome.conflictRecord.id,
                    "اكتشاف تعارض نسخ للمفردة ${local.humanId}: ${local.word}"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadEducationalStarterPack(workspaceId: String): Result<Int> {
        val sampleRows = listOf(
            ImportPreviewRow(
                rowIndex = 1,
                rawWord = "Abandon",
                normalizedWord = "abandon",
                partOfSpeech = PartOfSpeech.VERB,
                primaryMeaning = "يهجر / يتخلى عن",
                additionalMeanings = listOf("يترك نهائياً", "يتنازل عن"),
                englishDefinition = "To leave someone or something completely and permanently.",
                arabicExplanation = "تُستخدم بمعنى التخلي التام عن شيء أو مكان أو خطة.",
                phoneticText = "/əˈbændən/",
                exampleSentences = listOf("They had to abandon the damaged ship before sunset."),
                synonyms = listOf("forsake", "desert"),
                antonyms = listOf("keep", "maintain"),
                derivatives = listOf("abandonment", "abandoned"),
                expressions = listOf("abandon hope"),
                bookName = "New Enterprise B2",
                unit = "Unit 1",
                lesson = "Lesson 2",
                pageNumber = "14"
            ),
            ImportPreviewRow(
                rowIndex = 2,
                rawWord = "Resilient",
                normalizedWord = "resilient",
                partOfSpeech = PartOfSpeech.ADJECTIVE,
                primaryMeaning = "مرن / قادر على التعافي بسرعة",
                additionalMeanings = listOf("صامد أمام الصعاب"),
                englishDefinition = "Able to withstand or recover quickly from difficult conditions.",
                arabicExplanation = "وصف للشخص أو النظام القادر على استعادة توازنه بعد الأزمات.",
                phoneticText = "/rɪˈzɪliənt/",
                exampleSentences = listOf("Children are often remarkably resilient in new environments."),
                synonyms = listOf("adaptable", "tough"),
                antonyms = listOf("fragile", "vulnerable"),
                derivatives = listOf("resilience", "resiliently"),
                expressions = listOf("resilient economy"),
                bookName = "New Enterprise B2",
                unit = "Unit 2",
                lesson = "Lesson 1",
                pageNumber = "28"
            ),
            ImportPreviewRow(
                rowIndex = 3,
                rawWord = "Carry out",
                normalizedWord = "carry out",
                partOfSpeech = PartOfSpeech.PHRASAL_VERB,
                primaryMeaning = "ينفّذ / يُنجز",
                additionalMeanings = listOf("يُجري تجربة أو دراسة"),
                englishDefinition = "To perform or complete a task, research, or order.",
                arabicExplanation = "فعل مركب شائع في سياقات البحث العلمي وتنفيذ الخطط.",
                phoneticText = "/ˈkæri aʊt/",
                exampleSentences = listOf("The researchers will carry out a comprehensive survey."),
                synonyms = listOf("execute", "conduct"),
                antonyms = listOf("neglect", "cancel"),
                derivatives = emptyList(),
                expressions = listOf("carry out an experiment"),
                bookName = "Academic Writing Skills",
                unit = "Unit 3",
                lesson = "Lesson 4",
                pageNumber = "52"
            )
        )
        return commitImportBatch(
            workspaceId = workspaceId,
            fileName = "حزمة_تعليمية_نموذجية.json",
            sourceFormat = "STARTER_SAMPLE",
            rows = sampleRows,
            onProgress = {}
        ).map { it.createdCount }
    }

    suspend fun logExportAudit(workspaceId: String, summary: String) {
        val uid = auth.currentUser?.uid ?: return
        val member = workspaceDao.getMember(workspaceId, uid) ?: return
        logAudit(workspaceId, member, "EXPORT_DATA", "EXPORT", workspaceId, summary)
    }

    private suspend fun logAudit(
        workspaceId: String,
        member: WorkspaceMemberEntity,
        action: String,
        entityType: String,
        entityId: String,
        summary: String
    ) {
        val logId = "log_${UUID.randomUUID().toString().take(12)}"
        val entity = AuditLogEntity(
            id = logId,
            workspaceId = workspaceId,
            actorUserId = member.userId,
            actorName = member.displayName,
            actorRole = member.role,
            action = action,
            entityType = entityType,
            entityId = entityId,
            summary = summary,
            createdAt = System.currentTimeMillis()
        )
        govDao.insertAuditLog(entity)
        val payload = JSONObject().apply {
            put("actorUserId", entity.actorUserId)
            put("actorName", entity.actorName)
            put("actorRole", entity.actorRole)
            put("action", entity.action)
            put("entityType", entity.entityType)
            put("entityId", entity.entityId)
            put("summary", entity.summary)
        }.toString()
        syncEngine.enqueueOperation(workspaceId, "AUDIT", logId, SyncOpType.CREATE, payload, 1L)
    }
}
