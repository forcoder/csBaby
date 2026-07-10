package com.csbaby.kefu.presentation.screens.profile

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import android.util.Log
import com.csbaby.kefu.data.model.BackupRecord
import com.csbaby.kefu.data.model.BackupStatus
import com.csbaby.kefu.data.sync.SyncState
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据同步设置卡片。
 * 添加到 ProfileScreen 中，提供登录/注册/同步/登出功能。
 */
@Composable
fun SyncSettingsCard(
    syncState: SyncState,
    isLoggedIn: Boolean,
    currentTenantId: String?,
    pendingSyncCount: Int,
    lastSyncTime: Long,
    syncStats: String = "",
    dataStats: String = "",
    // 备份相关参数
    backupStatus: BackupStatus = BackupStatus.IDLE,
    backupMessage: String = "",
    backupRecords: List<BackupRecord> = emptyList(),
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String, displayName: String) -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    // 备份相关回调
    onUploadBackup: () -> Unit = {},
    onFetchBackupList: () -> Unit = {},
    onRestoreBackup: (Int) -> Unit = {},
    onDeleteBackup: (Int) -> Unit = {},
    onClearBackupStatus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showLoginDialog by remember { mutableStateOf(false) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var backupExpanded by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }

    // 同步状态变化提示（由外部 snackbarHostState 处理）
    // 内联状态指示器保留，用于卡片内的即时视觉反馈

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "云端同步",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoggedIn) {
                // 已登录状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "已登录",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        currentTenantId?.let { tenantId ->
                            Text(
                                text = "租户: $tenantId",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 上次同步时间
                        if (lastSyncTime > 0) {
                            Text(
                                text = "上次同步: ${formatSyncTime(lastSyncTime)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 同步状态指示器
                    when (syncState) {
                        is SyncState.Syncing -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = syncState.message,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        is SyncState.Error -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        is SyncState.Success -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        else -> {}
                    }
                }

                // 待同步数据提示
                if (pendingSyncCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📤 $pendingSyncCount 条数据待同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                // 数据统计信息（未登录时显示本地统计，已登录且同步后显示同步统计）
                if (!isLoggedIn && dataStats.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "本地数据：$dataStats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 同步统计信息（同步成功后显示）
                if (syncState is SyncState.Success && syncStats.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✓ $syncStats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSync,
                        modifier = Modifier.weight(1f),
                        enabled = syncState !is SyncState.Syncing
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("立即同步")
                    }

                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("登出")
                    }
                }

                // 备份操作区域
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { backupExpanded = !backupExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "备份操作",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (backupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (backupExpanded) "收起" else "展开"
                    )
                }

                AnimatedVisibility(visible = backupExpanded) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        // 备份状态显示
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

                            // 防御性检查：确保 backupRecords 不为 null
                            val safeRecords = backupRecords ?: emptyList()
                            safeRecords.take(3).forEach { record ->
                                BackupRecordItem(
                                    record = record,
                                    isOperating = backupStatus != BackupStatus.IDLE,
                                    onRestore = { showRestoreConfirm = record.id },
                                    onDelete = { showDeleteConfirm = record.id }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (safeRecords.size > 3) {
                                Text(
                                    text = "还有 ${backupRecords.size - 3} 条备份...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (backupExpanded) {
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
                            TextButton(onClick = onClearBackupStatus) {
                                Text("清除")
                            }
                        }
                    }
                }
            } else {
                // 未登录状态
                Text(
                    text = "登录后可在多设备间同步知识库、配置和风格数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showLoginDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("登录")
                    }

                    OutlinedButton(
                        onClick = { showRegisterDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("注册")
                    }
                }
            }

            // 错误信息
            if (syncState is SyncState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = syncState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // 登录对话框
    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onLogin = { email, password ->
                Log.d("SyncSettingsCard", "登录按钮点击: email=$email")
                onLogin(email, password)
                showLoginDialog = false
            }
        )
    }

    // 注册对话框
    if (showRegisterDialog) {
        RegisterDialog(
            onDismiss = { showRegisterDialog = false },
            onRegister = { email, password, displayName ->
                Log.d("SyncSettingsCard", "注册按钮点击: email=$email")
                onRegister(email, password, displayName)
                showRegisterDialog = false
            }
        )
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

private fun formatSyncTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 604_800_000 -> "${diff / 86_400_000} 天前"
        else -> {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}

@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // 自动识别输入: 含 '@' 当作邮箱, 全数字当手机号 (中国大陆 11 位)
    fun isValidIdentifier(text: String): Boolean {
        val t = text.trim()
        return t.contains('@') || t.matches(Regex("^\\d{11}$"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录") },
        text = {
            Column {
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("邮箱 / 手机号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            when {
                                identifier.isBlank() -> "邮箱或 11 位手机号"
                                identifier.contains('@') -> "邮箱登录"
                                identifier.matches(Regex("^\\d{11}$")) -> "手机号登录"
                                else -> "格式无效"
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("至少 6 位") }
                )
            }
        },
        // 按钮顺序反转: 登录按钮放左边, 取消放右边(用户偏好)
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        dismissButton = {
            Button(
                onClick = { onLogin(identifier.trim(), password) },
                enabled = isValidIdentifier(identifier) && password.length >= 6
            ) {
                Text("登录")
            }
        }
    )
}

@Composable
fun RegisterDialog(
    onDismiss: () -> Unit,
    onRegister: (String, String, String) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    fun isValidIdentifier(text: String): Boolean {
        val t = text.trim()
        return t.contains('@') || t.matches(Regex("^\\d{11}$"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("注册") },
        text = {
            Column {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("邮箱 / 手机号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            when {
                                identifier.isBlank() -> "邮箱或 11 位手机号"
                                identifier.contains('@') -> "邮箱注册"
                                identifier.matches(Regex("^\\d{11}$")) -> "手机号注册"
                                else -> "格式无效"
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("至少 6 位") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        dismissButton = {
            Button(
                onClick = { onRegister(identifier.trim(), password, displayName) },
                enabled = isValidIdentifier(identifier) && password.length >= 6 && displayName.trim().isNotBlank()
            ) {
                Text("注册")
            }
        }
    )
}

// ========== 备份相关组件 ==========

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
                    text = formatBackupSize(record.dataSize),
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

private fun formatBackupSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
