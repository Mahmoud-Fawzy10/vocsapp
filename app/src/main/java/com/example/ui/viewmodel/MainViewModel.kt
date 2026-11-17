package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.VocabAiService
import com.example.data.importexport.ExportGenerator
import com.example.data.importexport.ImportParser
import com.example.data.importexport.ParsedTabularData
import com.example.data.local.AppDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.BookEntity
import com.example.data.local.ConflictRecordEntity
import com.example.data.local.CurriculumEntryEntity
import com.example.data.local.ImportJobEntity
import com.example.data.local.ReviewQueueItemEntity
import com.example.data.local.SyncOperationEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.WordEntity
import com.example.data.local.WordLocationEntity
import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceMemberEntity
import com.example.data.repo.VocabularyRepository
import com.example.data.sync.SyncEngine
import com.example.domain.WordJsonCodec
import com.example.model.ConflictChoice
import com.example.model.CurriculumEntryDraft
import com.example.model.DashboardStats
import com.example.model.DuplicateStrategy
import com.example.model.ExportFormat
import com.example.model.ExportOptions
import com.example.model.ExportScope
import com.example.model.ImportColumnMapping
import com.example.model.ImportPreviewRow
import com.example.model.ImportReport
import com.example.model.MemberStatus
import com.example.model.PartOfSpeech
import com.example.model.SyncState
import com.example.model.ThemePreference
import com.example.model.UserPreferences
import com.example.model.VocabularyFilter
import com.example.model.WordDraft
import com.example.model.WordLocationDraft
import com.example.model.WordSortOrder
import com.example.model.WorkspaceRole
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class ImportWizardState(
    val fileName: String = "",
    val parsedData: ParsedTabularData? = null,
    val mapping: ImportColumnMapping = ImportColumnMapping(),
    val previewRows: List<ImportPreviewRow> = emptyList(),
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val lastReport: ImportReport? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: VocabularyRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val currentUserId: String,
    private val currentDisplayName: String,
    private val currentEmail: String
) : ViewModel() {

    private val _activeWorkspaceId = MutableStateFlow("")
    val activeWorkspaceId: StateFlow<String> = _activeWorkspaceId.asStateFlow()

    val userPreferences: StateFlow<UserPreferences> = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val workspaces: StateFlow<List<WorkspaceEntity>> = repository.observeWorkspaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeWorkspace: StateFlow<WorkspaceEntity?> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(null) else repository.observeWorkspace(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val members: StateFlow<List<WorkspaceMemberEntity>> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(emptyList()) else repository.observeMembers(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val currentRole: StateFlow<WorkspaceRole> = combine(members, activeWorkspace) { memList, ws ->
        val me = memList.firstOrNull { it.userId == currentUserId }
        if (me != null) WorkspaceRole.safeValueOf(me.role)
        else if (ws?.ownerId == currentUserId) WorkspaceRole.OWNER
        else WorkspaceRole.VIEWER
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), WorkspaceRole.VIEWER)

    val allWords: StateFlow<List<WordEntity>> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(emptyList()) else repository.observeWords(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val books: StateFlow<List<BookEntity>> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(emptyList()) else repository.observeBooks(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allLocations: StateFlow<List<WordLocationEntity>> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(emptyList()) else repository.observeAllLocations(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allCurriculum: StateFlow<List<CurriculumEntryEntity>> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(emptyList()) else repository.observeAllCurriculum(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val syncOperations: StateFlow<List<SyncOperationEntity>> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(emptyList()) else repository.observeSyncOperations(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val conflicts: StateFlow<List<ConflictRecordEntity>> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(emptyList()) else repository.observeConflicts(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val importJobs: StateFlow<List<ImportJobEntity>> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(emptyList()) else repository.observeImportJobs(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val reviewQueue: StateFlow<List<ReviewQueueItemEntity>> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(emptyList()) else repository.observeReviewQueue(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = _activeWorkspaceId
        .flatMapLatest { wsId -> if (wsId.isBlank()) flowOf(emptyList()) else repository.observeAuditLogs(wsId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _filter = MutableStateFlow(VocabularyFilter())
    val filter: StateFlow<VocabularyFilter> = _filter.asStateFlow()

    val filteredWords: StateFlow<List<WordEntity>> = combine(
        allWords,
        allLocations,
        allCurriculum,
        _filter
    ) { words, locations, curriculum, f ->
        val q = f.query.trim().lowercase(Locale.ROOT)
        val locsByWord = locations.groupBy { it.wordId }
        val curByWord = curriculum.groupBy { it.wordId }

        val matched = words.filter { word ->
            val wordLocs = locsByWord[word.id].orEmpty()
            val wordCur = curByWord[word.id].orEmpty()

            val queryMatch = if (q.isBlank()) true else {
                word.word.lowercase(Locale.ROOT).contains(q) ||
                    word.humanId.lowercase(Locale.ROOT).contains(q) ||
                    word.primaryMeaning.lowercase(Locale.ROOT).contains(q) ||
                    word.arabicExplanation.lowercase(Locale.ROOT).contains(q) ||
                    word.englishDefinition.lowercase(Locale.ROOT).contains(q) ||
                    WordJsonCodec.decodeSynonyms(word.synonymsJson).any { it.word.lowercase(Locale.ROOT).contains(q) } ||
                    WordJsonCodec.decodeDerivatives(word.derivativesJson).any { it.word.lowercase(Locale.ROOT).contains(q) } ||
                    wordLocs.any { it.bookName.lowercase(Locale.ROOT).contains(q) || it.unit.lowercase(Locale.ROOT).contains(q) }
            }

            val posMatch = f.partOfSpeech == null || word.partOfSpeech == f.partOfSpeech.name
            val bookMatch = f.bookId.isNullOrBlank() || wordLocs.any { it.bookId == f.bookId }
            val unitMatch = f.unit.isNullOrBlank() || wordLocs.any { it.unit.contains(f.unit, ignoreCase = true) } ||
                wordCur.any { it.unit.contains(f.unit, ignoreCase = true) }
            val lessonMatch = f.lesson.isNullOrBlank() || wordLocs.any { it.lesson.contains(f.lesson, ignoreCase = true) } ||
                wordCur.any { it.lesson.contains(f.lesson, ignoreCase = true) }
            val pageMatch = f.page.isNullOrBlank() || wordLocs.any { it.pageNumber.equals(f.page.trim(), ignoreCase = true) }
            val distinctionMatch = !f.curriculumDistinctionOnly || wordCur.any { it.curriculumDistinction.isNotBlank() }

            queryMatch && posMatch && bookMatch && unitMatch && lessonMatch && pageMatch && distinctionMatch
        }

        when (f.sortOrder) {
            WordSortOrder.ALPHABETICAL_ASC -> matched.sortedBy { it.normalizedWord }
            WordSortOrder.ALPHABETICAL_DESC -> matched.sortedByDescending { it.normalizedWord }
            WordSortOrder.UPDATED_DESC -> matched.sortedByDescending { it.updatedAt }
            WordSortOrder.CREATED_DESC -> matched.sortedByDescending { it.createdAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val syncState: StateFlow<SyncState> = combine(
        repository.syncEngine.isOnline,
        repository.syncEngine.isSyncing,
        repository.syncEngine.lastSyncError,
        syncOperations,
        conflicts
    ) { _, _, _, ops, confs ->
        val pendingCount = ops.count { it.status != "SYNCED" }
        val openConflicts = confs.count { it.status == "OPEN" }
        repository.syncEngine.computeSyncState(pendingCount, openConflicts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SyncState.OFFLINE)

    val lastSyncTimestamp: StateFlow<Long> = combine(
        repository.syncEngine.lastSuccessTimestamp,
        userPreferences
    ) { liveTs, prefs -> maxOf(liveTs, prefs.lastSyncTimestamp) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val lastSyncError: StateFlow<String?> = repository.syncEngine.lastSyncError

    val dashboardStats: StateFlow<DashboardStats> = combine(
        allWords,
        books,
        syncOperations,
        conflicts,
        reviewQueue
    ) { words, bookList, ops, confs, reviews ->
        val totalMeanings = words.sumOf { 1 + WordJsonCodec.decodeMeanings(it.meaningsJson).size }
        DashboardStats(
            totalWords = words.size,
            totalMeanings = totalMeanings,
            totalBooks = bookList.size,
            pendingSyncCount = ops.count { it.status != "SYNCED" },
            openConflictsCount = confs.count { it.status == "OPEN" },
            pendingReviewCount = reviews.count { it.status == "PENDING" },
            recentImportCount = 0
        )
    }.combine(importJobs) { stats, jobs ->
        stats.copy(recentImportCount = jobs.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardStats())

    private val _selectedWordIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedWordIds: StateFlow<Set<String>> = _selectedWordIds.asStateFlow()

    private val _importState = MutableStateFlow(ImportWizardState())
    val importState: StateFlow<ImportWizardState> = _importState.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val prefs = prefsRepo.preferences.first()
            val ws = repository.ensureWorkspaceForUser(
                uid = currentUserId,
                displayName = currentDisplayName,
                email = currentEmail,
                preferredWorkspaceId = prefs.activeWorkspaceId
            )
            _activeWorkspaceId.value = ws.id
            prefsRepo.setActiveWorkspaceId(ws.id)
            repository.syncEngine.startRealtimePull(ws.id, currentUserId)
            repository.syncEngine.synchronizeWorkspace(ws.id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.syncEngine.stopRealtimePull()
    }

    fun stopListenersBeforeSignOut() {
        repository.syncEngine.stopRealtimePull()
    }

    fun updateFilter(transform: (VocabularyFilter) -> VocabularyFilter) {
        _filter.value = transform(_filter.value)
    }

    fun toggleWordSelection(wordId: String) {
        val current = _selectedWordIds.value
        _selectedWordIds.value = if (wordId in current) current - wordId else current + wordId
    }

    fun clearSelection() {
        _selectedWordIds.value = emptySet()
    }

    fun selectAllFiltered() {
        _selectedWordIds.value = filteredWords.value.map { it.id }.toSet()
    }

    fun saveWord(draft: WordDraft, onSaved: (WordEntity) -> Unit = {}) {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.saveWord(wsId, draft)
                .onSuccess {
                    _messages.tryEmit("تم حفظ المفردة ${it.humanId} بنجاح")
                    onSaved(it)
                }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر حفظ المفردة") }
        }
    }

    fun deleteWords(wordIds: Set<String>) {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank() || wordIds.isEmpty()) return
        viewModelScope.launch {
            repository.deleteWords(wsId, wordIds)
                .onSuccess { count ->
                    _selectedWordIds.value = _selectedWordIds.value - wordIds
                    _messages.tryEmit("تم حذف $count مفردة")
                }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر الحذف") }
        }
    }

    fun saveBook(
        existingId: String?,
        title: String,
        publisher: String,
        editionOrYear: String,
        grade: String,
        notes: String
    ) {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.saveBook(wsId, existingId, title, publisher, editionOrYear, grade, notes)
                .onSuccess { _messages.tryEmit("تم حفظ الكتاب: ${it.title}") }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر حفظ الكتاب") }
        }
    }

    fun saveWordLocation(draft: WordLocationDraft) {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.saveWordLocation(wsId, draft)
                .onSuccess { _messages.tryEmit("تم حفظ مرجع الكتاب والصفحة") }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر حفظ مرجع الكتاب") }
        }
    }

    fun saveCurriculumEntry(draft: CurriculumEntryDraft) {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.saveCurriculumEntry(wsId, draft)
                .onSuccess { _messages.tryEmit("تم حفظ المعلومة التعليمية") }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر حفظ المعلومة") }
        }
    }

    fun requestAiSuggestion(word: WordEntity, focusArea: String, compareWithWord: String = "") {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            _aiBusy.value = true
            try {
                val aiEnabled = userPreferences.value.aiEnabled
                repository.requestAiSuggestionToReviewQueue(wsId, word, focusArea, compareWithWord, aiEnabled)
                    .onSuccess {
                        _messages.tryEmit("تم إرسال اقتراح الذكاء الاصطناعي إلى قائمة المراجعة (غير منشور تلقائياً)")
                    }
                    .onFailure { _messages.tryEmit(it.message ?: "تعذر توليد الاقتراح") }
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun decideReviewItem(itemId: String, approve: Boolean, notes: String) {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.decideReviewItem(wsId, itemId, approve, notes)
                .onSuccess {
                    _messages.tryEmit(if (approve) "تم اعتماد المقترح وتحديث المفردة" else "تم رفض المقترح")
                }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر تنفيذ المراجعة") }
        }
    }

    fun loadStarterPack() {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.loadEducationalStarterPack(wsId)
                .onSuccess { _messages.tryEmit("تمت إضافة $it مفردات نموذجية تعليمية") }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر تحميل العينة") }
        }
    }

    // --- Import Wizard ---
    fun parseImportedUri(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val lower = displayName.lowercase(Locale.ROOT)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        when {
                            lower.endsWith(".xlsx") -> ImportParser.parseXlsx(stream)
                            lower.endsWith(".json") -> ImportParser.parseJson(stream.bufferedReader().readText())
                            lower.endsWith(".csv") || lower.endsWith(".tsv") -> {
                                val text = stream.bufferedReader().readText()
                                if (lower.endsWith(".tsv")) ImportParser.parsePastedText(text) else ImportParser.parseCsv(text)
                            }
                            else -> throw IllegalArgumentException("صيغة الملف غير مدعومة للاستيراد الموثوق. استخدم CSV أو XLSX أو JSON.")
                        }
                    } ?: throw IllegalArgumentException("تعذر فتح الملف المحدد.")
                }
                applyParsedImport(displayName, parsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "فشل قراءة الملف")
            }
        }
    }

    fun parsePastedImport(text: String) {
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) {
                    val trimmed = text.trim()
                    if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                        ImportParser.parseJson(trimmed)
                    } else {
                        ImportParser.parsePastedText(trimmed)
                    }
                }
                applyParsedImport("نص_ملصوق.${parsed.sourceFormat.lowercase()}", parsed)
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "تعذر تحليل النص الملصوق")
            }
        }
    }

    private fun applyParsedImport(fileName: String, parsed: ParsedTabularData) {
        val mapping = ImportParser.detectColumnMapping(parsed.headers)
        val preview = ImportParser.buildPreviewRows(parsed, mapping, allWords.value)
        _importState.value = ImportWizardState(
            fileName = fileName,
            parsedData = parsed,
            mapping = mapping,
            previewRows = preview,
            lastReport = null
        )
    }

    fun updateImportMapping(newMapping: ImportColumnMapping) {
        val parsed = _importState.value.parsedData ?: return
        val preview = ImportParser.buildPreviewRows(parsed, newMapping, allWords.value)
        _importState.value = _importState.value.copy(mapping = newMapping, previewRows = preview)
    }

    fun updateRowDuplicateStrategy(rowIndex: Int, strategy: DuplicateStrategy) {
        val updated = _importState.value.previewRows.map { row ->
            if (row.rowIndex == rowIndex) row.copy(strategy = strategy) else row
        }
        _importState.value = _importState.value.copy(previewRows = updated)
    }

    fun applyBulkDuplicateStrategy(strategy: DuplicateStrategy) {
        val updated = _importState.value.previewRows.map { row ->
            if (row.matchedReason != null) row.copy(strategy = strategy) else row
        }
        _importState.value = _importState.value.copy(previewRows = updated)
    }

    fun confirmAndExecuteImport() {
        val wsId = _activeWorkspaceId.value
        val state = _importState.value
        val parsed = state.parsedData ?: return
        if (wsId.isBlank() || state.previewRows.isEmpty()) return

        viewModelScope.launch {
            _importState.value = state.copy(isImporting = true, progress = 0f)
            repository.commitImportBatch(
                workspaceId = wsId,
                fileName = state.fileName,
                sourceFormat = parsed.sourceFormat,
                rows = state.previewRows,
                onProgress = { p -> _importState.value = _importState.value.copy(progress = p) }
            ).onSuccess { report ->
                _importState.value = _importState.value.copy(isImporting = false, progress = 1f, lastReport = report)
                _messages.tryEmit("اكتمل الاستيراد: تمت إضافة ${report.createdCount} مفردة")
            }.onFailure { err ->
                _importState.value = _importState.value.copy(isImporting = false)
                _messages.tryEmit(err.message ?: "فشل الاستيراد")
            }
        }
    }

    fun clearImportWizard() {
        _importState.value = ImportWizardState()
    }

    // --- Export ---
    fun resolveWordsForExport(options: ExportOptions): List<WordEntity> {
        val words = allWords.value
        val locsByWord = allLocations.value.groupBy { it.wordId }
        return when (options.scope) {
            ExportScope.ALL -> words
            ExportScope.SELECTED -> words.filter { it.id in _selectedWordIds.value }
            ExportScope.FILTERED -> filteredWords.value
            ExportScope.BY_BOOK -> words.filter { w ->
                locsByWord[w.id].orEmpty().any { it.bookId == options.filterBookId }
            }
            ExportScope.BY_UNIT -> words.filter { w ->
                locsByWord[w.id].orEmpty().any { it.unit.equals(options.filterUnit.orEmpty(), ignoreCase = true) }
            }
            ExportScope.BY_LESSON -> words.filter { w ->
                locsByWord[w.id].orEmpty().any { it.lesson.equals(options.filterLesson.orEmpty(), ignoreCase = true) }
            }
            ExportScope.BY_PART_OF_SPEECH -> words.filter { w ->
                options.filterPartOfSpeech == null || w.partOfSpeech == options.filterPartOfSpeech.name
            }
        }
    }

    fun exportToUri(context: Context, uri: Uri, options: ExportOptions) {
        val ws = activeWorkspace.value ?: return
        val role = currentRole.value
        if (!role.canExport(ws.allowViewerExport)) {
            _messages.tryEmit("غير مصرح: التصدير غير مفعّل للمشاهدين.")
            return
        }
        val wordsToExport = resolveWordsForExport(options)
        if (wordsToExport.isEmpty()) {
            _messages.tryEmit("لا توجد مفردات مطابقة لنطاق التصدير المحدد.")
            return
        }

        viewModelScope.launch {
            try {
                val locsMap = allLocations.value.groupBy { it.wordId }
                val curMap = allCurriculum.value.groupBy { it.wordId }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        when (options.format) {
                            ExportFormat.CSV -> ExportGenerator.writeCsv(out, wordsToExport, locsMap, curMap, options)
                            ExportFormat.JSON -> ExportGenerator.writeJson(out, wordsToExport, locsMap, curMap)
                            ExportFormat.XLSX -> ExportGenerator.writeXlsx(out, wordsToExport, locsMap, curMap, options)
                            ExportFormat.PDF -> ExportGenerator.writePdf(out, ws.name, wordsToExport, locsMap, curMap, options)
                        }
                    } ?: throw IllegalStateException("تعذر فتح مسار الحفظ.")
                }
                repository.logExportAudit(ws.id, "تصدير ${wordsToExport.size} مفردة بصيغة ${options.format.name}")
                _messages.tryEmit("تم تصدير ${wordsToExport.size} مفردة بنجاح (${options.format.arabicLabel})")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "فشل التصدير")
            }
        }
    }

    fun exportToTempFileForShare(context: Context, options: ExportOptions, onReady: (File) -> Unit) {
        val ws = activeWorkspace.value ?: return
        if (!currentRole.value.canExport(ws.allowViewerExport)) {
            _messages.tryEmit("غير مصرح: التصدير غير مفعّل للمشاهدين.")
            return
        }
        val wordsToExport = resolveWordsForExport(options)
        if (wordsToExport.isEmpty()) {
            _messages.tryEmit("لا توجد مفردات مطابقة لنطاق التصدير.")
            return
        }
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val outFile = File(dir, "mufradati_export_${System.currentTimeMillis()}.${options.format.extension}")
                    val locsMap = allLocations.value.groupBy { it.wordId }
                    val curMap = allCurriculum.value.groupBy { it.wordId }
                    outFile.outputStream().use { out ->
                        when (options.format) {
                            ExportFormat.CSV -> ExportGenerator.writeCsv(out, wordsToExport, locsMap, curMap, options)
                            ExportFormat.JSON -> ExportGenerator.writeJson(out, wordsToExport, locsMap, curMap)
                            ExportFormat.XLSX -> ExportGenerator.writeXlsx(out, wordsToExport, locsMap, curMap, options)
                            ExportFormat.PDF -> ExportGenerator.writePdf(out, ws.name, wordsToExport, locsMap, curMap, options)
                        }
                    }
                    outFile
                }
                repository.logExportAudit(ws.id, "مشاركة تصدير ${wordsToExport.size} مفردة (${options.format.name})")
                onReady(file)
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "تعذر تجهيز ملف المشاركة")
            }
        }
    }

    // --- Sync, Conflicts, Users, Settings ---
    fun syncNow() {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.syncEngine.synchronizeWorkspace(wsId)
                .onSuccess { synced ->
                    prefsRepo.setLastSyncTimestamp(System.currentTimeMillis())
                    _messages.tryEmit(if (synced > 0) "تمت مزامنة $synced عملية بنجاح" else "جميع البيانات محدثة")
                }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر المزامنة") }
        }
    }

    fun simulateConflictOnFirstWord() {
        val wsId = _activeWorkspaceId.value
        val firstWord = allWords.value.firstOrNull()
        if (wsId.isBlank() || firstWord == null) {
            _messages.tryEmit("أضف مفردة أولاً لاختبار كشف التعارضات.")
            return
        }
        viewModelScope.launch {
            repository.simulateConflictForTesting(wsId, firstWord.id)
                .onSuccess { _messages.tryEmit("تم إنشاء حالة تعارض تجريبية للمفردة ${firstWord.word}") }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر المحاكاة") }
        }
    }

    fun resolveConflict(conflictId: String, choices: Map<String, ConflictChoice>) {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.resolveConflictRecord(wsId, conflictId, choices)
                .onSuccess { _messages.tryEmit("تم حل التعارض وحفظ النسخة المعتمدة") }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر حل التعارض") }
        }
    }

    fun addOrUpdateMember(targetUid: String, name: String, email: String, role: WorkspaceRole, status: MemberStatus) {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.addOrUpdateMember(wsId, targetUid, name, email, role, status)
                .onSuccess { _messages.tryEmit("تم تحديث بيانات العضو وصلاحياته") }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر تحديث العضوية") }
        }
    }

    fun removeMember(targetUid: String) {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.removeMember(wsId, targetUid)
                .onSuccess { _messages.tryEmit("تمت إزالة العضو") }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر إزالة العضو") }
        }
    }

    fun updateWorkspaceSettings(name: String, desc: String, allowViewerExport: Boolean, allowEditorImport: Boolean) {
        val wsId = _activeWorkspaceId.value
        if (wsId.isBlank()) return
        viewModelScope.launch {
            repository.updateWorkspaceSettings(wsId, name, desc, allowViewerExport, allowEditorImport)
                .onSuccess { _messages.tryEmit("تم حفظ إعدادات مساحة العمل") }
                .onFailure { _messages.tryEmit(it.message ?: "تعذر حفظ الإعدادات") }
        }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { prefsRepo.setThemePreference(theme) }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAiEnabled(enabled) }
    }

    fun setTtsSettings(speed: Float, accent: String, voiceName: String) {
        viewModelScope.launch { prefsRepo.setTtsSettings(speed, accent, voiceName) }
    }

    companion object {
        fun createRepository(context: Context): Pair<VocabularyRepository, UserPreferencesRepository> {
            val appCtx = context.applicationContext
            val db = AppDatabase.getInstance(appCtx)
            val auth = FirebaseAuth.getInstance()
            val syncEngine = SyncEngine(appCtx, db, auth)
            val aiService = VocabAiService()
            val repo = VocabularyRepository(appCtx, db, syncEngine, aiService, auth)
            val prefs = UserPreferencesRepository(appCtx)
            return repo to prefs
        }
    }
}
