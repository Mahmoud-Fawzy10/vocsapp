package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.WordEntity
import com.example.data.local.WorkspaceEntity
import com.example.model.DashboardStats
import com.example.model.PartOfSpeech
import com.example.model.SyncState
import com.example.model.WorkspaceRole
import com.example.ui.components.EmptyStateView
import com.example.ui.components.LtrText
import com.example.ui.components.RoleBadge
import com.example.ui.components.SyncEntityChip
import com.example.ui.components.SyncStatusBanner
import com.example.ui.components.VocabIdBadge

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    workspace: WorkspaceEntity?,
    role: WorkspaceRole,
    stats: DashboardStats,
    syncState: SyncState,
    lastSyncTimestamp: Long,
    words: List<WordEntity>,
    onOpenWord: (String) -> Unit,
    onAddWord: () -> Unit,
    onLoadStarterPack: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val recentlyAdded = words.sortedByDescending { it.createdAt }.take(4)
    val recentlyUpdated = words.sortedByDescending { it.updatedAt }.take(4)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = workspace?.name ?: "مكتبة المفردات",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            RoleBadge(role = role)
                        }
                        Image(
                            painter = painterResource(id = R.drawable.img_vocab_hero),
                            contentDescription = "شعار المكتبة",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SyncStatusBanner(
                        syncState = syncState,
                        lastSyncTimestamp = lastSyncTimestamp,
                        pendingCount = stats.pendingSyncCount,
                        onClick = { onNavigateRoute("sync_center") }
                    )
                }
            }
        }

        // Dashboard metrics grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        title = "إجمالي المفردات",
                        value = stats.totalWords.toString(),
                        icon = Icons.Default.Translate,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("library") }
                    )
                    StatCard(
                        title = "إجمالي المعاني",
                        value = stats.totalMeanings.toString(),
                        icon = Icons.Default.AutoStories,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("library") }
                    )
                    StatCard(
                        title = "الكتب والمراجع",
                        value = stats.totalBooks.toString(),
                        icon = Icons.Default.MenuBook,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("books") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        title = "مزامنة معلّقة",
                        value = stats.pendingSyncCount.toString(),
                        icon = Icons.Default.CloudSync,
                        isAlert = stats.pendingSyncCount > 0,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("sync_center") }
                    )
                    StatCard(
                        title = "قائمة المراجعة",
                        value = stats.pendingReviewCount.toString(),
                        icon = Icons.Default.RateReview,
                        isAlert = stats.pendingReviewCount > 0,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("review_queue") }
                    )
                    StatCard(
                        title = "نشاط الاستيراد",
                        value = stats.recentImportCount.toString(),
                        icon = Icons.Default.UploadFile,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateRoute("import_history") }
                    )
                }
            }
        }

        if (stats.openConflictsCount > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateRoute("conflicts") }
                        .testTag("dashboard_conflict_alert"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "يوجد ${stats.openConflictsCount} تعارض تعديل يتطلب مراجعتك",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "اضغط لمقارنة النسخ وحل التعارض دون فقدان أي تعديل.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions
        item {
            Text("إجراءات سريعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (role.canEditVocab()) {
                    Button(onClick = onAddWord, modifier = Modifier.testTag("dash_add_word_btn")) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إضافة مفردة")
                    }
                }
                if (role.canImport(workspace?.allowEditorImport ?: false)) {
                    FilledTonalButton(onClick = { onNavigateRoute("import_center") }) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("استيراد")
                    }
                }
                if (role.canExport(workspace?.allowViewerExport ?: false)) {
                    FilledTonalButton(onClick = { onNavigateRoute("export_center") }) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تصدير")
                    }
                }
                AssistChip(
                    onClick = { onNavigateRoute("review_queue") },
                    label = { Text("المراجعة (${stats.pendingReviewCount})") },
                    leadingIcon = { Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { onNavigateRoute("conflicts") },
                    label = { Text("التعارضات (${stats.openConflictsCount})") },
                    leadingIcon = { Icon(Icons.Default.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                if (role.canManageUsers()) {
                    AssistChip(
                        onClick = { onNavigateRoute("users") },
                        label = { Text("المستخدمون") },
                        leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                AssistChip(
                    onClick = { onNavigateRoute("audit_logs") },
                    label = { Text("سجل التدقيق") },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        if (words.isEmpty()) {
            item {
                EmptyStateView(
                    title = "المكتبة فارغة حالياً",
                    subtitle = "ابدأ بإضافة مفردات جديدة يدوياً، أو استورد من ملف CSV / Excel، أو حمّل 3 مفردات نموذجية تعليمية لتجربة التطبيق.",
                    actionLabel = if (role.canEditVocab()) "إضافة أول مفردة" else null,
                    onAction = if (role.canEditVocab()) onAddWord else null,
                    secondaryActionLabel = if (role.canEditVocab()) "تحميل عينة تعليمية تجريبية" else null,
                    onSecondaryAction = if (role.canEditVocab()) onLoadStarterPack else null
                )
            }
        } else {
            item {
                Text("المفردات المضافة حديثاً", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(recentlyAdded, key = { "add_${it.id}" }) { word ->
                DashboardWordRow(word = word, onClick = { onOpenWord(word.id) })
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("آخر التعديلات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(recentlyUpdated, key = { "upd_${it.id}" }) { word ->
                DashboardWordRow(word = word, onClick = { onOpenWord(word.id) })
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                icon,
                contentDescription = title,
                tint = if (isAlert) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardWordRow(word: WordEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VocabIdBadge(word.humanId)
                    LtrText(
                        text = word.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${PartOfSpeech.safeValueOf(word.partOfSpeech).arabicLabel} • ${word.primaryMeaning}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SyncEntityChip(word.syncStatus)
        }
    }
}
