package com.csbaby.kefu.presentation.screens.model

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.csbaby.kefu.R
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(
    viewModel: ModelViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<AIModelConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_config)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Model")
            }
        }
    ) { padding ->
        if (uiState.models.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("暂无 AI 模型", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点击右下角 + 添加模型", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = uiState.models, key = { it.id }) { model ->
                    ModelItem(
                        model = model,
                        testResult = uiState.testResults[model.id],
                        onEdit = { editingModel = model },
                        onDelete = { viewModel.deleteModel(model.id) },
                        onSetDefault = { viewModel.setDefaultModel(model.id) },
                        onTest = { viewModel.testConnection(model.id) }
                    )
                }
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog || editingModel != null) {
        ModelEditDialog(
            model = editingModel,
            onDismiss = {
                showAddDialog = false
                editingModel = null
            },
            onSave = { model ->
                viewModel.saveModel(model)
                showAddDialog = false
                editingModel = null
            }
        )
    }
}

@Composable
fun ModelItem(
    model: AIModelConfig,
    testResult: Boolean?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onTest: () -> Unit
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.modelName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (model.isDefault) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("默认") }
                            )
                        }
                        // Test result indicator
                        if (testResult != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (testResult) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = if (testResult) "测试成功" else "测试失败",
                                tint = if (testResult) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "${model.modelType.name} • ${model.apiEndpoint.take(30)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!model.isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Text("设为默认")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "温度: ${model.temperature}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "最大Token: ${model.maxTokens}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "本月费用: ¥${String.format("%.2f", model.monthlyCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onTest) {
                    Icon(Icons.Default.Settings, contentDescription = null)

                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (testResult == null) "测试" else "重新测试")
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEditDialog(
    model: AIModelConfig?,
    onDismiss: () -> Unit,
    onSave: (AIModelConfig) -> Unit
) {
    var modelName by remember { mutableStateOf(model?.modelName ?: "") }
    var modelType by remember { mutableStateOf(model?.modelType ?: ModelType.OPENAI) }
    var apiKey by remember { mutableStateOf(model?.apiKey ?: "") }
    var apiEndpoint by remember { mutableStateOf(model?.apiEndpoint ?: "") }
    var temperature by remember { mutableStateOf(model?.temperature?.toString() ?: "0.7") }
    var maxTokens by remember { mutableStateOf(model?.maxTokens?.toString() ?: "1000") }
    var isDefault by remember { mutableStateOf(model?.isDefault ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (model == null) "添加模型" else "编辑模型") },
        text = {
            Column {
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Model Type Dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = modelType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("模型类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ModelType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    modelType = type
                                    expanded = false
                                    // Auto-fill endpoint based on type
                                    if (model == null) {
                                        apiEndpoint = getDefaultEndpoint(type)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API密钥") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiEndpoint,
                    onValueChange = { apiEndpoint = it },
                    label = { Text("API地址") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    OutlinedTextField(
                        value = temperature,
                        onValueChange = { temperature = it },
                        label = { Text("温度") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        label = { Text("最大Token") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it }
                    )
                    Text("设为默认模型")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val temp = (temperature.toFloatOrNull() ?: 0.7f).coerceIn(0f, 2f)
                    val tokens = (maxTokens.toIntOrNull() ?: 1000).coerceIn(1, 32768)
                    val newModel = AIModelConfig(
                        id = model?.id ?: 0,
                        modelType = modelType,
                        modelName = modelName.trim(),
                        apiKey = apiKey,
                        apiEndpoint = apiEndpoint.trim(),
                        temperature = temp,
                        maxTokens = tokens,
                        isDefault = isDefault,
                        isEnabled = true
                    )
                    onSave(newModel)
                },
                enabled = modelName.trim().isNotBlank() && apiKey.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun getDefaultEndpoint(modelType: ModelType): String {
    return when (modelType) {
        ModelType.OPENAI -> "https://api.openai.com/v1/chat/completions"
        ModelType.CLAUDE -> "https://api.anthropic.com/v1/messages"
        ModelType.ZHIPU -> "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        ModelType.TONGYI -> "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
        ModelType.CUSTOM -> ""
    }
}
