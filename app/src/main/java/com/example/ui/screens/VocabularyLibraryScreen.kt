package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.BookEntity
import com.example.data.local.WordEntity
import com.example.data.local.WordLocationEntity
import com.example.domain.WordJsonCodec
import com.example.model.PartOfSpeech
import com.example.model.VocabularyFilter
import com.example.model.WordSortOrder
import com.example.model.WorkspaceRole
import com.example.tts.PronunciationManager
import com.example.ui.components.ConfirmDestructiveDialog
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LtrText
import com.example.ui.components.SyncEntityChip
import com.example.ui.components.VocabIdBadge

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularyLibraryScreen(
    words: List<WordEntity>,
    books: List<BookEntity>,
    locations: List<WordLocationEntity>,
    filter: VocabularyFilter,
    selectedWordIds: Set<String>,
    role: WorkspaceRole,
    allowViewerExport: Boolean,
    pronunciationManager: PronunciationManager,
    preferredAccent: String,
    preferredVoiceName: String,
    ttsSpeed: Float,
    onUpdateFilter: ((VocabularyFilter) -> VocabularyFilter) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenWord: (String) -> Unit,
    onAddWord: () -> Unit,
    onBulkDelete: (Set<String>) -> Unit,
    onBulkExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectionMode by remember(selectedWordIds) { mutableStateOf(selectedWordIds.isNotEmpty()) }

    val locsByWord = remember(locations) { locations.groupBy { it.wordId } }

    if (showDeleteConfirm) {
        ConfirmDestructiveDialog(
            title = "حذف المفردات المحددة",
            message = "هل أنت متأكد من حذف ${selectedWordIds.size} مفردة؟ سيتم تسجيل العملية في سجل التدقيق.",
            onConfirm = {
                onBulkDelete(selectedWordIds)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (role.canEditVocab()) {
                FloatingActionButton(
                    onClick = onAddWord,
                    modifier = Modifier.testTag("library_add_word_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة مفردة")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Search bar
            OutlinedTextField(
                value = filter.query,
                onValueChange = { q -> onUpdateFilter { it.copy(query = q) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_input"),
                placeholder = { Text("ابحث بالكلمة، المعنى، المرادفات، المشتقات، أو VOC-ID...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                trailingIcon = {
                    if (filter.query.isNotBlank()) {
                        IconButton(onClick = { onUpdateFilter { it.copy(query = "") } }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(8.dp))

            // Action & filter bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = showFilters,
                        onClick = { showFilters = !showFilters },
                        label = { Text("تصفية متقدمة") },
                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("library_filter_toggle")
                    )
                    FilterChip(
                        selected = selectionMode,
                        onClick = {
                            if (selectionMode) {
                                selectionMode = false
                                onClearSelection()
                            } else {
                                selectionMode = true
                            }
                        },
                        label = { Text(if (selectedWordIds.isEmpty()) "تحديد متعدد" else "محدد: ${selectedWordIds.size}") },
                        leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Column {
                    TextButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(filter.sortOrder.arabicLabel, style = MaterialTheme.typography.labelMedium)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        WordSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.arabicLabel) },
                                onClick = {
                                    onUpdateFilter { it.copy(sortOrder = order) }
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Bulk action toolbar
            AnimatedVisibility(visible = selectionMode && selectedWordIds.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onSelectAll) { Text("تحديد الكل") }
                            TextButton(onClick = onClearSelection) { Text("إلغاء") }
                        }
                        Row {
                            if (role.canExport(allowViewerExport)) {
                                IconButton(onClick = onBulkExport) {
                                    Icon(Icons.Default.Download, contentDescription = "تصدير المحدد")
                                }
                            }
                            if (role.canDeleteVocab()) {
                                IconButton(onClick = { showDeleteConfirm = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف المحدد", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Expandable filters panel
            AnimatedVisibility(visible = showFilters) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("قسم الكلام", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = filter.partOfSpeech == null,
                                onClick = { onUpdateFilter { it.copy(partOfSpeech = null) } },
                                label = { Text("الكل") }
                            )
                            PartOfSpeech.entries.take(7).forEach { pos ->
                                FilterChip(
                                    selected = filter.partOfSpeech == pos,
                                    onClick = { onUpdateFilter { it.copy(partOfSpeech = if (it.partOfSpeech == pos) null else pos) } },
                                    label = { Text(pos.arabicLabel) }
                                )
                            }
                        }

                        if (books.isNotEmpty()) {
                            Text("تصفية حسب الكتاب", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = filter.bookId == null,
                                    onClick = { onUpdateFilter { it.copy(bookId = null) } },
                                    label = { Text("كل الكتب") }
                                )
                                books.forEach { bk ->
                                    FilterChip(
                                        selected = filter.bookId == bk.id,
                                        onClick = { onUpdateFilter { it.copy(bookId = if (it.bookId == bk.id) null else bk.id) } },
                                        label = { Text(bk.title) }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = filter.unit.orEmpty(),
                                onValueChange = { u -> onUpdateFilter { it.copy(unit = u.ifBlank { null }) } },
                                label = { Text("الوحدة") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = filter.lesson.orEmpty(),
                                onValueChange = { l -> onUpdateFilter { it.copy(lesson = l.ifBlank { null }) } },
                                label = { Text("الدرس") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = filter.page.orEmpty(),
                                onValueChange = { p -> onUpdateFilter { it.copy(page = p.ifBlank { null }) } },
                                label = { Text("الصفحة") },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = filter.curriculumDistinctionOnly,
                                onClick = {
                                    onUpdateFilter { it.copy(curriculumDistinctionOnly = !it.curriculumDistinctionOnly) }
                                },
                                label = { Text("لها تمييز منهج") }
                            )
                            TextButton(onClick = { onUpdateFilter { VocabularyFilter() } }) {
                                Text("إعادة ضبط التصفية")
                            }
                        }
                    }
                }
            }

            if (words.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد مفردات مطابقة",
                    subtitle = if (filter.query.isBlank() && filter.partOfSpeech == null && filter.bookId == null) {
                        "أضف أول كلمة إنجليزية لبناء مكتبتك التعليمية."
                    } else {
                        "جرّب تعديل كلمات البحث أو إزالة بعض فلاتر التصفية."
                    },
                    actionLabel = if (role.canEditVocab()) "إضافة مفردة" else null,
                    onAction = if (role.canEditVocab()) onAddWord else null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(words, key = { it.id }) { word ->
                        val isSelected = word.id in selectedWordIds
                        val locs = locsByWord[word.id].orEmpty()
                        val synsCount = remember(word.synonymsJson) { WordJsonCodec.decodeSynonyms(word.synonymsJson).size }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectionMode) onToggleSelect(word.id) else onOpenWord(word.id)
                                }
                                .testTag("word_card_${word.humanId}"),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (selectionMode) {
                                            IconButton(onClick = { onToggleSelect(word.id) }) {
                                                Icon(
                                                    if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                    contentDescription = "تحديد"
                                                )
                                            }
                                        }
                                        VocabIdBadge(word.humanId)
                                        LtrText(
                                            text = word.word,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SyncEntityChip(word.syncStatus)
                                        IconButton(
                                            onClick = {
                                                pronunciationManager.speak(word.word, preferredAccent, preferredVoiceName, ttsSpeed)
                                            }
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "نطق ${word.word}",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = word.primaryMeaning,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (word.arabicExplanation.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = word.arabicExplanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val pos = PartOfSpeech.safeValueOf(word.partOfSpeech)
                                    MetaPill("${pos.arabicLabel} (${pos.englishLabel})")
                                    if (synsCount > 0) MetaPill("$synsCount مرادفات")
                                    locs.firstOrNull()?.let { loc ->
                                        MetaPill("${loc.bookName} • ص ${loc.pageNumber.ifBlank { "-" }}")
                                    }
                                    MetaPill("نسخة v${word.version}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
