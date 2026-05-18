package com.kefu.xiaomi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefu.xiaomi.data.model.AIModelConfig
import com.kefu.xiaomi.data.model.ModelType
import com.kefu.xiaomi.ui.viewmodel.ModelConfigViewModel
import com.kefu.xiaomi.ui.viewmodel.TestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    viewModel: ModelConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加模型")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 费用统计卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "本月费用",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "¥${uiState.models.sumOf { it.monthlyCost }.format(2)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                    )
                }
            }

            // 模型列表
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.models, key = { it.id }) { model ->
                        ModelCard(
                            model = model,
                            onEdit = { viewModel.showEditDialog(model) },
                            onDelete = { viewModel.deleteModel(model.id) },
                            onToggleEnabled = { viewModel.toggleModelEnabled(model.id) },
                            onSetDefault = { viewModel.setDefaultModel(model.id) },
                            onTestConnection = { viewModel.testConnection(model) }
                        )
                    }
                }
            }
        }
    }

    // 添加/编辑模型对话框
    if (uiState.showAddDialog) {
        AddEditModelDialog(
            model = uiState.editingModel,
            onDismiss = { viewModel.hideDialog() },
            onSave = { viewModel.saveModel(it) },
            onTest = { viewModel.testConnection(it) }
        )
    }

    // 测试结果对话框
    uiState.testResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearTestResult() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (result) {
                        is TestResult.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        is TestResult.Success -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        is TestResult.Error -> Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (result) {
                            is TestResult.Loading -> "测试连接中..."
                            is TestResult.Success -> "连接成功"
                            is TestResult.Error -> "连接失败"
                        }
                    )
                }
            },
            text = {
                Text(
                    when (result) {
                        is TestResult.Loading -> "正在测试API连接，请稍候..."
                        is TestResult.Success -> result.message
                        is TestResult.Error -> result.message
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearTestResult() }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
private fun ModelCard(
    model: AIModelConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
    onSetDefault: () -> Unit,
    onTestConnection: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (model.isEnabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                when (model.modelType) {
                                    ModelType.OPENAI -> MaterialTheme.colorScheme.primaryContainer
                                    ModelType.CLAUDE -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (model.modelType) {
                                ModelType.OPENAI -> Icons.Default.Psychology
                                ModelType.CLAUDE -> Icons.Default.Psychology
                                ModelType.ZHIPU -> Icons.Default.AutoAwesome
                                ModelType.TONGYI -> Icons.Default.Cloud
                                ModelType.CUSTOM -> Icons.Default.Settings
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = model.modelName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (model.isDefault) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "默认",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                        Text(
                            text = when (model.modelType) {
                                ModelType.OPENAI -> "OpenAI"
                                ModelType.CLAUDE -> "Claude"
                                ModelType.ZHIPU -> "智谱AI"
                                ModelType.TONGYI -> "通义千问"
                                ModelType.CUSTOM -> "自定义"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = model.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 参数信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "温度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = model.temperature.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "最大Token",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = model.maxTokens.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "本月费用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "¥${model.monthlyCost.format(2)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onTestConnection) {
                    Icon(
                        Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("测试")
                }
                if (!model.isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("设为默认")
                    }
                }
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除模型 \"${model.modelName}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditModelDialog(
    model: AIModelConfig?,
    onDismiss: () -> Unit,
    onSave: (AIModelConfig) -> Unit,
    onTest: (AIModelConfig) -> Unit
) {
    var modelName by remember { mutableStateOf(model?.modelName ?: "") }
    var apiKey by remember { mutableStateOf(model?.apiKey ?: "") }
    var apiEndpoint by remember { mutableStateOf(model?.apiEndpoint ?: "") }
    var temperature by remember { mutableStateOf(model?.temperature ?: 0.7f) }
    var maxTokens by remember { mutableStateOf(model?.maxTokens?.toFloat() ?: 1000f) }
    var isDefault by remember { mutableStateOf(model?.isDefault ?: false) }
    var selectedType by remember { mutableStateOf(model?.modelType ?: ModelType.OPENAI) }
    var typeExpanded by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (model == null) "添加模型" else "编辑模型") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (selectedType) {
                            ModelType.OPENAI -> "OpenAI"
                            ModelType.CLAUDE -> "Claude"
                            ModelType.ZHIPU -> "智谱AI"
                            ModelType.TONGYI -> "通义千问"
                            ModelType.CUSTOM -> "自定义"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("模型类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        ModelType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (type) {
                                            ModelType.OPENAI -> "OpenAI"
                                            ModelType.CLAUDE -> "Claude"
                                            ModelType.ZHIPU -> "智谱AI"
                                            ModelType.TONGYI -> "通义千问"
                                            ModelType.CUSTOM -> "自定义"
                                        }
                                    )
                                },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("如: GPT-4, Claude-3") }
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API密钥") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "隐藏" else "显示"
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = apiEndpoint,
                    onValueChange = { apiEndpoint = it },
                    label = { Text("API端点") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("如: https://api.openai.com/v1") }
                )

                Column {
                    Text(
                        text = "温度 (Creativity): ${temperature.format(1)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f
                    )
                }

                Column {
                    Text(
                        text = "最大Token: ${maxTokens.toInt()}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        valueRange = 100f..4000f,
                        steps = 38
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("设为默认模型", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it }
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        if (modelName.isNotBlank() && apiKey.isNotBlank()) {
                            onTest(
                                AIModelConfig(
                                    id = model?.id ?: 0,
                                    modelType = selectedType,
                                    modelName = modelName,
                                    apiKey = apiKey,
                                    apiEndpoint = apiEndpoint,
                                    temperature = temperature,
                                    maxTokens = maxTokens.toInt(),
                                    isDefault = isDefault,
                                    isEnabled = model?.isEnabled ?: true
                                )
                            )
                        }
                    },
                    enabled = modelName.isNotBlank() && apiKey.isNotBlank()
                ) {
                    Text("测试")
                }
                Button(
                    onClick = {
                        if (modelName.isNotBlank() && apiKey.isNotBlank()) {
                            onSave(
                                AIModelConfig(
                                    id = model?.id ?: 0,
                                    modelType = selectedType,
                                    modelName = modelName,
                                    apiKey = apiKey,
                                    apiEndpoint = apiEndpoint,
                                    temperature = temperature,
                                    maxTokens = maxTokens.toInt(),
                                    isDefault = isDefault,
                                    isEnabled = model?.isEnabled ?: true
                                )
                            )
                        }
                    },
                    enabled = modelName.isNotBlank() && apiKey.isNotBlank()
                ) {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)
private fun Float.format(digits: Int) = "%.${digits}f".format(this)
