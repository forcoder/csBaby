package com.csbaby.kefu.presentation.screens.profile

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.csbaby.kefu.data.model.BackupRecord
import com.csbaby.kefu.data.model.BackupStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据备份与恢复卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupCard(
    backupStatus: BackupStatus,
    backupMessage: String,
    backupRecords: List<BackupRecord>,
    onUploadBackup: () -> Unit,
    onFetchBackupList: () -> Unit,
    onRestoreBackup: (Int) -> Unit,
    onDeleteBackup: (Int) -> Unit,
    onClearStatus: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
    var showRestoreConfirm by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "数据备份与恢复",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "将数据备份到云端或从云端恢复",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // 状态显示
                    if (backupStatus != BackupStatus.IDLE) {
                        BackupStatusChip(status = backupStatus, message = backupMessage)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onUploadBackup,
                            modifier = Modifier.weight(1f),
                            enabled = backupStatus == BackupStatus.IDLE
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("备份到云端", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = onFetchBackupList,
                            modifier = Modifier.weight(1f),
                            enabled = backupStatus == BackupStatus.IDLE
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("刷新列表", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    // 备份列表
                    if (backupRecords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "云端备份 (${backupRecords.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        backupRecords.forEach { record ->
                            BackupRecordItem(
                                record = record,
                                isOperating = backupStatus != BackupStatus.IDLE,
                                onRestore = { showRestoreConfirm = record.id },
                                onDelete = { showDeleteConfirm = record.id }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无云端备份，点击「备份到云端」开始备份",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 错误消息
                    if (backupStatus == BackupStatus.FAILED && backupMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = backupMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onClearStatus) {
                            Text("清除")
                        }
                    }
                }
            }
        }
    }

    // 恢复确认对话框
    showRestoreConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text("确认恢复") },
            text = { Text("恢复备份将覆盖当前本地数据，此操作不可撤销。确定要继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onRestoreBackup(id)
                    showRestoreConfirm = null
                }) {
                    Text("恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除确认对话框
    showDeleteConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除备份") },
            text = { Text("确定要删除这条云端备份吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteBackup(id)
                    showDeleteConfirm = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun BackupStatusChip(status: BackupStatus, message: String) {
    val (icon, color) = when (status) {
        BackupStatus.EXPORTING -> Icons.Default.Upload to MaterialTheme.colorScheme.secondary
        BackupStatus.UPLOADING -> Icons.Default.CloudUpload to MaterialTheme.colorScheme.primary
        BackupStatus.DOWNLOADING -> Icons.Default.CloudDownload to MaterialTheme.colorScheme.primary
        BackupStatus.RESTORING -> Icons.Default.Restore to MaterialTheme.colorScheme.secondary
        BackupStatus.SUCCESS -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        BackupStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
        else -> Icons.Default.CloudSync to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val displayText = when (status) {
        BackupStatus.EXPORTING -> "导出中..."
        BackupStatus.UPLOADING -> "上传中..."
        BackupStatus.DOWNLOADING -> "下载中..."
        BackupStatus.RESTORING -> "恢复中..."
        BackupStatus.SUCCESS -> "操作成功"
        BackupStatus.FAILED -> "操作失败"
        BackupStatus.IDLE -> ""
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == BackupStatus.EXPORTING || status == BackupStatus.UPLOADING ||
                status == BackupStatus.DOWNLOADING || status == BackupStatus.RESTORING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = color
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = message.ifBlank { displayText },
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

@Composable
private fun BackupRecordItem(
    record: BackupRecord,
    isOperating: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.deviceName.ifBlank { "未知设备" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = dateFormat.format(Date(record.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatSize(record.dataSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRestore,
                    enabled = !isOperating
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("恢复")
                }
                TextButton(
                    onClick = onDelete,
                    enabled = !isOperating
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
