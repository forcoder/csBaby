package com.csbaby.kefu.presentation.screens.blacklist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(
    viewModel: BlacklistViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.importBlacklists(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) viewModel.exportBlacklists(uri)
    }

    LaunchedEffect(uiState.noticeMessage) {
        val message = uiState.noticeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeNoticeMessage()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息黑名单") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        enabled = !uiState.isImporting && !uiState.isExporting
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "导入黑名单"
                        )
                    }
                    IconButton(
                        onClick = { exportLauncher.launch("blacklist.json") },
                        enabled = uiState.blacklists.isNotEmpty() && !uiState.isImporting && !uiState.isExporting
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "导出黑名单"
                        )
                    }
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = uiState.blacklists.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "清空黑名单"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加黑名单")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading || uiState.isImporting || uiState.isExporting) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            if (uiState.blacklists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "黑名单还是空的",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "添加关键词、发送者或消息内容到黑名单，\n这些消息将不会再被监听和处理。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.blacklists) { blacklist ->
                        BlacklistItem(
                            blacklist = blacklist,
                            onToggle = { viewModel.toggleBlacklist(blacklist.id, !blacklist.isEnabled) },
                            onDelete = { viewModel.removeBlacklist(blacklist.id) }
                        )
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddBlacklistDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { type, value, description, packageName ->
                viewModel.addBlacklist(type, value, description, packageName)
                showAddDialog = false
            }
        )
    }
    
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空黑名单") },
            text = { Text("将删除全部 ${uiState.blacklists.size} 条黑名单记录，确认继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAll()
                    }
                ) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun BlacklistItem(
    blacklist: MessageBlacklistEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (blacklist.type) {
                                MessageBlacklistEntity.TYPE_KEYWORD -> Icons.Default.Key
                                MessageBlacklistEntity.TYPE_SENDER -> Icons.Default.Person
                                MessageBlacklistEntity.TYPE_CONTENT -> Icons.Default.Message
                                else -> Icons.Default.Block
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when (blacklist.type) {
                                MessageBlacklistEntity.TYPE_KEYWORD -> "关键词"
                                MessageBlacklistEntity.TYPE_SENDER -> "发送者"
                                MessageBlacklistEntity.TYPE_CONTENT -> "完整内容"
                                else -> "未知"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Switch(
                    checked = blacklist.isEnabled,
                    onCheckedChange = { onToggle() }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = blacklist.value,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            if (blacklist.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = blacklist.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (blacklist.packageName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "来源应用: ${blacklist.packageName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlacklistDialog(
    onDismiss: () -> Unit,
    onAdd: (type: String, value: String, description: String, packageName: String?) -> Unit
) {
    var selectedType by remember { mutableStateOf(MessageBlacklistEntity.TYPE_KEYWORD) }
    var value by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加黑名单") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = when (selectedType) {
                            MessageBlacklistEntity.TYPE_KEYWORD -> "关键词"
                            MessageBlacklistEntity.TYPE_SENDER -> "发送者"
                            MessageBlacklistEntity.TYPE_CONTENT -> "完整内容"
                            else -> "未知"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("黑名单类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        listOf(
                            MessageBlacklistEntity.TYPE_KEYWORD to "关键词",
                            MessageBlacklistEntity.TYPE_SENDER to "发送者",
                            MessageBlacklistEntity.TYPE_CONTENT to "完整内容"
                        ).forEach { (type, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { 
                        Text(
                            when (selectedType) {
                                MessageBlacklistEntity.TYPE_KEYWORD -> "关键词"
                                MessageBlacklistEntity.TYPE_SENDER -> "发送者名称"
                                MessageBlacklistEntity.TYPE_CONTENT -> "消息内容"
                                else -> "值"
                            }
                        )
                    },
                    supportingText = {
                        Text(
                            when (selectedType) {
                                MessageBlacklistEntity.TYPE_KEYWORD -> "消息中包含此关键词时将被过滤"
                                MessageBlacklistEntity.TYPE_SENDER -> "来自此发送者的消息将被过滤"
                                MessageBlacklistEntity.TYPE_CONTENT -> "与此内容完全匹配的消息将被过滤"
                                else -> ""
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注说明（可选）") },
                    placeholder = { Text("添加一些说明，方便后续管理") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("来源应用（可选）") },
                    placeholder = { Text("留空表示所有应用") },
                    supportingText = { Text("指定只过滤来自特定应用的消息") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        selectedType,
                        value,
                        description,
                        packageName.ifBlank { null }
                    )
                },
                enabled = value.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
