package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.BookEntity
import com.example.data.local.CurriculumEntryEntity
import com.example.data.local.WordEntity
import com.example.data.local.WordLocationEntity
import com.example.model.CurriculumEntryType
import com.example.model.VerificationStatus
import com.example.model.WorkspaceRole
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LtrText
import com.example.ui.components.SyncEntityChip

@Composable
fun BooksAndCurriculumScreen(
    books: List<BookEntity>,
    locations: List<WordLocationEntity>,
    curriculumEntries: List<CurriculumEntryEntity>,
    words: List<WordEntity>,
    role: WorkspaceRole,
    onSaveBook: (existingId: String?, title: String, publisher: String, edition: String, grade: String, notes: String) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var editingBook by remember { mutableStateOf<BookEntity?>(null) }
    var showBookDialog by remember { mutableStateOf(false) }

    val wordsById = remember(words) { words.associateBy { it.id } }

    if (showBookDialog) {
        BookEditDialog(
            existing = editingBook,
            onSave = { title, pub, ed, grade, notes ->
                onSaveBook(editingBook?.id, title, pub, ed, grade, notes)
                showBookDialog = false
            },
            onDismiss = { showBookDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (selectedTab == 0 && role.canManageBooks()) {
                FloatingActionButton(
                    onClick = {
                        editingBook = null
                        showBookDialog = true
                    },
                    modifier = Modifier.testTag("add_book_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة كتاب")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("الكتب (${books.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("المواقع (${locations.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("المنهج (${curriculumEntries.size})") })
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث في الكتب أو الوحدات أو الدروس أو المنهج...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            when (selectedTab) {
                0 -> {
                    val filteredBooks = books.filter {
                        searchQuery.isBlank() ||
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.grade.contains(searchQuery, ignoreCase = true) ||
                            it.publisher.contains(searchQuery, ignoreCase = true)
                    }
                    if (filteredBooks.isEmpty()) {
                        EmptyStateView(
                            title = "لا توجد كتب مسجلة",
                            subtitle = "أضف الكتب الدراسية والمراجع لربط المفردات بالوحدات والدروس وأرقام الصفحات.",
                            actionLabel = if (role.canManageBooks()) "إضافة كتاب" else null,
                            onAction = if (role.canManageBooks()) {
                                {
                                    editingBook = null
                                    showBookDialog = true
                                }
                            } else null
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredBooks, key = { it.id }) { book ->
                                val linkedCount = locations.count { it.bookId == book.id }
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${book.grade.ifBlank { "كل المراحل" }} • ${book.publisher.ifBlank { "دار نشر غير محددة" }} • ${book.editionOrYear}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text("$linkedCount مفردة مرتبطة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                SyncEntityChip(book.syncStatus)
                                            }
                                        }
                                        if (role.canManageBooks()) {
                                            IconButton(onClick = {
                                                editingBook = book
                                                showBookDialog = true
                                            }) {
                                                Icon(Icons.Default.Edit, contentDescription = "تعديل الكتاب")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val filteredLocs = locations.filter { loc ->
                        val wordText = wordsById[loc.wordId]?.word.orEmpty()
                        searchQuery.isBlank() ||
                            loc.bookName.contains(searchQuery, ignoreCase = true) ||
                            loc.unit.contains(searchQuery, ignoreCase = true) ||
                            loc.lesson.contains(searchQuery, ignoreCase = true) ||
                            loc.pageNumber.contains(searchQuery, ignoreCase = true) ||
                            wordText.contains(searchQuery, ignoreCase = true)
                    }
                    if (filteredLocs.isEmpty()) {
                        EmptyStateView(
                            title = "لا توجد مراجع صفحات مطابقة",
                            subtitle = "افتح أي مفردة واضغط «إضافة مرجع» لربطها بكتاب ووحدة ورقم صفحة."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredLocs, key = { it.id }) { loc ->
                                val w = wordsById[loc.wordId]
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        if (w != null) onOpenWord(w.id)
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            LtrText(w?.word ?: loc.wordId, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(loc.bookName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text(
                                            "الوحدة: ${loc.unit.ifBlank { "-" }} | الدرس: ${loc.lesson.ifBlank { "-" }} | ص ${loc.pageNumber.ifBlank { "-" }}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (loc.quotation.isNotBlank()) {
                                            LtrText("“${loc.quotation}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val filteredCur = curriculumEntries.filter { entry ->
                        val wordText = wordsById[entry.wordId]?.word.orEmpty()
                        searchQuery.isBlank() ||
                            wordText.contains(searchQuery, ignoreCase = true) ||
                            entry.curriculumDistinction.contains(searchQuery, ignoreCase = true) ||
                            entry.importantExamNotes.contains(searchQuery, ignoreCase = true) ||
                            entry.unit.contains(searchQuery, ignoreCase = true)
                    }
                    val official = filteredCur.filter { it.entryType == CurriculumEntryType.OFFICIAL_CURRICULUM.name }
                    val personal = filteredCur.filter { it.entryType == CurriculumEntryType.USER_OBSERVATION.name }

                    if (filteredCur.isEmpty()) {
                        EmptyStateView(
                            title = "لا توجد قيود منهج أو ملاحظات تعليمية",
                            subtitle = "يمكنك إضافة تمييز المنهج الرسمي أو الملاحظات الشخصية من داخل صفحة تفاصيل كل مفردة."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (official.isNotEmpty()) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                        Text("معلومات المنهج الرسمية (${official.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                items(official, key = { it.id }) { entry ->
                                    CurriculumOverviewCard(entry = entry, word = wordsById[entry.wordId], onOpenWord = onOpenWord)
                                }
                            }
                            if (personal.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                        Text("ملاحظات تعليمية شخصية (${personal.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                items(personal, key = { it.id }) { entry ->
                                    CurriculumOverviewCard(entry = entry, word = wordsById[entry.wordId], onOpenWord = onOpenWord)
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
private fun CurriculumOverviewCard(
    entry: CurriculumEntryEntity,
    word: WordEntity?,
    onOpenWord: (String) -> Unit
) {
    val verification = VerificationStatus.safeValueOf(entry.verificationStatus)
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            if (word != null) onOpenWord(word.id)
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                LtrText(word?.word ?: entry.wordId, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(verification.arabicLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Text("الصف: ${entry.grade.ifBlank { "-" }} • وحدة ${entry.unit.ifBlank { "-" }} • درس ${entry.lesson.ifBlank { "-" }}", style = MaterialTheme.typography.labelMedium)
            if (entry.curriculumDistinction.isNotBlank()) Text(entry.curriculumDistinction, style = MaterialTheme.typography.bodySmall)
            if (entry.importantExamNotes.isNotBlank()) Text("امتحاني: ${entry.importantExamNotes}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BookEditDialog(
    existing: BookEntity?,
    onSave: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var publisher by remember(existing) { mutableStateOf(existing?.publisher.orEmpty()) }
    var edition by remember(existing) { mutableStateOf(existing?.editionOrYear.orEmpty()) }
    var grade by remember(existing) { mutableStateOf(existing?.grade.orEmpty()) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "إضافة كتاب جديد" else "تعديل بيانات الكتاب", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان الكتاب *") }, singleLine = true)
                OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("الناشر") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = edition, onValueChange = { edition = it }, label = { Text("الطبعة / السنة") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("الصف / المرحلة") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, publisher, edition, grade, notes) }, enabled = title.isNotBlank()) {
                Text("حفظ")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
