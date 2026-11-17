package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.data.local.CurriculumEntryEntity
import com.example.data.local.WordEntity
import com.example.data.local.WordLocationEntity
import com.example.domain.WordJsonCodec
import com.example.model.CurriculumEntryDraft
import com.example.model.CurriculumEntryType
import com.example.model.PartOfSpeech
import com.example.model.UserPreferences
import com.example.model.VerificationStatus
import com.example.model.WordLocationDraft
import com.example.model.WorkspaceRole
import com.example.tts.PronunciationManager
import com.example.ui.components.ConfirmDestructiveDialog
import com.example.ui.components.LtrText
import com.example.ui.components.PronunciationBar
import com.example.ui.components.SyncEntityChip
import com.example.ui.components.VocabIdBadge

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordDetailScreen(
    word: WordEntity?,
    locations: List<WordLocationEntity>,
    curriculumEntries: List<CurriculumEntryEntity>,
    books: List<BookEntity>,
    role: WorkspaceRole,
    userPrefs: UserPreferences,
    aiBusy: Boolean,
    pronunciationManager: PronunciationManager,
    onBack: () -> Unit,
    onEditWord: (String) -> Unit,
    onDeleteWord: (String) -> Unit,
    onSaveLocation: (WordLocationDraft) -> Unit,
    onSaveCurriculum: (CurriculumEntryDraft) -> Unit,
    onRequestAiSuggestion: (WordEntity, String, String) -> Unit,
    onUpdateTtsSettings: (Float, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (word == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("تفاصيل المفردة") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                )
            }
        ) { inner ->
            Column(modifier = Modifier.padding(inner).padding(24.dp)) {
                Text("لم يتم العثور على المفردة أو تم حذفها.")
            }
        }
        return
    }

    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showAddCurriculumDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val meanings = remember(word.meaningsJson) { WordJsonCodec.decodeMeanings(word.meaningsJson) }
    val synonyms = remember(word.synonymsJson) { WordJsonCodec.decodeSynonyms(word.synonymsJson) }
    val antonyms = remember(word.antonymsJson) { WordJsonCodec.decodeAntonyms(word.antonymsJson) }
    val derivatives = remember(word.derivativesJson) { WordJsonCodec.decodeDerivatives(word.derivativesJson) }
    val expressions = remember(word.expressionsJson) { WordJsonCodec.decodeExpressions(word.expressionsJson) }
    val notes = remember(word.notesJson) { WordJsonCodec.decodeNotes(word.notesJson) }
    val examples = remember(word.exampleSentencesJson) { WordJsonCodec.decodeStringList(word.exampleSentencesJson) }

    val officialEntries = curriculumEntries.filter { it.entryType == CurriculumEntryType.OFFICIAL_CURRICULUM.name }
    val userObservations = curriculumEntries.filter { it.entryType == CurriculumEntryType.USER_OBSERVATION.name }

    if (showDeleteConfirm) {
        ConfirmDestructiveDialog(
            title = "حذف المفردة",
            message = "هل تريد حذف ${word.word} (${word.humanId})؟",
            onConfirm = {
                showDeleteConfirm = false
                onDeleteWord(word.id)
                onBack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showAddLocationDialog) {
        AddLocationDialog(
            wordId = word.id,
            books = books,
            onSave = {
                onSaveLocation(it)
                showAddLocationDialog = false
            },
            onDismiss = { showAddLocationDialog = false }
        )
    }

    if (showAddCurriculumDialog) {
        AddCurriculumDialog(
            wordId = word.id,
            canVerifyOfficial = role.canManageBooks(),
            onSave = {
                onSaveCurriculum(it)
                showAddCurriculumDialog = false
            },
            onDismiss = { showAddCurriculumDialog = false }
        )
    }

    if (showAiDialog) {
        AiSuggestionDialog(
            word = word,
            onGenerate = { focus, compare ->
                onRequestAiSuggestion(word, focus, compare)
                showAiDialog = false
            },
            onDismiss = { showAiDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VocabIdBadge(word.humanId)
                        LtrText(word.word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (role.canEditVocab()) {
                        IconButton(onClick = { onEditWord(word.id) }, modifier = Modifier.testTag("detail_edit_button")) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل المفردة")
                        }
                    }
                    if (role.canDeleteVocab()) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف المفردة", tint = MaterialTheme.colorScheme.error)
                        }
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
            // Header Summary Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val pos = PartOfSpeech.safeValueOf(word.partOfSpeech)
                            Text(
                                "${pos.arabicLabel} (${pos.englishLabel})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SyncEntityChip(word.syncStatus)
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("v${word.version}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }

                        LtrText(
                            text = word.word,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = word.primaryMeaning,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (word.englishDefinition.isNotBlank()) {
                            LtrText(
                                text = word.englishDefinition,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (word.arabicExplanation.isNotBlank()) {
                            Text(
                                text = word.arabicExplanation,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Pronunciation Bar
            item {
                PronunciationBar(
                    wordText = word.word,
                    phoneticText = word.phoneticText,
                    pronunciationManager = pronunciationManager,
                    preferredAccent = userPrefs.preferredAccent,
                    preferredVoiceName = userPrefs.preferredVoiceName,
                    speed = userPrefs.ttsSpeed,
                    onSettingsChanged = onUpdateTtsSettings
                )
            }

            // Optional Gemini AI card (disabled by default)
            if (userPrefs.aiEnabled && role.canEditVocab()) {
                item {
                    Card(
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("اقتراحات الذكاء الاصطناعي (اختياري)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "تُرسل الاقتراحات إلى قائمة المراجعة ولا تُنشر تلقائياً.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { showAiDialog = true }, enabled = !aiBusy) {
                                if (aiBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("توليد اقتراح")
                            }
                        }
                    }
                }
            }

            // Additional Meanings & Examples
            if (meanings.isNotEmpty() || examples.isNotEmpty()) {
                item {
                    SectionCard("المعاني الإضافية والأمثلة") {
                        meanings.forEach { m ->
                            Text("• ${m.arabicMeaning}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            if (m.englishNuance.isNotBlank()) {
                                LtrText(m.englishNuance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (m.contextExample.isNotBlank()) {
                                LtrText("“${m.contextExample}”", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        if (examples.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            Text("جمل أمثلة:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            examples.forEach { ex ->
                                LtrText("• $ex", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }

            // Synonyms & Antonyms
            if (synonyms.isNotEmpty() || antonyms.isNotEmpty()) {
                item {
                    SectionCard("المرادفات والمتضادات") {
                        if (synonyms.isNotEmpty()) {
                            Text("المرادفات (Synonyms):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            synonyms.forEach { s ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    LtrText(s.word, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("${s.meaning} ${if (s.notes.isNotBlank()) "(${s.notes})" else ""}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (antonyms.isNotEmpty()) {
                            if (synonyms.isNotEmpty()) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("المتضادات (Antonyms):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            antonyms.forEach { a ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    LtrText(a.word, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    Text("${a.meaning} ${if (a.notes.isNotBlank()) "(${a.notes})" else ""}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Derivatives & Expressions
            if (derivatives.isNotEmpty() || expressions.isNotEmpty()) {
                item {
                    SectionCard("المشتقات والتعبيرات والمتلازمات") {
                        if (derivatives.isNotEmpty()) {
                            Text("المشتقات (Derivatives):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            derivatives.forEach { d ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    LtrText("${d.word} (${d.formType.englishLabel})", fontWeight = FontWeight.SemiBold)
                                    Text("${d.arabicMeaning} ${d.relationshipNote}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (expressions.isNotEmpty()) {
                            if (derivatives.isNotEmpty()) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("التعبيرات والأفعال المركبة:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            expressions.forEach { e ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                            Text(e.type.arabicLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                        LtrText(e.expression, fontWeight = FontWeight.Bold)
                                    }
                                    if (e.arabicExplanation.isNotBlank()) {
                                        Text(e.arabicExplanation, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (e.exampleUsage.isNotBlank()) {
                                        LtrText(e.exampleUsage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Notes
            if (notes.isNotEmpty()) {
                item {
                    SectionCard("الملاحظات اللغوية والتعليمية") {
                        notes.forEach { n ->
                            Text("• [${n.category.arabicLabel}] ${n.content}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
            }

            // Curriculum Information (Strictly Separated)
            item {
                SectionCard(
                    title = "معلومات المنهج والملاحظات التعليمية",
                    action = if (role.canEditVocab()) {
                        {
                            TextButton(onClick = { showAddCurriculumDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("إضافة")
                            }
                        }
                    } else null
                ) {
                    if (curriculumEntries.isEmpty()) {
                        Text("لا توجد بيانات منهج أو ملاحظات تعليمية مسجلة لهذه الكلمة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        if (officialEntries.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                Text("معلومات المنهج الرسمية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            }
                            officialEntries.forEach { entry ->
                                CurriculumItemCard(entry)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (userObservations.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Text("ملاحظات تعليمية شخصية (غير رسمية)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                            userObservations.forEach { entry ->
                                CurriculumItemCard(entry)
                            }
                        }
                    }
                }
            }

            // Book Locations
            item {
                SectionCard(
                    title = "مواقع الكلمة في الكتب والمراجع",
                    action = if (role.canEditVocab()) {
                        {
                            TextButton(onClick = { showAddLocationDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("إضافة مرجع")
                            }
                        }
                    } else null
                ) {
                    if (locations.isEmpty()) {
                        Text("لم تُربط هذه الكلمة بكتاب أو صفحة بعد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        locations.forEach { loc ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text(loc.bookName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        "الوحدة: ${loc.unit.ifBlank { "-" }} | الدرس: ${loc.lesson.ifBlank { "-" }} | صفحة: ${loc.pageNumber.ifBlank { "-" }} | تمرين: ${loc.exerciseNumber.ifBlank { "-" }}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (loc.quotation.isNotBlank()) {
                                        LtrText("“${loc.quotation}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (loc.notes.isNotBlank()) {
                                        Text("ملاحظة: ${loc.notes}", style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun SectionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                action?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CurriculumItemCard(entry: CurriculumEntryEntity) {
    val verification = VerificationStatus.safeValueOf(entry.verificationStatus)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${entry.grade.ifBlank { "عام" }} • وحدة ${entry.unit.ifBlank { "-" }} • درس ${entry.lesson.ifBlank { "-" }}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(verification.arabicLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (entry.curriculumDistinction.isNotBlank()) Text("التمييز المنهجي: ${entry.curriculumDistinction}", style = MaterialTheme.typography.bodySmall)
            if (entry.importantExamNotes.isNotBlank()) Text("ملاحظات امتحانية: ${entry.importantExamNotes}", style = MaterialTheme.typography.bodySmall)
            if (entry.commonExamPatterns.isNotBlank()) Text("أنماط الأسئلة: ${entry.commonExamPatterns}", style = MaterialTheme.typography.bodySmall)
            if (entry.relatedGrammarRules.isNotBlank()) Text("القواعد المرتبطة: ${entry.relatedGrammarRules}", style = MaterialTheme.typography.bodySmall)
            if (entry.teacherNotes.isNotBlank()) Text("ملاحظات المعلم: ${entry.teacherNotes}", style = MaterialTheme.typography.bodySmall)
            if (entry.source.isNotBlank()) Text("المصدر: ${entry.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddLocationDialog(
    wordId: String,
    books: List<BookEntity>,
    onSave: (WordLocationDraft) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBookId by remember { mutableStateOf(books.firstOrNull()?.id.orEmpty()) }
    var bookName by remember { mutableStateOf(books.firstOrNull()?.title.orEmpty()) }
    var unit by remember { mutableStateOf("") }
    var lesson by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("") }
    var exercise by remember { mutableStateOf("") }
    var quotation by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مرجع كتاب", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (books.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        books.forEach { bk ->
                            FilterChip(
                                selected = selectedBookId == bk.id,
                                onClick = {
                                    selectedBookId = bk.id
                                    bookName = bk.title
                                },
                                label = { Text(bk.title) }
                            )
                        }
                    }
                }
                OutlinedTextField(value = bookName, onValueChange = { bookName = it }, label = { Text("اسم الكتاب *") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("الوحدة") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = lesson, onValueChange = { lesson = it }, label = { Text("الدرس") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = page, onValueChange = { page = it }, label = { Text("الصفحة") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = exercise, onValueChange = { exercise = it }, label = { Text("التمرين") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = quotation, onValueChange = { quotation = it }, label = { Text("الاقتباس من الكتاب (إنجليزي)") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        WordLocationDraft(
                            wordId = wordId,
                            bookId = selectedBookId,
                            bookName = bookName,
                            unit = unit,
                            lesson = lesson,
                            pageNumber = page,
                            exerciseNumber = exercise,
                            quotation = quotation,
                            notes = notes
                        )
                    )
                },
                enabled = bookName.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddCurriculumDialog(
    wordId: String,
    canVerifyOfficial: Boolean,
    onSave: (CurriculumEntryDraft) -> Unit,
    onDismiss: () -> Unit
) {
    var entryType by remember { mutableStateOf(CurriculumEntryType.USER_OBSERVATION) }
    var grade by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var lesson by remember { mutableStateOf("") }
    var distinction by remember { mutableStateOf("") }
    var examNotes by remember { mutableStateOf("") }
    var grammar by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة معلومة منهج / ملاحظة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = entryType == CurriculumEntryType.USER_OBSERVATION,
                        onClick = { entryType = CurriculumEntryType.USER_OBSERVATION },
                        label = { Text("ملاحظة شخصية") }
                    )
                    FilterChip(
                        selected = entryType == CurriculumEntryType.OFFICIAL_CURRICULUM,
                        onClick = { entryType = CurriculumEntryType.OFFICIAL_CURRICULUM },
                        label = { Text("منهج رسمي") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("الصف") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("الوحدة") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = lesson, onValueChange = { lesson = it }, label = { Text("الدرس") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = distinction, onValueChange = { distinction = it }, label = { Text("التمييز / الأهمية المنهجية") })
                OutlinedTextField(value = examNotes, onValueChange = { examNotes = it }, label = { Text("ملاحظات وأنماط الامتحان") })
                OutlinedTextField(value = grammar, onValueChange = { grammar = it }, label = { Text("قواعد مرتبطة") })
                OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("المصدر") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val status = when {
                        entryType == CurriculumEntryType.USER_OBSERVATION -> VerificationStatus.PERSONAL_NOTE
                        canVerifyOfficial -> VerificationStatus.VERIFIED_OFFICIAL
                        else -> VerificationStatus.PENDING_REVIEW
                    }
                    onSave(
                        CurriculumEntryDraft(
                            wordId = wordId,
                            entryType = entryType,
                            curriculumDistinction = distinction,
                            importantExamNotes = examNotes,
                            relatedGrammarRules = grammar,
                            grade = grade,
                            unit = unit,
                            lesson = lesson,
                            source = source,
                            verificationStatus = status
                        )
                    )
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AiSuggestionDialog(
    word: WordEntity,
    onGenerate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val focuses = listOf("معاني ومرادفات ومتضادات شاملة", "مشتقات ومتلازمات لفظية", "جمل أمثلة تعليمية", "مقارنة مع كلمة مشابهة")
    var selectedFocus by remember { mutableStateOf(focuses.first()) }
    var compareWord by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اقتراح بالذكاء الاصطناعي — ${word.word}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "تنبيه: يتم تمييز جميع الاقتراحات بأنها غير موثقة وتُرسل إلى قائمة المراجعة للموافقة قبل النشر.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                focuses.forEach { item ->
                    FilterChip(
                        selected = selectedFocus == item,
                        onClick = { selectedFocus = item },
                        label = { Text(item) }
                    )
                }
                if (selectedFocus == "مقارنة مع كلمة مشابهة") {
                    OutlinedTextField(
                        value = compareWord,
                        onValueChange = { compareWord = it },
                        label = { Text("الكلمة الإنجليزية للمقارنة") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onGenerate(selectedFocus, compareWord) }) {
                Text("إرسال لقائمة المراجعة")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
