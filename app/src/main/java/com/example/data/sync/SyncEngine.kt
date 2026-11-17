package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.R
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
import com.example.domain.ConflictEngine
import com.example.domain.MergeOutcome
import com.example.domain.WordJsonCodec
import com.example.firebase.FirestoreOperationType
import com.example.firebase.handleFirestoreError
import com.example.model.SyncOpType
import com.example.model.SyncState
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SyncEngine(
    context: Context,
    private val db: AppDatabase,
    private val auth: FirebaseAuth
) {
    private val appContext = context.applicationContext
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        appContext.getString(R.string.firestore_database_id)
    )
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isOnline = MutableStateFlow(checkCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val _lastSuccessTimestamp = MutableStateFlow(0L)
    val lastSuccessTimestamp: StateFlow<Long> = _lastSuccessTimestamp.asStateFlow()

    private var wordsListener: ListenerRegistration? = null
    private var booksListener: ListenerRegistration? = null
    private var activeListeningWorkspaceId: String? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            val wsId = activeListeningWorkspaceId
            if (!wsId.isNullOrBlank() && auth.currentUser != null) {
                scope.launch { synchronizeWorkspace(wsId) }
            }
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentlyOnline()
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    private fun checkCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun computeSyncState(pendingCount: Int, openConflictsCount: Int): SyncState {
        return when {
            openConflictsCount > 0 -> SyncState.CONFLICT_REQUIRES_REVIEW
            _isSyncing.value -> SyncState.SYNCHRONIZING
            _lastSyncError.value != null -> SyncState.SYNC_ERROR
            !_isOnline.value -> SyncState.OFFLINE
            pendingCount > 0 -> SyncState.PENDING_CHANGES
            else -> SyncState.ONLINE_SYNCED
        }
    }

    fun startRealtimePull(workspaceId: String, uid: String) {
        if (uid.isBlank() || workspaceId.isBlank()) return
        if (activeListeningWorkspaceId == workspaceId && wordsListener != null) return
        stopRealtimePull()
        activeListeningWorkspaceId = workspaceId

        val wordsPath = "workspaces/$workspaceId/words"
        wordsListener = firestore.collection("workspaces").document(workspaceId)
            .collection("words")
            .whereArrayContains("memberUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, FirestoreOperationType.LIST, wordsPath)
                        _lastSyncError.value = error.localizedMessage ?: "تعذر الاستماع للتحديثات السحابية"
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    scope.launch {
                        mergeRemoteWordsSnapshot(workspaceId, snapshot.documents, uid)
                    }
                }
            }

        val booksPath = "workspaces/$workspaceId/books"
        booksListener = firestore.collection("workspaces").document(workspaceId)
            .collection("books")
            .whereArrayContains("memberUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, FirestoreOperationType.LIST, booksPath)
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    scope.launch {
                        snapshot.documents.forEach { doc ->
                            val remoteBook = docToBookEntity(doc, workspaceId) ?: return@forEach
                            val local = db.bookAndCurriculumDao().getBookById(remoteBook.id)
                            if (local == null || local.syncStatus == "SYNCED" || remoteBook.version > local.version) {
                                db.bookAndCurriculumDao().upsertBook(remoteBook.copy(syncStatus = "SYNCED"))
                            }
                        }
                    }
                }
            }
    }

    fun stopRealtimePull() {
        wordsListener?.remove()
        wordsListener = null
        booksListener?.remove()
        booksListener = null
        activeListeningWorkspaceId = null
    }

    fun shutdown() {
        stopRealtimePull()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    suspend fun enqueueOperation(
        workspaceId: String,
        entityType: String,
        entityId: String,
        opType: SyncOpType,
        payloadJson: String,
        baseVersion: Long
    ) {
        val op = SyncOperationEntity(
            operationId = "op_${UUID.randomUUID().toString().take(16)}",
            workspaceId = workspaceId,
            entityType = entityType,
            entityId = entityId,
            opType = opType.name,
            payloadJson = payloadJson,
            baseVersion = baseVersion,
            status = "PENDING",
            retryCount = 0
        )
        db.syncAndGovernanceDao().upsertSyncOperation(op)
    }

    suspend fun synchronizeWorkspace(workspaceId: String): Result<Int> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            return Result.failure(IllegalStateException("يجب تسجيل الدخول أولاً قبل المزامنة."))
        }
        if (!_isOnline.value) {
            return Result.failure(IllegalStateException("لا يوجد اتصال بالإنترنت. التغييرات محفوظة محلياً."))
        }
        if (_isSyncing.value) return Result.success(0)

        _isSyncing.value = true
        _lastSyncError.value = null
        var syncedCount = 0

        return try {
            val ws = db.workspaceDao().getWorkspaceById(workspaceId)
                ?: return Result.success(0)
            val memberUids = ws.memberUidsCsv.split(",").filter { it.isNotBlank() }.ifEmpty { listOf(uid) }

            val pendingOps = db.syncAndGovernanceDao().getPendingSyncOperations(workspaceId)
            for (op in pendingOps) {
                try {
                    val outcome = processSingleOperation(op, ws, memberUids, uid)
                    if (outcome) {
                        db.syncAndGovernanceDao().upsertSyncOperation(
                            op.copy(status = "SYNCED", lastError = null, updatedAt = System.currentTimeMillis())
                        )
                        syncedCount++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, FirestoreOperationType.WRITE, "workspaces/$workspaceId/${op.entityType}/${op.entityId}")
                    db.syncAndGovernanceDao().upsertSyncOperation(
                        op.copy(
                            status = "ERROR",
                            retryCount = op.retryCount + 1,
                            lastError = e.localizedMessage ?: "خطأ في المزامنة",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    _lastSyncError.value = e.localizedMessage ?: "تعذر إتمام مزامنة بعض السجلات"
                }
            }

            if (_lastSyncError.value == null) {
                _lastSuccessTimestamp.value = System.currentTimeMillis()
            }
            Result.success(syncedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _lastSyncError.value = e.localizedMessage ?: "فشلت المزامنة"
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun processSingleOperation(
        op: SyncOperationEntity,
        ws: WorkspaceEntity,
        memberUids: List<String>,
        uid: String
    ): Boolean {
        val wsRef = firestore.collection("workspaces").document(ws.id)
        when (op.entityType) {
            "WORKSPACE" -> {
                val batch = firestore.batch()
                val wsMap = mapOf(
                    "name" to ws.name,
                    "description" to ws.description,
                    "ownerId" to ws.ownerId,
                    "memberUids" to memberUids,
                    "allowViewerExport" to ws.allowViewerExport,
                    "allowEditorImport" to ws.allowEditorImport,
                    "nextVocabSequence" to ws.nextVocabSequence,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val docSnap = wsRef.get().await()
                if (!docSnap.exists()) {
                    batch.set(wsRef, wsMap)
                    val me = db.workspaceDao().getMember(ws.id, uid)
                    if (me != null) {
                        val memberMap = mapOf(
                            "workspaceId" to ws.id,
                            "ownerId" to ws.ownerId,
                            "userId" to me.userId,
                            "displayName" to me.displayName,
                            "email" to me.email,
                            "role" to me.role,
                            "status" to me.status,
                            "invitedBy" to me.invitedBy,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ).filterValues { it != null }
                        batch.set(wsRef.collection("members").document(uid), memberMap)
                    }
                    batch.commit().await()
                } else {
                    wsRef.update(
                        mapOf(
                            "name" to ws.name,
                            "description" to ws.description,
                            "memberUids" to memberUids,
                            "allowViewerExport" to ws.allowViewerExport,
                            "allowEditorImport" to ws.allowEditorImport,
                            "nextVocabSequence" to ws.nextVocabSequence,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                return true
            }
            "WORD" -> {
                val wordRef = wsRef.collection("words").document(op.entityId)
                if (op.opType == SyncOpType.DELETE.name) {
                    wordRef.delete().await()
                    return true
                }
                val localWord = db.wordDao().getWordById(op.entityId) ?: return true
                val remoteSnap = wordRef.get().await()
                if (!remoteSnap.exists()) {
                    val payload = wordToFirestoreMap(localWord, memberUids, isCreate = true)
                    wordRef.set(payload).await()
                    db.wordDao().upsertWord(
                        localWord.copy(
                            syncStatus = "SYNCED",
                            lastSyncedSnapshotJson = ConflictEngine.snapshotToJson(localWord)
                        )
                    )
                    return true
                } else {
                    val remoteWord = docToWordEntity(remoteSnap, ws.id)
                    if (remoteWord != null && remoteWord.version > op.baseVersion && op.opType == SyncOpType.UPDATE.name) {
                        when (val outcome = ConflictEngine.evaluateThreeWayMerge(localWord, remoteWord, uid)) {
                            is MergeOutcome.NoChangesNeeded -> {
                                db.wordDao().upsertWord(
                                    remoteWord.copy(
                                        syncStatus = "SYNCED",
                                        lastSyncedSnapshotJson = ConflictEngine.snapshotToJson(remoteWord)
                                    )
                                )
                                return true
                            }
                            is MergeOutcome.AutoMerged -> {
                                val merged = outcome.mergedWord
                                val updateMap = wordToFirestoreMap(merged, memberUids, isCreate = false)
                                wordRef.update(updateMap).await()
                                db.wordDao().upsertWord(
                                    merged.copy(
                                        syncStatus = "SYNCED",
                                        lastSyncedSnapshotJson = ConflictEngine.snapshotToJson(merged)
                                    )
                                )
                                return true
                            }
                            is MergeOutcome.ConflictDetected -> {
                                db.syncAndGovernanceDao().upsertConflict(outcome.conflictRecord)
                                db.wordDao().upsertWord(localWord.copy(syncStatus = "CONFLICT"))
                                pushConflictRecord(wsRef, outcome.conflictRecord, memberUids)
                                return false
                            }
                        }
                    } else {
                        val payload = wordToFirestoreMap(localWord, memberUids, isCreate = false)
                        wordRef.update(payload).await()
                        db.wordDao().upsertWord(
                            localWord.copy(
                                syncStatus = "SYNCED",
                                lastSyncedSnapshotJson = ConflictEngine.snapshotToJson(localWord)
                            )
                        )
                        return true
                    }
                }
            }
            "BOOK" -> {
                val bookRef = wsRef.collection("books").document(op.entityId)
                if (op.opType == SyncOpType.DELETE.name) {
                    bookRef.delete().await()
                    return true
                }
                val book = db.bookAndCurriculumDao().getBookById(op.entityId) ?: return true
                val remote = bookRef.get().await()
                val map = mutableMapOf<String, Any>(
                    "workspaceId" to ws.id,
                    "memberUids" to memberUids,
                    "title" to book.title,
                    "publisher" to book.publisher,
                    "editionOrYear" to book.editionOrYear,
                    "grade" to book.grade,
                    "notes" to book.notes,
                    "version" to book.version,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!remote.exists()) {
                    map["createdBy"] = book.createdBy
                    map["createdAt"] = FieldValue.serverTimestamp()
                    bookRef.set(map).await()
                } else {
                    bookRef.update(
                        map.filterKeys { it != "workspaceId" }
                    ).await()
                }
                db.bookAndCurriculumDao().upsertBook(book.copy(syncStatus = "SYNCED"))
                return true
            }
            "LOCATION" -> {
                val locRef = wsRef.collection("wordLocations").document(op.entityId)
                if (op.opType == SyncOpType.DELETE.name) {
                    locRef.delete().await()
                    return true
                }
                val loc = db.bookAndCurriculumDao().getLocationsSync(ws.id).firstOrNull { it.id == op.entityId }
                    ?: return true
                val remote = locRef.get().await()
                val map = mutableMapOf<String, Any>(
                    "workspaceId" to ws.id,
                    "memberUids" to memberUids,
                    "wordId" to loc.wordId,
                    "bookId" to loc.bookId,
                    "bookName" to loc.bookName,
                    "publisher" to loc.publisher,
                    "editionOrYear" to loc.editionOrYear,
                    "unit" to loc.unit,
                    "lesson" to loc.lesson,
                    "pageNumber" to loc.pageNumber,
                    "exerciseNumber" to loc.exerciseNumber,
                    "section" to loc.section,
                    "quotation" to loc.quotation,
                    "sourceType" to loc.sourceType,
                    "notes" to loc.notes,
                    "version" to loc.version,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!remote.exists()) {
                    map["createdBy"] = loc.createdBy
                    map["createdAt"] = FieldValue.serverTimestamp()
                    locRef.set(map).await()
                } else {
                    locRef.update(map.filterKeys { it != "workspaceId" && it != "wordId" }).await()
                }
                db.bookAndCurriculumDao().upsertLocation(loc.copy(syncStatus = "SYNCED"))
                return true
            }
            "CURRICULUM" -> {
                val curRef = wsRef.collection("curriculumEntries").document(op.entityId)
                if (op.opType == SyncOpType.DELETE.name) {
                    curRef.delete().await()
                    return true
                }
                val cur = db.bookAndCurriculumDao().getCurriculumSync(ws.id).firstOrNull { it.id == op.entityId }
                    ?: return true
                val remote = curRef.get().await()
                val map = mutableMapOf<String, Any>(
                    "workspaceId" to ws.id,
                    "memberUids" to memberUids,
                    "wordId" to cur.wordId,
                    "entryType" to cur.entryType,
                    "curriculumDistinction" to cur.curriculumDistinction,
                    "importantExamNotes" to cur.importantExamNotes,
                    "commonExamPatterns" to cur.commonExamPatterns,
                    "teacherNotes" to cur.teacherNotes,
                    "relatedGrammarRules" to cur.relatedGrammarRules,
                    "grade" to cur.grade,
                    "academicYear" to cur.academicYear,
                    "unit" to cur.unit,
                    "lesson" to cur.lesson,
                    "source" to cur.source,
                    "verificationStatus" to cur.verificationStatus,
                    "version" to cur.version,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!remote.exists()) {
                    map["createdBy"] = cur.createdBy
                    map["createdAt"] = FieldValue.serverTimestamp()
                    curRef.set(map).await()
                } else {
                    curRef.update(map.filterKeys { it != "workspaceId" && it != "wordId" }).await()
                }
                db.bookAndCurriculumDao().upsertCurriculum(cur.copy(syncStatus = "SYNCED"))
                return true
            }
            "REVIEW" -> {
                val revRef = wsRef.collection("reviewQueue").document(op.entityId)
                val item = db.syncAndGovernanceDao().getReviewItemById(op.entityId) ?: return true
                val remote = revRef.get().await()
                if (!remote.exists()) {
                    revRef.set(
                        mapOf(
                            "workspaceId" to ws.id,
                            "memberUids" to memberUids,
                            "targetWordId" to item.targetWordId,
                            "targetWordText" to item.targetWordText,
                            "sourceType" to item.sourceType,
                            "suggestionType" to item.suggestionType,
                            "proposedPayload" to item.proposedPayload,
                            "summaryArabic" to item.summaryArabic,
                            "aiGenerated" to item.aiGenerated,
                            "verified" to item.verified,
                            "status" to item.status,
                            "createdBy" to item.createdBy,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else if (item.status != "PENDING") {
                    revRef.update(
                        mapOf(
                            "status" to item.status,
                            "verified" to item.verified,
                            "reviewedBy" to (item.reviewedBy ?: uid),
                            "reviewNotes" to item.reviewNotes,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                db.syncAndGovernanceDao().upsertReviewItem(item.copy(syncStatus = "SYNCED"))
                return true
            }
            "MEMBER" -> {
                val memRef = wsRef.collection("members").document(op.entityId)
                if (op.opType == SyncOpType.DELETE.name) {
                    memRef.delete().await()
                    return true
                }
                val mem = db.workspaceDao().getMember(ws.id, op.entityId) ?: return true
                val remote = memRef.get().await()
                if (!remote.exists()) {
                    memRef.set(
                        mapOf(
                            "workspaceId" to ws.id,
                            "ownerId" to ws.ownerId,
                            "userId" to mem.userId,
                            "displayName" to mem.displayName,
                            "email" to mem.email,
                            "role" to mem.role,
                            "status" to mem.status,
                            "invitedBy" to mem.invitedBy,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    memRef.update(
                        mapOf(
                            "displayName" to mem.displayName,
                            "email" to mem.email,
                            "role" to mem.role,
                            "status" to mem.status,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                return true
            }
            "AUDIT" -> {
                val logObj = JSONObject(op.payloadJson)
                val logRef = wsRef.collection("auditLogs").document(op.entityId)
                if (!logRef.get().await().exists()) {
                    logRef.set(
                        mapOf(
                            "workspaceId" to ws.id,
                            "memberUids" to memberUids,
                            "actorUserId" to logObj.optString("actorUserId", uid),
                            "actorName" to logObj.optString("actorName", "User"),
                            "actorRole" to logObj.optString("actorRole", "OWNER"),
                            "action" to logObj.optString("action", "ACTION"),
                            "entityType" to logObj.optString("entityType", "WORD"),
                            "entityId" to logObj.optString("entityId", op.entityId),
                            "summary" to logObj.optString("summary", ""),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                return true
            }
            else -> return true
        }
    }

    private suspend fun pushConflictRecord(
        wsRef: com.google.firebase.firestore.DocumentReference,
        conflict: ConflictRecordEntity,
        memberUids: List<String>
    ) {
        runCatching {
            wsRef.collection("conflicts").document(conflict.id).set(
                mapOf(
                    "workspaceId" to conflict.workspaceId,
                    "memberUids" to memberUids,
                    "wordId" to conflict.wordId,
                    "humanId" to conflict.humanId,
                    "wordText" to conflict.wordText,
                    "localVersion" to conflict.localVersion,
                    "remoteVersion" to conflict.remoteVersion,
                    "conflictingFields" to conflict.conflictingFieldsCsv.split(",").filter { it.isNotBlank() },
                    "status" to conflict.status,
                    "createdBy" to conflict.createdBy,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    private suspend fun mergeRemoteWordsSnapshot(
        workspaceId: String,
        docs: List<DocumentSnapshot>,
        uid: String
    ) {
        for (doc in docs) {
            val remoteWord = docToWordEntity(doc, workspaceId) ?: continue
            val local = db.wordDao().getWordById(remoteWord.id)
            when {
                local == null -> {
                    db.wordDao().upsertWord(
                        remoteWord.copy(
                            syncStatus = "SYNCED",
                            lastSyncedSnapshotJson = ConflictEngine.snapshotToJson(remoteWord)
                        )
                    )
                }
                local.syncStatus == "SYNCED" && remoteWord.version > local.version -> {
                    db.wordDao().upsertWord(
                        remoteWord.copy(
                            syncStatus = "SYNCED",
                            lastSyncedSnapshotJson = ConflictEngine.snapshotToJson(remoteWord)
                        )
                    )
                }
                local.syncStatus == "PENDING" && remoteWord.version > local.version -> {
                    when (val outcome = ConflictEngine.evaluateThreeWayMerge(local, remoteWord, uid)) {
                        is MergeOutcome.ConflictDetected -> {
                            db.syncAndGovernanceDao().upsertConflict(outcome.conflictRecord)
                            db.wordDao().upsertWord(local.copy(syncStatus = "CONFLICT"))
                        }
                        is MergeOutcome.AutoMerged -> {
                            db.wordDao().upsertWord(outcome.mergedWord)
                        }
                        is MergeOutcome.NoChangesNeeded -> Unit
                    }
                }
            }
        }
    }

    private fun wordToFirestoreMap(
        word: WordEntity,
        memberUids: List<String>,
        isCreate: Boolean
    ): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "memberUids" to memberUids,
            "word" to word.word,
            "normalizedWord" to word.normalizedWord,
            "partOfSpeech" to word.partOfSpeech,
            "primaryMeaning" to word.primaryMeaning,
            "englishDefinition" to word.englishDefinition,
            "arabicExplanation" to word.arabicExplanation,
            "phoneticText" to word.phoneticText,
            "exampleSentences" to WordJsonCodec.decodeStringList(word.exampleSentencesJson),
            "meanings" to jsonArrayToList(word.meaningsJson),
            "synonyms" to jsonArrayToList(word.synonymsJson),
            "antonyms" to jsonArrayToList(word.antonymsJson),
            "derivatives" to jsonArrayToList(word.derivativesJson),
            "expressions" to jsonArrayToList(word.expressionsJson),
            "notes" to jsonArrayToList(word.notesJson),
            "version" to word.version,
            "updatedBy" to word.updatedBy,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (word.deletedAt != null) {
            map["deletedAt"] = word.deletedAt
        }
        if (isCreate) {
            map["workspaceId"] = word.workspaceId
            map["humanId"] = word.humanId
            map["createdBy"] = word.createdBy
            map["createdAt"] = FieldValue.serverTimestamp()
        }
        return map
    }

    private fun jsonArrayToList(json: String): List<Map<String, Any>> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
            val out = mutableMapOf<String, Any>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.opt(k)
                if (v != null && v != JSONObject.NULL) out[k] = v
            }
            out
        }
    }

    private fun docToWordEntity(doc: DocumentSnapshot, workspaceId: String): WordEntity? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val word = data["word"] as? String ?: return null
        val createdTs = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val updatedTs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return WordEntity(
            id = doc.id,
            humanId = data["humanId"] as? String ?: "VOC-000000",
            workspaceId = workspaceId,
            word = word,
            normalizedWord = data["normalizedWord"] as? String ?: word.lowercase(),
            partOfSpeech = data["partOfSpeech"] as? String ?: "OTHER",
            primaryMeaning = data["primaryMeaning"] as? String ?: "",
            englishDefinition = data["englishDefinition"] as? String ?: "",
            arabicExplanation = data["arabicExplanation"] as? String ?: "",
            phoneticText = data["phoneticText"] as? String ?: "",
            exampleSentencesJson = JSONArray((data["exampleSentences"] as? List<*>)?.filterIsInstance<String>().orEmpty()).toString(),
            meaningsJson = JSONArray((data["meanings"] as? List<*>).orEmpty()).toString(),
            synonymsJson = JSONArray((data["synonyms"] as? List<*>).orEmpty()).toString(),
            antonymsJson = JSONArray((data["antonyms"] as? List<*>).orEmpty()).toString(),
            derivativesJson = JSONArray((data["derivatives"] as? List<*>).orEmpty()).toString(),
            expressionsJson = JSONArray((data["expressions"] as? List<*>).orEmpty()).toString(),
            notesJson = JSONArray((data["notes"] as? List<*>).orEmpty()).toString(),
            version = (data["version"] as? Number)?.toLong() ?: 1L,
            createdBy = data["createdBy"] as? String ?: "",
            updatedBy = data["updatedBy"] as? String ?: "",
            createdAt = createdTs?.toDate()?.time ?: System.currentTimeMillis(),
            updatedAt = updatedTs?.toDate()?.time ?: System.currentTimeMillis(),
            deletedAt = (data["deletedAt"] as? Number)?.toLong(),
            syncStatus = "SYNCED"
        )
    }

    private fun docToBookEntity(doc: DocumentSnapshot, workspaceId: String): BookEntity? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val title = data["title"] as? String ?: return null
        val createdTs = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val updatedTs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return BookEntity(
            id = doc.id,
            workspaceId = workspaceId,
            title = title,
            publisher = data["publisher"] as? String ?: "",
            editionOrYear = data["editionOrYear"] as? String ?: "",
            grade = data["grade"] as? String ?: "",
            notes = data["notes"] as? String ?: "",
            version = (data["version"] as? Number)?.toLong() ?: 1L,
            createdBy = data["createdBy"] as? String ?: "",
            updatedBy = data["updatedBy"] as? String ?: "",
            createdAt = createdTs?.toDate()?.time ?: System.currentTimeMillis(),
            updatedAt = updatedTs?.toDate()?.time ?: System.currentTimeMillis(),
            syncStatus = "SYNCED"
        )
    }
}
