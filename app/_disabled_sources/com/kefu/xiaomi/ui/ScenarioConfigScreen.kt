package com.kefu.xiaomi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefu.xiaomi.data.model.Scenario
import com.kefu.xiaomi.data.model.ScenarioType
import com.kefu.xiaomi.ui.viewmodel.ScenarioConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioConfigScreen(
    viewModel: ScenarioConfigViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("场景配置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
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
                Icon(Icons.Default.Add, contentDescription = "添加场景")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "场景用于限定关键词规则的适用范围。您可以创建全局场景或针对特定房源/产品的场景。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                items(uiState.scenarios, key = { it.id }) { scenario ->
                    ScenarioCard(
                        scenario = scenario,
                        onEdit = { viewModel.showEditDialog(scenario) },
                        onDelete = { viewModel.deleteScenario(scenario.id) }
                    )
                }
            }
        }
    }

    // 添加/编辑场景对话框
    if (uiState.showAddDialog) {
        AddEditScenarioDialog(
            scenario = uiState.editingScenario,
            onDismiss = { viewModel.hideDialog() },
            onSave = { viewModel.saveScenario(it) }
        )
    }
}

@Composable
private fun ScenarioCard(
    scenario: Scenario,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    Icon(
                        when (scenario.type) {
                            ScenarioType.ALL_PROPERTIES -> Icons.Default.Language
                            ScenarioType.SPECIFIC_PROPERTY -> Icons.Default.Home
                            ScenarioType.SPECIFIC_PRODUCT -> Icons.Default.Inventory
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = scenario.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (scenario.type) {
                                ScenarioType.ALL_PROPERTIES -> "全局场景"
                                ScenarioType.SPECIFIC_PROPERTY -> "特定房源"
                                ScenarioType.SPECIFIC_PRODUCT -> "特定产品"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (scenario.type != ScenarioType.ALL_PROPERTIES) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            scenario.description?.let { desc ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            scenario.targetId?.let { targetId ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID: $targetId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除场景 \"${scenario.name}\" 吗？") },
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
private fun AddEditScenarioDialog(
    scenario: Scenario?,
    onDismiss: () -> Unit,
    onSave: (Scenario) -> Unit
) {
    var name by remember { mutableStateOf(scenario?.name ?: "") }
    var description by remember { mutableStateOf(scenario?.description ?: "") }
    var targetId by remember { mutableStateOf(scenario?.targetId ?: "") }
    var selectedType by remember { mutableStateOf(scenario?.type ?: ScenarioType.SPECIFIC_PROPERTY) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (scenario == null) "添加场景" else "编辑场景") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("场景名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (selectedType) {
                            ScenarioType.ALL_PROPERTIES -> "全局场景"
                            ScenarioType.SPECIFIC_PROPERTY -> "特定房源"
                            ScenarioType.SPECIFIC_PRODUCT -> "特定产品"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("场景类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        ScenarioType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (type) {
                                            ScenarioType.ALL_PROPERTIES -> "全局场景"
                                            ScenarioType.SPECIFIC_PROPERTY -> "特定房源"
                                            ScenarioType.SPECIFIC_PRODUCT -> "特定产品"
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

                if (selectedType != ScenarioType.ALL_PROPERTIES) {
                    OutlinedTextField(
                        value = targetId,
                        onValueChange = { targetId = it },
                        label = { Text(if (selectedType == ScenarioType.SPECIFIC_PROPERTY) "房源ID" else "产品ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("场景描述") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            Scenario(
                                id = scenario?.id ?: 0,
                                name = name,
                                type = selectedType,
                                targetId = if (selectedType != ScenarioType.ALL_PROPERTIES) targetId else null,
                                description = description.ifBlank { null }
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
