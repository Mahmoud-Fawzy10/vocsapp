package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.local.BookEntity
import com.example.data.local.SyncOperationEntity
import com.example.data.local.WorkspaceEntity
import com.example.model.ExportFormat
import com.example.model.ExportOptions
import com.example.model.ExportScope
import com.example.model.PartOfSpeech
import com.example.model.SyncState
import com.example.model.ThemePreference
import com.example.model.UserPreferences
import com.example.model.WorkspaceRole
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LtrText
import com.example.ui.components.SyncEntityChip
import com.example.ui.components.SyncStatusBanner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportCenterScreen(
    role: WorkspaceRole,
    allowViewerExport: Boolean,
    books: List<BookEntity>,
    selectedCount: Int,
    filteredCount: Int,
    totalCount: Int,
    onExportToUri: (android.content.Context, android.net.Uri, ExportOptions) -> Unit,
    onExportForShare: (android.content.Context, ExportOptions, (File) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canExport = role.canExport(allowViewerExport)
    var options by remember { mutableStateOf(ExportOptions()) }
    var unitFilter by remember { mutableStateOf("") }
    var lessonFilter by remember { mutableStateOf("") }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(options.format.mimeType)
    ) { uri ->
        if (uri != null) {
            onExportToUri(
                context,
                uri,
                options.copy(
                    filterUnit = unitFilter.ifBlank { null },
                    filterLesson = lessonFilter.ifBlank { null }
                )
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("مركز التصدير والتقارير") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!canExport) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            "التصدير غير مفعّل لدور المشاهد حالياً في إعدادات مساحة العمل.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. اختر صيغة التصدير", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExportFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = options.format == fmt,
                                    onClick = { options = options.copy(format = fmt) },
                                    label = { Text(fmt.arabicLabel) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("2. نطاق المفردات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExportScope.entries.forEach { sc ->
                                val extra = when (sc) {
                                    ExportScope.ALL -> " ($totalCount)"
                                    ExportScope.SELECTED -> " ($selectedCount)"
                                    ExportScope.FILTERED -> " ($filteredCount)"
                                    else -> ""
                                }
                                FilterChip(
                                    selected = options.scope == sc,
                                    onClick = { options = options.copy(scope = sc) },
                                    label = { Text("${sc.arabicLabel}$extra") }
                                )
                            }
                        }

                        if (options.scope == ExportScope.BY_BOOK && books.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                books.forEach { bk ->
                                    FilterChip(
                                        selected = options.filterBookId == bk.id,
                                        onClick = { options = options.copy(filterBookId = bk.id) },
                                        label = { Text(bk.title) }
                                    )
                                }
                            }
                        }
                        if (options.scope == ExportScope.BY_UNIT) {
                            OutlinedTextField(value = unitFilter, onValueChange = { unitFilter = it }, label = { Text("اسم الوحدة (مثال: Unit 1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        if (options.scope == ExportScope.BY_LESSON) {
                            OutlinedTextField(value = lessonFilter, onValueChange = { lessonFilter = it }, label = { Text("اسم الدرس") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        if (options.scope == ExportScope.BY_PART_OF_SPEECH) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PartOfSpeech.entries.take(7).forEach { pos ->
                                    FilterChip(
                                        selected = options.filterPartOfSpeech == pos,
                                        onClick = { options = options.copy(filterPartOfSpeech = pos) },
                                        label = { Text(pos.arabicLabel) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("3. الأقسام المضمّنة في الملف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OptionCheckbox("المعاني الإضافية", options.includeMeanings) { options = options.copy(includeMeanings = it) }
                        OptionCheckbox("المرادفات والمتضادات", options.includeSynonymsAntonyms) { options = options.copy(includeSynonymsAntonyms = it) }
                        OptionCheckbox("المشتقات", options.includeDerivatives) { options = options.copy(includeDerivatives = it) }
                        OptionCheckbox("التعبيرات والمتلازمات", options.includeExpressions) { options = options.copy(includeExpressions = it) }
                        OptionCheckbox("بيانات المنهج والملاحظات التعليمية", options.includeCurriculum) { options = options.copy(includeCurriculum = it) }
                        OptionCheckbox("مراجع الكتب والصفحات", options.includeBookLocations) { options = options.copy(includeBookLocations = it) }
                        OptionCheckbox("الملاحظات الشخصية والخاصة", options.includeNotes) { options = options.copy(includeNotes = it) }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            createDocumentLauncher.launch("mufradati_export.${options.format.extension}")
                        },
                        enabled = canExport,
                        modifier = Modifier.weight(1f).testTag("export_save_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("حفظ في الجهاز")
                    }

                    OutlinedButton(
                        onClick = {
                            val resolvedOptions = options.copy(
                                filterUnit = unitFilter.ifBlank { null },
                                filterLesson = lessonFilter.ifBlank { null }
                            )
                            onExportForShare(context, resolvedOptions) { file ->
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = resolvedOptions.format.mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة ملف المفردات"))
                            }
                        },
                        enabled = canExport,
                        modifier = Modifier.weight(1f).testTag("export_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("مشاركة")
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncCenterScreen(
    syncState: SyncState,
    lastSyncTimestamp: Long,
    lastSyncError: String?,
    operations: List<SyncOperationEntity>,
    openConflictsCount: Int,
    role: WorkspaceRole,
    onSyncNow: () -> Unit,
    onSimulateConflict: () -> Unit,
    onOpenConflicts: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingOps = operations.filter { it.status != "SYNCED" }
    val fmt = remember { SimpleDateFormat("HH:mm:ss • dd/MM", Locale("ar")) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("مركز المزامنة والطابور المحلي") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SyncStatusBanner(
                    syncState = syncState,
                    lastSyncTimestamp = lastSyncTimestamp,
                    pendingCount = pendingOps.size,
                    onClick = onSyncNow
                )
            }

            if (!lastSyncError.isNullOrBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = "تفاصيل آخر خطأ مزامنة: $lastSyncError",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onSyncNow,
                        modifier = Modifier.weight(1f).testTag("sync_now_button")
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("مزامنة الآن")
                    }
                    if (role.canEditVocab()) {
                        OutlinedButton(
                            onClick = onSimulateConflict,
                            modifier = Modifier.weight(1f).testTag("simulate_conflict_button")
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("اختبار تعارض", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (openConflictsCount > 0) {
                item {
                    Button(
                        onClick = onOpenConflicts,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("مراجعة وحل $openConflictsCount تعارض مفتوح")
                    }
                }
            }

            item {
                Text("سجل طابور المزامنة (${operations.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (operations.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "طابور المزامنة فارغ",
                        subtitle = "جميع التعديلات المحلية متزامنة بأمان مع السحابة."
                    )
                }
            } else {
                items(operations.take(40), key = { it.operationId }) { op ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${op.entityType} • ${op.opType}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                SyncEntityChip(op.status)
                            }
                            Text(
                                "المعرّف: ${op.entityId} • المحاولات: ${op.retryCount} • ${fmt.format(Date(op.updatedAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!op.lastError.isNullOrBlank()) {
                                Text(op.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceSettingsScreen(
    workspace: WorkspaceEntity?,
    role: WorkspaceRole,
    userEmail: String,
    userPrefs: UserPreferences,
    onSaveWorkspaceSettings: (name: String, desc: String, allowViewerExport: Boolean, allowEditorImport: Boolean) -> Unit,
    onThemeChanged: (ThemePreference) -> Unit,
    onAiToggleChanged: (Boolean) -> Unit,
    onNavigateRoute: (String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var wsName by remember(workspace) { mutableStateOf(workspace?.name.orEmpty()) }
    var wsDesc by remember(workspace) { mutableStateOf(workspace?.description.orEmpty()) }
    var allowViewerExport by remember(workspace) { mutableStateOf(workspace?.allowViewerExport ?: false) }
    var allowEditorImport by remember(workspace) { mutableStateOf(workspace?.allowEditorImport ?: true) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات ومساحة العمل") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الحساب والجلسة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LtrText(userEmail.ifBlank { "مستخدم Google" }, style = MaterialTheme.typography.bodyMedium)
                        Text("الدور الحالي: ${role.arabicLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("تسجيل الخروج")
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("المظهر وتجربة القراءة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemePreference.entries.forEach { pref ->
                                FilterChip(
                                    selected = userPrefs.themePreference == pref,
                                    onClick = { onThemeChanged(pref) },
                                    label = { Text(pref.arabicLabel) }
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("اقتراحات Gemini AI (اختيارية)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "معطّلة افتراضياً. عند التفعيل، تذهب جميع المخرجات لقائمة المراجعة كبيانات غير موثقة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = userPrefs.aiEnabled,
                                onCheckedChange = onAiToggleChanged,
                                modifier = Modifier.testTag("ai_optional_switch")
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("إعدادات مساحة العمل والسياسات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = wsName,
                            onValueChange = { wsName = it },
                            enabled = role.canManageSettings(),
                            label = { Text("اسم مساحة العمل") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = wsDesc,
                            onValueChange = { wsDesc = it },
                            enabled = role.canManageSettings(),
                            label = { Text("وصف المساحة") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("السماح للمشاهد بالتصدير", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = allowViewerExport,
                                onCheckedChange = { if (role.canManageSettings()) allowViewerExport = it },
                                enabled = role.canManageSettings()
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("السماح للمحرر بالاستيراد", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = allowEditorImport,
                                onCheckedChange = { if (role.canManageSettings()) allowEditorImport = it },
                                enabled = role.canManageSettings()
                            )
                        }
                        if (role.canManageSettings()) {
                            Button(
                                onClick = { onSaveWorkspaceSettings(wsName, wsDesc, allowViewerExport, allowEditorImport) },
                                enabled = wsName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().testTag("save_workspace_settings_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("حفظ إعدادات المساحة")
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الأدوات الإدارية والسجلات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { onNavigateRoute("sync_center") }, modifier = Modifier.fillMaxWidth()) {
                            Text("مركز المزامنة والطابور")
                        }
                        OutlinedButton(onClick = { onNavigateRoute("conflicts") }, modifier = Modifier.fillMaxWidth()) {
                            Text("شاشة حل التعارضات")
                        }
                        OutlinedButton(onClick = { onNavigateRoute("review_queue") }, modifier = Modifier.fillMaxWidth()) {
                            Text("قائمة المراجعة والاعتماد")
                        }
                        OutlinedButton(onClick = { onNavigateRoute("export_center") }, modifier = Modifier.fillMaxWidth()) {
                            Text("مركز التصدير (PDF / XLSX / CSV / JSON)")
                        }
                        if (role.canManageUsers()) {
                            OutlinedButton(onClick = { onNavigateRoute("users") }, modifier = Modifier.fillMaxWidth()) {
                                Text("إدارة المستخدمين والأدوار")
                            }
                        }
                        OutlinedButton(onClick = { onNavigateRoute("audit_logs") }, modifier = Modifier.fillMaxWidth()) {
                            Text("سجل التدقيق")
                        }
                        OutlinedButton(onClick = { onNavigateRoute("import_history") }, modifier = Modifier.fillMaxWidth()) {
                            Text("سجل عمليات الاستيراد")
                        }
                    }
                }
            }
        }
    }
}
