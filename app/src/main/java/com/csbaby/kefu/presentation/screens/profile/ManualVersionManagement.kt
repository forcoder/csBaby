package com.csbaby.kefu.presentation.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 手动版本管理卡片
 * 允许用户手动上传APK文件到阿里云OSS进行版本管理
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualVersionManagementCard(
    viewModel: ProfileViewModel,
    uiState: ProfileUiState
) {
    val context = LocalContext.current
    var showUploadDialog by remember { mutableStateOf(false) }
    var showVersionList by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<Uri?>(null) }
    
    // Upload dialog state
    var uploadVersionCode by remember { mutableStateOf("2") }
    var uploadVersionName by remember { mutableStateOf("1.1.0") }
    var uploadReleaseNotes by remember { mutableStateOf("") }
    var uploadForceUpdate by remember { mutableStateOf(false) }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            selectedFile = uri
            showUploadDialog = true
        }
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "手动版本管理 (阿里云OSS)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                    Text(
                        text = "支持手动上传APK到阿里云OSS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "阿里云OSS",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // OSS配置信息
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "阿里云OSS配置:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bucket: apk-ota",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "区域: oss-cn-shenzhen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "域名: apk-ota.oss-cn-shenzhen.aliyuncs.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "状态: ${if (uiState.ossConfigValid) "已配置" else "未配置"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.ossConfigValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { filePickerLauncher.launch("application/vnd.android.package-archive") },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.ossConfigValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "上传APK")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("上传APK")
                }
                
                OutlinedButton(
                    onClick = { showVersionList = true },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.ossConfigValid
                ) {
                    Icon(Icons.Default.List, contentDescription = "版本列表")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("版本列表")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.checkOssUpdate() },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.ossConfigValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "检查OSS更新")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("检查OSS更新")
                }
                
                OutlinedButton(
                    onClick = { viewModel.validateOssConfig() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "验证配置")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("验证配置")
                }
            }
            
            // Upload Status
            if (uiState.uploadStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            uiState.uploadStatus.contains("成功") -> Color(0xFFE8F5E8)
                            uiState.uploadStatus.contains("失败") -> Color(0xFFFDE8E8)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                uiState.uploadStatus.contains("成功") -> Icons.Default.CheckCircle
                                uiState.uploadStatus.contains("失败") -> Icons.Default.Error
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = when {
                                uiState.uploadStatus.contains("成功") -> Color(0xFF2E7D32)
                                uiState.uploadStatus.contains("失败") -> Color(0xFFD32F2F)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.uploadStatus,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
    
    // Upload Dialog
    if (showUploadDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("上传APK到阿里云OSS") },
            text = {
                Column {
                    Text("已选择文件: ${selectedFile?.toString()?.substringAfterLast("/")}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("请填写版本信息:")
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = uploadVersionCode,
                        onValueChange = { uploadVersionCode = it },
                        label = { Text("版本号 (versionCode)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uploadVersionName,
                        onValueChange = { uploadVersionName = it },
                        label = { Text("版本名称 (versionName)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uploadReleaseNotes,
                        onValueChange = { uploadReleaseNotes = it },
                        label = { Text("更新说明 (可选)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = uploadForceUpdate,
                            onCheckedChange = { uploadForceUpdate = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("强制更新")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = selectedFile
                        if (file == null) return@Button
                        try {
                            val code = uploadVersionCode.toIntOrNull() ?: 2
                            viewModel.uploadToOss(
                                uri = file,
                                versionCode = code,
                                versionName = uploadVersionName,
                                releaseNotes = uploadReleaseNotes,
                                isForceUpdate = uploadForceUpdate
                            )
                            showUploadDialog = false
                        } catch (e: Exception) {
                            // 处理错误
                        }
                    }
                ) {
                    Text("开始上传")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUploadDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    // Version List Dialog
    if (showVersionList) {
        AlertDialog(
            onDismissRequest = { showVersionList = false },
            title = { Text("阿里云OSS版本列表") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (uiState.ossVersionList.isEmpty()) {
                        Text(
                            text = "暂无版本信息",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.ossVersionList.forEach { version ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "v${version.versionName} (${version.versionCode})",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (version.isForceUpdate) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error
                                            ) {
                                                Text("强制", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "上传者: ${version.uploader}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "上传时间: ${version.uploadTime}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "文件大小: ${version.fileSize / (1024 * 1024)} MB",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "下载次数: ${version.downloadCount}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row {
                                        OutlinedButton(
                                            onClick = {
                                                // 下载该版本
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("下载")
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                // 设为强制更新
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(if (version.isForceUpdate) "取消强制" else "设强制")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showVersionList = false }
                ) {
                    Text("关闭")
                }
            }
        )
    }
}