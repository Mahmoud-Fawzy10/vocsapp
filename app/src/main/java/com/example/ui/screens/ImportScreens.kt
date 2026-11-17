package com.example.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.local.ImportJobEntity
import com.example.model.DuplicateStrategy
import com.example.model.ImportColumnMapping
import com.example.model.WorkspaceRole
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LtrText
import com.example.ui.viewmodel.ImportWizardState
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCenterScreen(
    role: WorkspaceRole,
    allowEditorImport: Boolean,
    importState: ImportWizardState,
    onPickFile: (android.content.Context, android.net.Uri, String) -> Unit,
    onParsePasted: (String) -> Unit,
    onUpdateMapping: (ImportColumnMapping) -> Unit,
    onOpenPreview: () -> Unit,
    onOpenHistory: () -> Unit,
    onClearImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canImport = role.canImport(allowEditorImport)
    var pastedText by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val displayName = resolveFileName(context, uri)
            onPickFile(context, uri, displayName)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("مركز الاستيراد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenHistory, modifier = Modifier.testTag("open_import_history_btn")) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("السجل")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!canImport) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = "دورك الحالي (${role.arabicLabel}) لا يملك صلاحية استيراد البيانات في إعدادات المساحة.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("استيراد من ملف (CSV / XLSX / JSON)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "يدعم التطبيق ملفات Excel (XLSX) وCSV بترميز UTF-8 وملفات JSON المنظمة مع كشف الأعمدة تلقائياً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                filePicker.launch(
                                    arrayOf(
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/json",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            enabled = canImport,
                            modifier = Modifier.fillMaxWidth().testTag("import_pick_file_btn")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("اختيار ملف للاستيراد")
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("لصق قائمة مفردات أو JSON مباشرة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "الصق جدولاً مفصولاً بفاصلة أو Tab (مثل: word, meaning, pos, book) أو مصفوفة JSON.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = pastedText,
                                onValueChange = { pastedText = it },
                                label = { Text("word,meaning,pos,synonyms,book\nabandon,يهجر,verb,forsake;desert,New Enterprise") },
                                minLines = 4,
                                modifier = Modifier.fillMaxWidth().testTag("import_pasted_input")
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onParsePasted(pastedText) },
                                enabled = canImport && pastedText.isNotBlank(),
                                modifier = Modifier.weight(1f).testTag("import_parse_text_btn")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("تحليل النص")
                            }
                            OutlinedButton(
                                onClick = {
                                    pastedText = "word,meaning,pos,definition,synonyms,antonyms,book,unit,page\n" +
                                        "meticulous,دقيق جداً,adjective,Showing great attention to detail,careful;thorough,careless,Academic B2,Unit 2,34\n" +
                                        "mitigate,يخفف من حدة,verb,Make less severe or painful,alleviate;reduce,aggravate,Academic B2,Unit 3,48"
                                }
                            ) {
                                Text("نموذج تجريبي")
                            }
                        }
                    }
                }
            }

            val parsed = importState.parsedData
            if (parsed != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("تم تحليل: ${importState.fileName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "الصفوف المكتشفة: ${importState.previewRows.size} • الصيغة: ${parsed.sourceFormat}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(onClick = onClearImport) { Text("مسح") }
                            }

                            Text("ربط الأعمدة يدوياً عند الحاجة:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            ColumnMappingSelector(
                                label = "عمود الكلمة الإنجليزية *",
                                headers = parsed.headers,
                                selected = importState.mapping.wordColumn,
                                onSelect = { col -> onUpdateMapping(importState.mapping.copy(wordColumn = col)) }
                            )
                            ColumnMappingSelector(
                                label = "عمود المعنى الأساسي *",
                                headers = parsed.headers,
                                selected = importState.mapping.primaryMeaningColumn,
                                onSelect = { col -> onUpdateMapping(importState.mapping.copy(primaryMeaningColumn = col)) }
                            )
                            ColumnMappingSelector(
                                label = "عمود قسم الكلام",
                                headers = parsed.headers,
                                selected = importState.mapping.partOfSpeechColumn,
                                onSelect = { col -> onUpdateMapping(importState.mapping.copy(partOfSpeechColumn = col)) }
                            )
                            ColumnMappingSelector(
                                label = "عمود المرادفات",
                                headers = parsed.headers,
                                selected = importState.mapping.synonymsColumn,
                                onSelect = { col -> onUpdateMapping(importState.mapping.copy(synonymsColumn = col)) }
                            )
                            ColumnMappingSelector(
                                label = "عمود الكتاب",
                                headers = parsed.headers,
                                selected = importState.mapping.bookNameColumn,
                                onSelect = { col -> onUpdateMapping(importState.mapping.copy(bookNameColumn = col)) }
                            )

                            Button(
                                onClick = onOpenPreview,
                                modifier = Modifier.fillMaxWidth().testTag("go_import_preview_btn")
                            ) {
                                Icon(Icons.Default.Preview, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("معاينة الصفوف وكشف التكرارات (${importState.previewRows.size})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnMappingSelector(
    label: String,
    headers: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selected ?: "غير مربوط")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("غير مربوط") },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    }
                )
                headers.forEach { header ->
                    DropdownMenuItem(
                        text = { LtrText(header) },
                        onClick = {
                            onSelect(header)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportPreviewScreen(
    importState: ImportWizardState,
    onBulkDuplicateStrategy: (DuplicateStrategy) -> Unit,
    onRowDuplicateStrategy: (Int, DuplicateStrategy) -> Unit,
    onConfirmImport: () -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = importState.previewRows
    val duplicatesCount = rows.count { it.matchedWordId != null }
    val errorsCount = rows.count { it.validationError != null }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("معاينة الاستيراد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (importState.isImporting) {
                LinearProgressIndicator(
                    progress = { importState.progress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                Text("جاري استيراد البيانات (${(importState.progress * 100).toInt()}%)...", style = MaterialTheme.typography.bodySmall)
            }

            importState.lastReport?.let { report ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Text("اكتمل الاستيراد بنجاح", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "إضافة: ${report.createdCount} | دمج: ${report.mergedCount} | تحديث: ${report.updatedCount} | تخطي: ${report.skippedCount} | أخطاء: ${report.errorCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onOpenHistory) { Text("عرض سجل الاستيراد") }
                    }
                }
            }

            if (rows.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد صفوف للمعاينة",
                    subtitle = "اختر ملفاً أو الصق بيانات من مركز الاستيراد أولاً."
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "إجمالي الصفوف: ${rows.size} • مكررات مكتشفة: $duplicatesCount • أخطاء تحقق: $errorsCount",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (duplicatesCount > 0) {
                            Text("تطبيق جماعي على الكلمات المكررة:", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DuplicateStrategy.entries.forEach { st ->
                                    OutlinedButton(onClick = { onBulkDuplicateStrategy(st) }) {
                                        Text(st.arabicLabel, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = onConfirmImport,
                            enabled = !importState.isImporting,
                            modifier = Modifier.fillMaxWidth().testTag("confirm_execute_import_btn")
                        ) {
                            Text("تنفيذ وحفظ الاستيراد")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows, key = { it.rowIndex }) { row ->
                        Card(
                            border = when {
                                row.validationError != null -> BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                row.matchedWordId != null -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                                else -> null
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("#${row.rowIndex}", style = MaterialTheme.typography.labelSmall)
                                        LtrText(
                                            text = row.rawWord.ifBlank { "(فارغ)" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(row.partOfSpeech.arabicLabel, style = MaterialTheme.typography.labelSmall)
                                }
                                Text(row.primaryMeaning.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)

                                if (row.validationError != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Text(row.validationError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }

                                if (row.matchedReason != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                        Text(row.matchedReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        DuplicateStrategy.entries.forEach { st ->
                                            FilterChip(
                                                selected = row.strategy == st,
                                                onClick = { onRowDuplicateStrategy(row.rowIndex, st) },
                                                label = { Text(st.arabicLabel) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHistoryScreen(
    jobs: List<ImportJobEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedJobId by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar")) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("سجل عمليات الاستيراد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (jobs.isEmpty()) {
            EmptyStateView(
                title = "لا يوجد سجل استيراد بعد",
                subtitle = "ستظهر هنا جميع عمليات استيراد الملفات مع إحصائيات الإضافة والدمج والأخطاء.",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(jobs, key = { it.id }) { job ->
                    val errors = remember(job.errorsJson) {
                        runCatching {
                            val arr = JSONArray(job.errorsJson)
                            (0 until arr.length()).map { arr.optString(it) }
                        }.getOrDefault(emptyList())
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            expandedJobId = if (expandedJobId == job.id) null else job.id
                        }
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(job.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(job.sourceFormat, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "بواسطة ${job.userName} • ${fmt.format(Date(job.createdAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "الإجمالي: ${job.totalRows} | جديد: ${job.createdCount} | دمج: ${job.mergedCount} | تحديث: ${job.updatedCount} | تخطي: ${job.skippedCount} | أخطاء: ${job.errorCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (expandedJobId == job.id && errors.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                errors.forEach { err ->
                                    Text("• $err", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveFileName(context: android.content.Context, uri: android.net.Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
            return cursor.getString(idx) ?: "import_file"
        }
    }
    return uri.lastPathSegment ?: "import_file"
}
