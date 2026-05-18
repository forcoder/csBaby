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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.csbaby.kefu.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_profile)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Style Learning Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.style_learning),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Learning Progress
                    Text(
                        text = "学习样本: ${uiState.learningSamples}个",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (uiState.learningSamples > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = uiState.accuracyScore,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "准确率: ${(uiState.accuracyScore * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Style Sliders
                    StyleSlider(
                        label = stringResource(R.string.formality),
                        value = uiState.formalityLevel,
                        onValueChange = { viewModel.updateFormality(it) }
                    )

                    StyleSlider(
                        label = stringResource(R.string.enthusiasm),
                        value = uiState.enthusiasmLevel,
                        onValueChange = { viewModel.updateEnthusiasm(it) }
                    )

                    StyleSlider(
                        label = stringResource(R.string.professionalism),
                        value = uiState.professionalismLevel,
                        onValueChange = { viewModel.updateProfessionalism(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Settings Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("启用风格学习")
                        Switch(
                            checked = uiState.styleLearningEnabled,
                            onCheckedChange = { viewModel.toggleStyleLearning(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自动发送")
                        Switch(
                            checked = uiState.autoSendEnabled,
                            onCheckedChange = { viewModel.toggleAutoSend(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cloud Sync Card
            SyncSettingsCard(
                syncState = uiState.syncState,
                isLoggedIn = uiState.isLoggedIn,
                currentTenantId = uiState.currentTenantId,
                pendingSyncCount = uiState.pendingSyncCount,
                lastSyncTime = uiState.lastSyncTime,
                onLogin = { email, password -> viewModel.login(email, password) },
                onRegister = { email, password, name -> viewModel.register(email, password, name) },
                onSync = { viewModel.syncNow() },
                onLogout = { viewModel.logout() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // OTA Update Card
            OtaUpdateCard(
                viewModel = viewModel,
                uiState = uiState
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Common Phrases
            if (uiState.commonPhrases.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "常用短语",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        uiState.commonPhrases.take(5).forEach { phrase ->
                            Text(
                                text = "• $phrase",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StyleSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label)
            Text(text = "${(value * 100).toInt()}%")
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtaUpdateCard(
    viewModel: ProfileViewModel,
    uiState: ProfileUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.availableUpdate != null) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "应用更新",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "当前版本: ${viewModel.getCurrentVersion()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Status Chip
                AssistChip(
                    onClick = { },
                    label = { Text(uiState.updateStatus) },
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Update Info
            if (uiState.availableUpdate != null) {
                val update = uiState.availableUpdate
                
                Column {
                    // Version Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Release Notes
                    if (update.releaseNotes.isNotBlank()) {
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
                    
                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (uiState.updateStatus.contains("下载中")) {
                            OutlinedButton(
                                onClick = { viewModel.cancelDownload() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("取消下载")
                            }
                        } else if (uiState.updateStatus.contains("下载完成")) {
                            Button(
                                onClick = { /* 安装逻辑会在下载完成后自动触发 */ },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.InstallDesktop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("已下载，点击安装")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startDownloadUpdate() },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.updateStatus.contains("检查")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("下载更新")
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.checkForUpdate() },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.updateStatus.contains("检查") && 
                                        !uiState.updateStatus.contains("下载")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("检查更新")
                            }
                        }
                    }
                }
            } else {
                // No update available
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "已是最新版本",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "您的应用已是最新版本，无需更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { viewModel.checkForUpdate() },
                        enabled = !uiState.updateStatus.contains("检查")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("检查更新")
                    }
                }
            }
            
            // Error Message
            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "错误: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
