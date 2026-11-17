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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AuditLogEntity
import com.example.data.local.ConflictRecordEntity
import com.example.data.local.ReviewQueueItemEntity
import com.example.data.local.WordEntity
import com.example.data.local.WorkspaceMemberEntity
import com.example.domain.ConflictEngine
import com.example.model.ConflictChoice
import com.example.model.MemberStatus
import com.example.model.ReviewStatus
import com.example.model.WorkspaceRole
import com.example.ui.components.ConfirmDestructiveDialog
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LtrText
import com.example.ui.components.RoleBadge
import com.example.ui.components.VocabIdBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueScreen(
    items: List<ReviewQueueItemEntity>,
    role: WorkspaceRole,
    onDecide: (itemId: String, approve: Boolean, notes: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingOnly by remember { mutableStateOf(true) }
    val filtered = remember(items, pendingOnly) {
        if (pendingOnly) items.filter { it.status == ReviewStatus.PENDING.name } else items
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("قائمة المراجعة والاعتماد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(
                    selected = pendingOnly,
                    onClick = { pendingOnly = true },
                    label = { Text("قيد الانتظار (${items.count { it.status == ReviewStatus.PENDING.name }})") }
                )
                FilterChip(
                    selected = !pendingOnly,
                    onClick = { pendingOnly = false },
                    label = { Text("الكل (${items.size})") }
                )
            }

            if (filtered.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد عناصر للمراجعة",
                    subtitle = "تظهر هنا اقتراحات الذكاء الاصطناعي والمقترحات المعلّقة قبل اعتمادها ونشرها في المكتبة."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        var reviewNotes by remember(item.id) { mutableStateOf(item.reviewNotes) }
                        val status = ReviewStatus.entries.firstOrNull { it.name == item.status } ?: ReviewStatus.PENDING

                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("review_item_${item.id}"),
                            border = if (item.aiGenerated && !item.verified) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    LtrText(item.targetWordText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (item.verified) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = if (item.aiGenerated && !item.verified) "اقتراح ذكاء اصطناعي غير موثق" else status.arabicLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                if (item.aiGenerated) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                        Text("نوع الاقتراح: ${item.suggestionType}", style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                Text(item.summaryArabic, style = MaterialTheme.typography.bodyMedium)

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LtrText(
                                        text = item.proposedPayload,
                                        style = MaterialTheme.typography.bodySmall,
                                        useMono = true,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                if (status == ReviewStatus.PENDING && role.canReview()) {
                                    OutlinedTextField(
                                        value = reviewNotes,
                                        onValueChange = { reviewNotes = it },
                                        label = { Text("ملاحظات المراجع (اختياري)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onDecide(item.id, true, reviewNotes) },
                                            modifier = Modifier.weight(1f).testTag("approve_review_btn_${item.id}")
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("اعتماد وتطبيق")
                                        }
                                        OutlinedButton(
                                            onClick = { onDecide(item.id, false, reviewNotes) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f).testTag("reject_review_btn_${item.id}")
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("رفض")
                                        }
                                    }
                                } else if (item.reviewNotes.isNotBlank()) {
                                    Text("ملاحظة المراجع: ${item.reviewNotes}", style = MaterialTheme.typography.bodySmall)
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
fun ConflictResolutionScreen(
    conflicts: List<ConflictRecordEntity>,
    words: List<WordEntity>,
    role: WorkspaceRole,
    onResolveConflict: (conflictId: String, choices: Map<String, ConflictChoice>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wordsById = remember(words) { words.associateBy { it.id } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("حل تعارضات المزامنة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (conflicts.isEmpty()) {
            EmptyStateView(
                title = "لا توجد تعارضات حالياً",
                subtitle = "عند حدوث تعديلات متضاربة من جهازين على نفس الحقل، ستظهر هنا للمقارنة والدمج الآمن دون فقدان البيانات.",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(conflicts, key = { it.id }) { conflict ->
                    val diffs = remember(conflict) {
                        ConflictEngine.buildDiffsForRecord(conflict, wordsById[conflict.wordId])
                    }
                    val choices = remember(conflict.id) {
                        mutableStateMapOf<String, ConflictChoice>().apply {
                            diffs.forEach { put(it.fieldKey, ConflictChoice.KEEP_LOCAL) }
                        }
                    }
                    val isOpen = conflict.status == "OPEN"

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("conflict_card_${conflict.id}"),
                        border = if (isOpen) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else null
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    VocabIdBadge(conflict.humanId)
                                    LtrText(conflict.wordText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isOpen) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = if (isOpen) "تعارض مفتوح" else "تم الحل",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Text(
                                "نسخة محلية: v${conflict.localVersion} • نسخة سحابية: v${conflict.remoteVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isOpen && diffs.isNotEmpty() && role.canResolveConflicts()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { diffs.forEach { choices[it.fieldKey] = ConflictChoice.KEEP_LOCAL } },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("اختيار كل المحلي", style = MaterialTheme.typography.labelSmall) }
                                    OutlinedButton(
                                        onClick = { diffs.forEach { choices[it.fieldKey] = ConflictChoice.USE_REMOTE } },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("اختيار كل السحابي", style = MaterialTheme.typography.labelSmall) }
                                }
                            }

                            diffs.forEach { diff ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            Text(diff.fieldArabicLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        }
                                        if (diff.baseValue.isNotBlank()) {
                                            Text("الأصل: ${diff.baseValue.take(120)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text("المحلي: ${diff.localValue.ifBlank { "(فارغ)" }.take(180)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        Text("السحابي: ${diff.remoteValue.ifBlank { "(فارغ)" }.take(180)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)

                                        if (isOpen && role.canResolveConflicts()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(
                                                    selected = choices[diff.fieldKey] == ConflictChoice.KEEP_LOCAL,
                                                    onClick = { choices[diff.fieldKey] = ConflictChoice.KEEP_LOCAL },
                                                    label = { Text("إبقاء المحلي") }
                                                )
                                                FilterChip(
                                                    selected = choices[diff.fieldKey] == ConflictChoice.USE_REMOTE,
                                                    onClick = { choices[diff.fieldKey] = ConflictChoice.USE_REMOTE },
                                                    label = { Text("اعتماد السحابي") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isOpen && role.canResolveConflicts()) {
                                Button(
                                    onClick = { onResolveConflict(conflict.id, choices.toMap()) },
                                    modifier = Modifier.fillMaxWidth().testTag("resolve_conflict_btn_${conflict.id}")
                                ) {
                                    Text("حفظ النسخة المدمجة وإغلاق التعارض")
                                }
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
fun UserManagementScreen(
    members: List<WorkspaceMemberEntity>,
    currentUserId: String,
    role: WorkspaceRole,
    onSaveMember: (uid: String, name: String, email: String, role: WorkspaceRole, status: MemberStatus) -> Unit,
    onRemoveMember: (uid: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMemberDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<WorkspaceMemberEntity?>(null) }
    var memberToDelete by remember { mutableStateOf<WorkspaceMemberEntity?>(null) }

    if (memberToDelete != null) {
        ConfirmDestructiveDialog(
            title = "إزالة العضو",
            message = "هل تريد إزالة ${memberToDelete!!.displayName} من مساحة العمل؟",
            onConfirm = {
                onRemoveMember(memberToDelete!!.userId)
                memberToDelete = null
            },
            onDismiss = { memberToDelete = null }
        )
    }

    if (showMemberDialog) {
        MemberEditDialog(
            existing = editingMember,
            onSave = { uid, name, email, r, st ->
                onSaveMember(uid, name, email, r, st)
                showMemberDialog = false
            },
            onDismiss = { showMemberDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("إدارة المستخدمين والأدوار") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            if (role.canManageUsers()) {
                FloatingActionButton(
                    onClick = {
                        editingMember = null
                        showMemberDialog = true
                    },
                    modifier = Modifier.testTag("add_member_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة عضو")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("صلاحيات الأدوار المحمية بقواعد Firestore:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("• المالك: تحكم كامل بالمستخدمين والكتب والإعدادات والحذف وسجل التدقيق.", style = MaterialTheme.typography.bodySmall)
                        Text("• المحرر: إضافة وتعديل المفردات والمراجع والاستيراد إذا سُمح.", style = MaterialTheme.typography.bodySmall)
                        Text("• المراجع: اعتماد أو رفض المقترحات وقائمة المراجعة.", style = MaterialTheme.typography.bodySmall)
                        Text("• المشاهد: القراءة والبحث فقط (والتصدير إذا سُمح).", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            items(members, key = { it.id }) { m ->
                val mRole = WorkspaceRole.safeValueOf(m.role)
                val mStatus = MemberStatus.safeValueOf(m.status)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                RoleBadge(mRole)
                            }
                            if (m.email.isNotBlank()) {
                                LtrText(m.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "UID: ${m.userId.take(16)} • الحالة: ${mStatus.arabicLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mStatus == MemberStatus.SUSPENDED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (role.canManageUsers() && m.userId != currentUserId) {
                            IconButton(onClick = {
                                editingMember = m
                                showMemberDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل العضو")
                            }
                            IconButton(onClick = { memberToDelete = m }) {
                                Icon(Icons.Default.Delete, contentDescription = "إزالة العضو", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberEditDialog(
    existing: WorkspaceMemberEntity?,
    onSave: (String, String, String, WorkspaceRole, MemberStatus) -> Unit,
    onDismiss: () -> Unit
) {
    var uid by remember(existing) { mutableStateOf(existing?.userId.orEmpty()) }
    var name by remember(existing) { mutableStateOf(existing?.displayName.orEmpty()) }
    var email by remember(existing) { mutableStateOf(existing?.email.orEmpty()) }
    var selectedRole by remember(existing) {
        mutableStateOf(
            WorkspaceRole.safeValueOf(existing?.role ?: WorkspaceRole.EDITOR.name)
                .let { if (it == WorkspaceRole.OWNER && existing == null) WorkspaceRole.EDITOR else it }
        )
    }
    var selectedStatus by remember(existing) {
        mutableStateOf(MemberStatus.safeValueOf(existing?.status ?: MemberStatus.ACTIVE.name))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "دعوة / إضافة عضو" else "تعديل صلاحية العضو", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uid,
                    onValueChange = { uid = it },
                    enabled = existing == null,
                    label = { Text("معرّف المستخدم Firebase UID *") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم المعروض *") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني") },
                    singleLine = true
                )
                Text("الدور:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(WorkspaceRole.EDITOR, WorkspaceRole.REVIEWER, WorkspaceRole.VIEWER).forEach { r ->
                        FilterChip(
                            selected = selectedRole == r,
                            onClick = { selectedRole = r },
                            label = { Text(r.arabicLabel) }
                        )
                    }
                }
                Text("الحالة:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemberStatus.entries.forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.arabicLabel) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(uid, name, email, selectedRole, selectedStatus) },
                enabled = uid.isNotBlank() && name.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogsScreen(
    logs: List<AuditLogEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale("ar")) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("سجل التدقيق والعمليات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (logs.isEmpty()) {
            EmptyStateView(
                title = "لا توجد سجلات تدقيق بعد",
                subtitle = "تُسجّل هنا جميع عمليات الإنشاء والتعديل والحذف والاستيراد والتصدير وحل التعارضات بشكل غير قابل للتعديل.",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(log.actorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    RoleBadge(WorkspaceRole.safeValueOf(log.actorRole))
                                }
                                Text(log.action, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            Text(
                                "${log.entityType} • ${fmt.format(Date(log.createdAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
