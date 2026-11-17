package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.SyncState
import com.example.model.WorkspaceRole
import com.example.tts.PronunciationManager
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LtrText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    useMono: Boolean = false
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = style.copy(
                fontFamily = if (useMono) JetBrainsMonoFontFamily else PlusJakartaSansFontFamily,
                textDirection = TextDirection.Ltr
            )
        )
    }
}

@Composable
fun VocabIdBadge(humanId: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        LtrText(
            text = humanId,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            useMono = true
        )
    }
}

@Composable
fun RoleBadge(role: WorkspaceRole, modifier: Modifier = Modifier) {
    val (bg, fg) = when (role) {
        WorkspaceRole.OWNER -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        WorkspaceRole.EDITOR -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        WorkspaceRole.REVIEWER -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        WorkspaceRole.VIEWER -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = role.arabicLabel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SyncEntityChip(syncStatus: String, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (syncStatus) {
        "SYNCED" -> Triple("متزامن", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        "CONFLICT" -> Triple("تعارض", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        "ERROR" -> Triple("خطأ", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Triple("معلّق محلياً", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

@Composable
fun SyncStatusBanner(
    syncState: SyncState,
    lastSyncTimestamp: Long,
    pendingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, bg, fg) = when (syncState) {
        SyncState.ONLINE_SYNCED -> Triple(Icons.Default.CloudDone, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        SyncState.SYNCHRONIZING -> Triple(Icons.Default.CloudSync, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        SyncState.OFFLINE -> Triple(Icons.Default.CloudOff, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        SyncState.PENDING_CHANGES -> Triple(Icons.Default.CloudQueue, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        SyncState.SYNC_ERROR -> Triple(Icons.Default.ErrorOutline, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        SyncState.CONFLICT_REQUIRES_REVIEW -> Triple(Icons.Default.WarningAmber, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }

    val lastTimeLabel = remember(lastSyncTimestamp) {
        if (lastSyncTimestamp <= 0L) "لم تتم المزامنة السحابية بعد"
        else "آخر مزامنة: ${SimpleDateFormat("HH:mm • dd/MM", Locale("ar")).format(Date(lastSyncTimestamp))}"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("sync_status_banner"),
        color = bg,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = syncState.arabicLabel, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (pendingCount > 0 && syncState != SyncState.ONLINE_SYNCED) {
                        "${syncState.arabicLabel} ($pendingCount معلّق)"
                    } else {
                        syncState.arabicLabel
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = lastTimeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.85f)
                )
            }
            Text(
                text = "التفاصيل",
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PronunciationBar(
    wordText: String,
    phoneticText: String,
    pronunciationManager: PronunciationManager,
    preferredAccent: String,
    preferredVoiceName: String,
    speed: Float,
    onSettingsChanged: (Float, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isReady by pronunciationManager.isReady.collectAsStateWithLifecycle()
    val isSpeaking by pronunciationManager.isSpeaking.collectAsStateWithLifecycle()
    val voices by pronunciationManager.availableVoices.collectAsStateWithLifecycle()
    val hasUs by pronunciationManager.hasUsVoice.collectAsStateWithLifecycle()
    val hasUk by pronunciationManager.hasUkVoice.collectAsStateWithLifecycle()
    val statusMsg by pronunciationManager.statusMessage.collectAsStateWithLifecycle()
    var showVoicesMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("النطق الصوتي والنسخ الصوتي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (phoneticText.isNotBlank()) {
                        LtrText(
                            text = phoneticText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            useMono = true
                        )
                    } else {
                        Text(
                            text = "لا يوجد رمز صوتي مضاف — اضغط تشغيل للاستماع",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = {
                        if (isSpeaking) pronunciationManager.stop()
                        else pronunciationManager.speak(wordText, preferredAccent, preferredVoiceName, speed)
                    },
                    enabled = isReady,
                    modifier = Modifier.testTag("play_pronunciation_button")
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "تشغيل النطق"
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSpeaking) "إيقاف" else "نطق")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = preferredAccent == "US",
                    onClick = {
                        onSettingsChanged(speed, "US", "")
                        if (isReady) pronunciationManager.speak(wordText, "US", "", speed)
                    },
                    label = { Text(if (hasUs) "أمريكي (US)" else "أمريكي (غير مثبت)") },
                    enabled = isReady
                )
                FilterChip(
                    selected = preferredAccent == "UK",
                    onClick = {
                        onSettingsChanged(speed, "UK", "")
                        if (isReady) pronunciationManager.speak(wordText, "UK", "", speed)
                    },
                    label = { Text(if (hasUk) "بريطاني (UK)" else "بريطاني (غير مثبت)") },
                    enabled = isReady
                )
                if (voices.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { showVoicesMenu = true }) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("الأصوات المتاحة (${voices.size})", style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded = showVoicesMenu, onDismissRequest = { showVoicesMenu = false }) {
                            voices.take(12).forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.displayLabel) },
                                    onClick = {
                                        showVoicesMenu = false
                                        val acc = if (voice.localeTag.contains("GB", true)) "UK" else "US"
                                        onSettingsChanged(speed, acc, voice.name)
                                        pronunciationManager.speak(wordText, acc, voice.name, speed)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("السرعة: ${String.format(Locale.US, "%.2fx", speed)}", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(10.dp))
                Slider(
                    value = speed,
                    onValueChange = { onSettingsChanged(it, preferredAccent, preferredVoiceName) },
                    valueRange = 0.5f..1.75f,
                    modifier = Modifier.weight(1f)
                )
            }

            if (statusMsg != null) {
                Text(
                    text = statusMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_library),
            contentDescription = title,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction, modifier = Modifier.testTag("empty_primary_action")) {
                Text(actionLabel)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.testTag("empty_secondary_action")) {
                Text(secondaryActionLabel)
            }
        }
    }
}

@Composable
fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmText: String = "تأكيد الحذف",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
