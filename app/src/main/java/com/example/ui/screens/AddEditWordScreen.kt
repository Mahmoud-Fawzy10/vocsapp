package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.local.WordEntity
import com.example.domain.DuplicateDetector
import com.example.domain.WordJsonCodec
import com.example.model.AntonymItem
import com.example.model.DerivativeForm
import com.example.model.DerivativeItem
import com.example.model.ExpressionItem
import com.example.model.ExpressionType
import com.example.model.MeaningItem
import com.example.model.NoteCategory
import com.example.model.PartOfSpeech
import com.example.model.SynonymItem
import com.example.model.WordDraft
import com.example.model.WordNoteItem
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditWordScreen(
    existingWord: WordEntity?,
    allWords: List<WordEntity>,
    onBack: () -> Unit,
    onSave: (WordDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    var wordText by remember(existingWord) { mutableStateOf(existingWord?.word.orEmpty()) }
    var partOfSpeech by remember(existingWord) {
        mutableStateOf(PartOfSpeech.safeValueOf(existingWord?.partOfSpeech ?: PartOfSpeech.NOUN.name))
    }
    var primaryMeaning by remember(existingWord) { mutableStateOf(existingWord?.primaryMeaning.orEmpty()) }
    var englishDef by remember(existingWord) { mutableStateOf(existingWord?.englishDefinition.orEmpty()) }
    var arabicExplanation by remember(existingWord) { mutableStateOf(existingWord?.arabicExplanation.orEmpty()) }
    var phoneticText by remember(existingWord) { mutableStateOf(existingWord?.phoneticText.orEmpty()) }
    var examplesText by remember(existingWord) {
        mutableStateOf(
            WordJsonCodec.decodeStringList(existingWord?.exampleSentencesJson).joinToString("\n")
        )
    }

    val meanings = remember(existingWord) {
        mutableStateListOf<MeaningItem>().apply {
            addAll(WordJsonCodec.decodeMeanings(existingWord?.meaningsJson))
        }
    }
    val synonyms = remember(existingWord) {
        mutableStateListOf<SynonymItem>().apply {
            addAll(WordJsonCodec.decodeSynonyms(existingWord?.synonymsJson))
        }
    }
    val antonyms = remember(existingWord) {
        mutableStateListOf<AntonymItem>().apply {
            addAll(WordJsonCodec.decodeAntonyms(existingWord?.antonymsJson))
        }
    }
    val derivatives = remember(existingWord) {
        mutableStateListOf<DerivativeItem>().apply {
            addAll(WordJsonCodec.decodeDerivatives(existingWord?.derivativesJson))
        }
    }
    val expressions = remember(existingWord) {
        mutableStateListOf<ExpressionItem>().apply {
            addAll(WordJsonCodec.decodeExpressions(existingWord?.expressionsJson))
        }
    }
    val notes = remember(existingWord) {
        mutableStateListOf<WordNoteItem>().apply {
            addAll(WordJsonCodec.decodeNotes(existingWord?.notesJson))
        }
    }

    val duplicateMatches = remember(wordText, partOfSpeech, allWords, existingWord) {
        DuplicateDetector.findMatches(wordText, partOfSpeech, allWords, ignoreWordId = existingWord?.id)
    }

    var newMeaningInput by remember { mutableStateOf("") }
    var newSynWord by remember { mutableStateOf("") }
    var newSynMeaning by remember { mutableStateOf("") }
    var newAntWord by remember { mutableStateOf("") }
    var newAntMeaning by remember { mutableStateOf("") }
    var newDerivWord by remember { mutableStateOf("") }
    var newDerivForm by remember { mutableStateOf(DerivativeForm.NOUN) }
    var newDerivArabic by remember { mutableStateOf("") }
    var newExpText by remember { mutableStateOf("") }
    var newExpType by remember { mutableStateOf(ExpressionType.COLLOCATION) }
    var newExpArabic by remember { mutableStateOf("") }
    var newNoteContent by remember { mutableStateOf("") }
    var newNoteCategory by remember { mutableStateOf(NoteCategory.TEACHER) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (existingWord == null) "إضافة مفردة جديدة" else "تعديل ${existingWord.humanId}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            onSave(
                                WordDraft(
                                    existingId = existingWord?.id,
                                    humanId = existingWord?.humanId.orEmpty(),
                                    word = wordText,
                                    partOfSpeech = partOfSpeech,
                                    primaryMeaning = primaryMeaning,
                                    englishDefinition = englishDef,
                                    arabicExplanation = arabicExplanation,
                                    phoneticText = phoneticText,
                                    exampleSentences = examplesText.lines().map { it.trim() }.filter { it.isNotBlank() },
                                    meanings = meanings.toList(),
                                    synonyms = synonyms.toList(),
                                    antonyms = antonyms.toList(),
                                    derivatives = derivatives.toList(),
                                    expressions = expressions.toList(),
                                    notes = notes.toList(),
                                    version = existingWord?.version ?: 1L
                                )
                            )
                        },
                        enabled = wordText.isNotBlank() && primaryMeaning.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp).testTag("save_word_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("حفظ")
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
            // Duplicate Warning
            if (duplicateMatches.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("duplicate_warning_banner")
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("تنبيه تطابق محتمل", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                duplicateMatches.forEach { m ->
                                    Text("• ${m.reasonArabic}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // Main Information
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("المعلومات الأساسية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = wordText,
                                onValueChange = { wordText = it },
                                label = { Text("English Word *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_english_word")
                            )
                        }

                        Text("قسم الكلام", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PartOfSpeech.entries.forEach { pos ->
                                FilterChip(
                                    selected = partOfSpeech == pos,
                                    onClick = { partOfSpeech = pos },
                                    label = { Text("${pos.arabicLabel} (${pos.englishLabel})") }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = primaryMeaning,
                            onValueChange = { primaryMeaning = it },
                            label = { Text("المعنى الأساسي بالعربية *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_primary_meaning")
                        )

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = englishDef,
                                onValueChange = { englishDef = it },
                                label = { Text("English Definition") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = arabicExplanation,
                            onValueChange = { arabicExplanation = it },
                            label = { Text("الشرح العربي التفصيلي") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = phoneticText,
                                onValueChange = { phoneticText = it },
                                label = { Text("Phonetic Transcription (e.g. /əˈbændən/)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = examplesText,
                                onValueChange = { examplesText = it },
                                label = { Text("Example Sentences (one per line)") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Additional Meanings
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("معانٍ إضافية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        meanings.forEachIndexed { index, item ->
                            RemovableItemRow(label = item.arabicMeaning) { meanings.removeAt(index) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newMeaningInput,
                                onValueChange = { newMeaningInput = it },
                                label = { Text("معنى إضافي") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    if (newMeaningInput.isNotBlank()) {
                                        meanings.add(MeaningItem(UUID.randomUUID().toString().take(8), newMeaningInput.trim()))
                                        newMeaningInput = ""
                                    }
                                }
                            ) { Icon(Icons.Default.Add, contentDescription = "إضافة معنى") }
                        }
                    }
                }
            }

            // Synonyms & Antonyms
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("المرادفات (Synonyms)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        synonyms.forEachIndexed { idx, s ->
                            RemovableItemRow("${s.word} — ${s.meaning}") { synonyms.removeAt(idx) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = newSynWord, onValueChange = { newSynWord = it }, label = { Text("Synonym") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = newSynMeaning, onValueChange = { newSynMeaning = it }, label = { Text("المعنى") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedButton(onClick = {
                                if (newSynWord.isNotBlank()) {
                                    synonyms.add(SynonymItem(UUID.randomUUID().toString().take(8), newSynWord.trim(), newSynMeaning.trim()))
                                    newSynWord = ""; newSynMeaning = ""
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = "إضافة مرادف") }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text("المتضادات (Antonyms)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        antonyms.forEachIndexed { idx, a ->
                            RemovableItemRow("${a.word} — ${a.meaning}") { antonyms.removeAt(idx) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = newAntWord, onValueChange = { newAntWord = it }, label = { Text("Antonym") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = newAntMeaning, onValueChange = { newAntMeaning = it }, label = { Text("المعنى") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedButton(onClick = {
                                if (newAntWord.isNotBlank()) {
                                    antonyms.add(AntonymItem(UUID.randomUUID().toString().take(8), newAntWord.trim(), newAntMeaning.trim()))
                                    newAntWord = ""; newAntMeaning = ""
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = "إضافة مضاد") }
                        }
                    }
                }
            }

            // Derivatives & Expressions
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("المشتقات (Derivatives)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        derivatives.forEachIndexed { idx, d ->
                            RemovableItemRow("${d.word} (${d.formType.arabicLabel}) — ${d.arabicMeaning}") { derivatives.removeAt(idx) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DerivativeForm.entries.forEach { form ->
                                FilterChip(selected = newDerivForm == form, onClick = { newDerivForm = form }, label = { Text(form.arabicLabel) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = newDerivWord, onValueChange = { newDerivWord = it }, label = { Text("Derivative") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = newDerivArabic, onValueChange = { newDerivArabic = it }, label = { Text("المعنى") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedButton(onClick = {
                                if (newDerivWord.isNotBlank()) {
                                    derivatives.add(DerivativeItem(UUID.randomUUID().toString().take(8), newDerivWord.trim(), newDerivForm, "", newDerivArabic.trim()))
                                    newDerivWord = ""; newDerivArabic = ""
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = "إضافة مشتق") }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text("التعبيرات والمتلازمات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        expressions.forEachIndexed { idx, e ->
                            RemovableItemRow("[${e.type.arabicLabel}] ${e.expression}: ${e.arabicExplanation}") { expressions.removeAt(idx) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExpressionType.entries.forEach { t ->
                                FilterChip(selected = newExpType == t, onClick = { newExpType = t }, label = { Text(t.arabicLabel) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = newExpText, onValueChange = { newExpText = it }, label = { Text("Expression") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = newExpArabic, onValueChange = { newExpArabic = it }, label = { Text("الشرح") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedButton(onClick = {
                                if (newExpText.isNotBlank()) {
                                    expressions.add(ExpressionItem(UUID.randomUUID().toString().take(8), newExpType, newExpText.trim(), "", newExpArabic.trim()))
                                    newExpText = ""; newExpArabic = ""
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = "إضافة تعبير") }
                        }
                    }
                }
            }

            // Notes
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الملاحظات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        notes.forEachIndexed { idx, n ->
                            RemovableItemRow("[${n.category.arabicLabel}] ${n.content}") { notes.removeAt(idx) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NoteCategory.entries.forEach { cat ->
                                FilterChip(selected = newNoteCategory == cat, onClick = { newNoteCategory = cat }, label = { Text(cat.arabicLabel) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = newNoteContent, onValueChange = { newNoteContent = it }, label = { Text("نص الملاحظة") }, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                if (newNoteContent.isNotBlank()) {
                                    notes.add(WordNoteItem(UUID.randomUUID().toString().take(8), newNoteCategory, newNoteContent.trim()))
                                    newNoteContent = ""
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = "إضافة ملاحظة") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemovableItemRow(label: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "إزالة")
            }
        }
    }
}
