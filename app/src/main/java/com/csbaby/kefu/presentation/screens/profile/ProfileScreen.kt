package com.csbaby.kefu.presentation.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.csbaby.kefu.R
import com.csbaby.kefu.data.remote.UserAuthManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.csbaby.kefu.presentation.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToBlacklist: () -> Unit = {},
    userAuthManager: UserAuthManager = androidx.lifecycle.viewmodel.compose.viewModel { throw IllegalStateException("Should be injected") }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 主题设置
            ThemeSelectionCard(
                currentTheme = uiState.themeMode,
                onThemeChange = { viewModel.updateThemeMode(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 设置选项
            SettingsCard(
                uiState = uiState,
                onStyleLearningToggle = { viewModel.toggleStyleLearning(it) },
                onAutoSendToggle = { viewModel.toggleAutoSend(it) },
                onNavigateToBlacklist = onNavigateToBlacklist
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 风格学习
            StyleLearningCard(
                uiState = uiState,
                onFormalityChange = { viewModel.updateFormality(it) },
                onEnthusiasmChange = { viewModel.updateEnthusiasm(it) },
                onProfessionalismChange = { viewModel.updateProfessionalism(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 备份与恢复
            BackupRestoreCard(
                viewModel = viewModel,
                uiState = uiState
            )

            Spacer(modifier = Modifier.height(12.dp))

            // OTA 更新
            OtaUpdateCard(
                viewModel = viewModel,
                uiState = uiState
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Logout button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.launchLogoutDialog()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "退出登录")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("退出登录")
                }
            }

            if (uiState.showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.hideLogoutDialog() },
                    title = { Text("确认退出") },
                    text = { Text("确定要退出登录吗？退出后需要重新登录才能使用。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.hideLogoutDialog()
                                // Perform logout
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    userAuthManager.logout()
                                }
                            }
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.hideLogoutDialog() }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelectionCard(
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "主题设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.values().forEach { mode ->
                    FilterChip(
                        selected = currentTheme == mode.value,
                        onClick = { onThemeChange(mode.value) },
                        label = { Text(mode.displayName) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCard(
    uiState: ProfileUiState,
    onStyleLearningToggle: (Boolean) -> Unit,
    onAutoSendToggle: (Boolean) -> Unit,
    onNavigateToBlacklist: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            SettingsItem(
                icon = Icons.Default.AutoAwesome,
                title = "启用风格学习",
                subtitle = "根据历史回复学习你的风格",
                checked = uiState.styleLearningEnabled,
                onCheckedChange = onStyleLearningToggle
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsItem(
                icon = Icons.Default.Send,
                title = "自动发送",
                subtitle = "自动发送生成的回复",
                checked = uiState.autoSendEnabled,
                onCheckedChange = onAutoSendToggle
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsItemWithArrow(
                icon = Icons.Default.Block,
                title = "消息黑名单",
                subtitle = "管理需要过滤的消息",
                onClick = onNavigateToBlacklist
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsItemWithArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StyleLearningCard(
    uiState: ProfileUiState,
    onFormalityChange: (Float) -> Unit,
    onEnthusiasmChange: (Float) -> Unit,
    onProfessionalismChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "风格学习",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "${uiState.learningSamples} 个样本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.learningSamples > 0) {
                        Text(
                            text = "准确率 ${(uiState.accuracyScore * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (uiState.learningSamples > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = uiState.accuracyScore,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            StyleSlider(
                label = "正式度",
                value = uiState.formalityLevel,
                onValueChange = onFormalityChange
            )

            StyleSlider(
                label = "热情度",
                value = uiState.enthusiasmLevel,
                onValueChange = onEnthusiasmChange
            )

            StyleSlider(
                label = "专业度",
                value = uiState.professionalismLevel,
                onValueChange = onProfessionalismChange
            )
        }
    }
}

@Composable
private fun StyleSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreCard(
    viewModel: ProfileViewModel,
    uiState: ProfileUiState
) {
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) viewModel.createLocalBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.restoreFromLocalBackup(uri)
    }

    // Show backup/restore messages via snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.backupMessage) {
        val msg = uiState.backupMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBackupMessage()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "备份与恢复",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "备份知识库规则、黑名单和模型配置，支持本地和远程两种方式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.lastBackupTime.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "上次备份: ${uiState.lastBackupTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Local backup/restore
            Text(
                text = "本地备份",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { backupLauncher.launch("csbaby_backup.json") },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isBackingUp && !uiState.isRestoring
                ) {
                    if (uiState.isBackingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(if (uiState.isBackingUp) "备份中..." else "备份到本地")
                }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isBackingUp && !uiState.isRestoring
                ) {
                    if (uiState.isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(if (uiState.isRestoring) "恢复中..." else "从本地恢复")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))

            // Remote backup/restore
            Text(
                text = "远程备份",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.createRemoteBackup() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isBackingUp && !uiState.isRestoring
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("上传到云端")
                }
                OutlinedButton(
                    onClick = { viewModel.restoreFromRemoteBackup() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isBackingUp && !uiState.isRestoring
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("从云端恢复")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtaUpdateCard(
    viewModel: ProfileViewModel,
    uiState: ProfileUiState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.availableUpdate != null) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "应用更新",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "当前版本: ${viewModel.getCurrentVersion()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                AssistChip(
                    onClick = { },
                    label = { 
                        Text(
                            uiState.updateStatus,
                            style = MaterialTheme.typography.labelSmall
                        ) 
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when {
                            uiState.updateStatus.contains("失败") -> MaterialTheme.colorScheme.errorContainer
                            uiState.updateStatus.contains("成功") -> MaterialTheme.colorScheme.primaryContainer
                            uiState.updateStatus.contains("下载") -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                )
            }
            
            if (uiState.availableUpdate != null) {
                val update = uiState.availableUpdate
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "新版本: v${update.versionName}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (update.isForceUpdate) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) {
                                Text("强制更新", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "大小: ${update.fileSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (update.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "更新内容:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = update.releaseNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (uiState.updateStatus.contains("下载中")) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = uiState.downloadProgress,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${(uiState.downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.cancelDownload() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "取消下载")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("取消下载")
                            }
                        }
                    } else if (uiState.updateStatus.contains("下载完成")) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.installUpdate() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.InstallDesktop, contentDescription = "安装更新")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("点击安装")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startDownloadUpdate() },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.updateStatus.contains("检查"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "下载更新")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("下载更新")
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.checkForUpdate() },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.updateStatus.contains("检查") && 
                                        !uiState.updateStatus.contains("下载")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "检查更新")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("检查更新")
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "已是最新版本",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "无需更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.checkForUpdate() },
                        enabled = !uiState.updateStatus.contains("检查"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "检查更新")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("检查更新")
                    }
                }
            }
            
            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "错误: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
