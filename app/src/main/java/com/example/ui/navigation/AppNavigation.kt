package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.ThemePreference
import com.example.tts.PronunciationManager
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.performSignOut
import com.example.ui.screens.AddEditWordScreen
import com.example.ui.screens.AuditLogsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BooksAndCurriculumScreen
import com.example.ui.screens.ConflictResolutionScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExportCenterScreen
import com.example.ui.screens.ImportCenterScreen
import com.example.ui.screens.ImportHistoryScreen
import com.example.ui.screens.ImportPreviewScreen
import com.example.ui.screens.ReviewQueueScreen
import com.example.ui.screens.SyncCenterScreen
import com.example.ui.screens.UserManagementScreen
import com.example.ui.screens.VocabularyLibraryScreen
import com.example.ui.screens.WordDetailScreen
import com.example.ui.screens.WorkspaceSettingsScreen
import com.example.ui.theme.MufradatiTheme
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopLevelDestination("dashboard", "الرئيسية", Icons.Default.Dashboard, "nav_dashboard"),
    TopLevelDestination("library", "المفردات", Icons.Default.LibraryBooks, "nav_library"),
    TopLevelDestination("books", "الكتب", Icons.Default.MenuBook, "nav_books"),
    TopLevelDestination("import_center", "استيراد", Icons.Default.UploadFile, "nav_import"),
    TopLevelDestination("settings", "الإعدادات", Icons.Default.Settings, "nav_settings")
)

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (currentUser == null) {
            MufradatiTheme(themePreference = ThemePreference.SYSTEM) {
                AuthScreen(onAuthSuccess = {})
            }
        } else {
            val user = currentUser!!
            key(user.uid) {
                AuthenticatedAppShell(
                    uid = user.uid,
                    displayName = user.displayName ?: "مستخدم Google",
                    email = user.email.orEmpty(),
                    credentialManager = credentialManager
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedAppShell(
    uid: String,
    displayName: String,
    email: String,
    credentialManager: CredentialManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: MainViewModel = viewModel(
        key = uid,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val (repo, prefs) = MainViewModel.createRepository(context)
                return MainViewModel(repo, prefs, uid, displayName, email) as T
            }
        }
    )

    val pronunciationManager = remember(context) { PronunciationManager(context) }
    DisposableEffect(pronunciationManager) {
        onDispose { pronunciationManager.shutdown() }
    }

    val userPrefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val activeWorkspace by viewModel.activeWorkspace.collectAsStateWithLifecycle()
    val role by viewModel.currentRole.collectAsStateWithLifecycle()
    val allWords by viewModel.allWords.collectAsStateWithLifecycle()
    val filteredWords by viewModel.filteredWords.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val allLocations by viewModel.allLocations.collectAsStateWithLifecycle()
    val allCurriculum by viewModel.allCurriculum.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selectedWordIds by viewModel.selectedWordIds.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsStateWithLifecycle()
    val lastSyncError by viewModel.lastSyncError.collectAsStateWithLifecycle()
    val syncOperations by viewModel.syncOperations.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val importJobs by viewModel.importJobs.collectAsStateWithLifecycle()
    val reviewQueue by viewModel.reviewQueue.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val dashboardStats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val aiBusy by viewModel.aiBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "dashboard"
    val showPrimaryNav = currentRoute in topDestinations.map { it.route }

    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo("dashboard") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    MufradatiTheme(themePreference = userPrefs.themePreference) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (showPrimaryNav && !useRail) {
                        NavigationBar {
                            topDestinations.forEach { dest ->
                                NavigationBarItem(
                                    selected = currentRoute == dest.route,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (showPrimaryNav && useRail) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            topDestinations.forEach { dest ->
                                NavigationRailItem(
                                    selected = currentRoute == dest.route,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = "dashboard"
                        ) {
                            composable("dashboard") {
                                DashboardScreen(
                                    workspace = activeWorkspace,
                                    role = role,
                                    stats = dashboardStats,
                                    syncState = syncState,
                                    lastSyncTimestamp = lastSyncTimestamp,
                                    words = allWords,
                                    onOpenWord = { wordId -> navController.navigate("word_detail/$wordId") },
                                    onAddWord = { navController.navigate("add_edit_word?wordId=") },
                                    onLoadStarterPack = { viewModel.loadStarterPack() },
                                    onNavigateRoute = { route ->
                                        if (route in topDestinations.map { it.route }) navigateTopLevel(route)
                                        else navController.navigate(route)
                                    }
                                )
                            }

                            composable("library") {
                                VocabularyLibraryScreen(
                                    words = filteredWords,
                                    books = books,
                                    locations = allLocations,
                                    filter = filter,
                                    selectedWordIds = selectedWordIds,
                                    role = role,
                                    allowViewerExport = activeWorkspace?.allowViewerExport ?: false,
                                    pronunciationManager = pronunciationManager,
                                    preferredAccent = userPrefs.preferredAccent,
                                    preferredVoiceName = userPrefs.preferredVoiceName,
                                    ttsSpeed = userPrefs.ttsSpeed,
                                    onUpdateFilter = viewModel::updateFilter,
                                    onToggleSelect = viewModel::toggleWordSelection,
                                    onSelectAll = viewModel::selectAllFiltered,
                                    onClearSelection = viewModel::clearSelection,
                                    onOpenWord = { wordId -> navController.navigate("word_detail/$wordId") },
                                    onAddWord = { navController.navigate("add_edit_word?wordId=") },
                                    onBulkDelete = viewModel::deleteWords,
                                    onBulkExport = { navController.navigate("export_center") }
                                )
                            }

                            composable(
                                route = "word_detail/{wordId}",
                                arguments = listOf(navArgument("wordId") { type = NavType.StringType })
                            ) { entry ->
                                val wordId = entry.arguments?.getString("wordId").orEmpty()
                                val word = allWords.firstOrNull { it.id == wordId }
                                WordDetailScreen(
                                    word = word,
                                    locations = allLocations.filter { it.wordId == wordId },
                                    curriculumEntries = allCurriculum.filter { it.wordId == wordId },
                                    books = books,
                                    role = role,
                                    userPrefs = userPrefs,
                                    aiBusy = aiBusy,
                                    pronunciationManager = pronunciationManager,
                                    onBack = { navController.popBackStack() },
                                    onEditWord = { id -> navController.navigate("add_edit_word?wordId=$id") },
                                    onDeleteWord = { id -> viewModel.deleteWords(setOf(id)) },
                                    onSaveLocation = viewModel::saveWordLocation,
                                    onSaveCurriculum = viewModel::saveCurriculumEntry,
                                    onRequestAiSuggestion = viewModel::requestAiSuggestion,
                                    onUpdateTtsSettings = viewModel::setTtsSettings
                                )
                            }

                            composable(
                                route = "add_edit_word?wordId={wordId}",
                                arguments = listOf(navArgument("wordId") { type = NavType.StringType; defaultValue = "" })
                            ) { entry ->
                                val wordId = entry.arguments?.getString("wordId").orEmpty()
                                val existingWord = if (wordId.isNotBlank()) allWords.firstOrNull { it.id == wordId } else null
                                AddEditWordScreen(
                                    existingWord = existingWord,
                                    allWords = allWords,
                                    onBack = { navController.popBackStack() },
                                    onSave = { draft ->
                                        viewModel.saveWord(draft) { saved ->
                                            navController.popBackStack()
                                            if (existingWord == null) {
                                                navController.navigate("word_detail/${saved.id}")
                                            }
                                        }
                                    }
                                )
                            }

                            composable("import_center") {
                                ImportCenterScreen(
                                    role = role,
                                    allowEditorImport = activeWorkspace?.allowEditorImport ?: false,
                                    importState = importState,
                                    onPickFile = viewModel::parseImportedUri,
                                    onParsePasted = viewModel::parsePastedImport,
                                    onUpdateMapping = viewModel::updateImportMapping,
                                    onOpenPreview = { navController.navigate("import_preview") },
                                    onOpenHistory = { navController.navigate("import_history") },
                                    onClearImport = viewModel::clearImportWizard,
                                    onBack = { navigateTopLevel("dashboard") }
                                )
                            }

                            composable("import_preview") {
                                ImportPreviewScreen(
                                    importState = importState,
                                    onBulkDuplicateStrategy = viewModel::applyBulkDuplicateStrategy,
                                    onRowDuplicateStrategy = viewModel::updateRowDuplicateStrategy,
                                    onConfirmImport = viewModel::confirmAndExecuteImport,
                                    onOpenHistory = { navController.navigate("import_history") },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("import_history") {
                                ImportHistoryScreen(
                                    jobs = importJobs,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("books") {
                                BooksAndCurriculumScreen(
                                    books = books,
                                    locations = allLocations,
                                    curriculumEntries = allCurriculum,
                                    words = allWords,
                                    role = role,
                                    onSaveBook = viewModel::saveBook,
                                    onOpenWord = { wordId -> navController.navigate("word_detail/$wordId") }
                                )
                            }

                            composable("review_queue") {
                                ReviewQueueScreen(
                                    items = reviewQueue,
                                    role = role,
                                    onDecide = viewModel::decideReviewItem,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("conflicts") {
                                ConflictResolutionScreen(
                                    conflicts = conflicts,
                                    words = allWords,
                                    role = role,
                                    onResolveConflict = viewModel::resolveConflict,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("users") {
                                UserManagementScreen(
                                    members = members,
                                    currentUserId = uid,
                                    role = role,
                                    onSaveMember = viewModel::addOrUpdateMember,
                                    onRemoveMember = viewModel::removeMember,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("audit_logs") {
                                AuditLogsScreen(
                                    logs = auditLogs,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("export_center") {
                                ExportCenterScreen(
                                    role = role,
                                    allowViewerExport = activeWorkspace?.allowViewerExport ?: false,
                                    books = books,
                                    selectedCount = selectedWordIds.size,
                                    filteredCount = filteredWords.size,
                                    totalCount = allWords.size,
                                    onExportToUri = viewModel::exportToUri,
                                    onExportForShare = viewModel::exportToTempFileForShare,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("sync_center") {
                                SyncCenterScreen(
                                    syncState = syncState,
                                    lastSyncTimestamp = lastSyncTimestamp,
                                    lastSyncError = lastSyncError,
                                    operations = syncOperations,
                                    openConflictsCount = conflicts.count { it.status == "OPEN" },
                                    role = role,
                                    onSyncNow = viewModel::syncNow,
                                    onSimulateConflict = viewModel::simulateConflictOnFirstWord,
                                    onOpenConflicts = { navController.navigate("conflicts") },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable("settings") {
                                WorkspaceSettingsScreen(
                                    workspace = activeWorkspace,
                                    role = role,
                                    userEmail = email,
                                    userPrefs = userPrefs,
                                    onSaveWorkspaceSettings = viewModel::updateWorkspaceSettings,
                                    onThemeChanged = viewModel::setThemePreference,
                                    onAiToggleChanged = viewModel::setAiEnabled,
                                    onNavigateRoute = { route -> navController.navigate(route) },
                                    onSignOut = {
                                        performSignOut(
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onBeforeAuthSignOut = {
                                                viewModel.stopListenersBeforeSignOut()
                                            }
                                        )
                                    },
                                    onBack = { navigateTopLevel("dashboard") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
