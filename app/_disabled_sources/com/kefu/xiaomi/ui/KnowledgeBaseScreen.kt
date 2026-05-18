package com.kefu.xiaomi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefu.xiaomi.data.model.KeywordRule
import com.kefu.xiaomi.data.model.MatchType
import com.kefu.xiaomi.ui.viewmodel.KnowledgeBaseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen(
    viewModel: KnowledgeBaseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库") },
                actions = {
                    IconButton(onClick = { viewModel.exportRules() }) {
                        Icon(Icons.Default.Upload, contentDescription = "导出")
                    }
                    IconButton(onClick = { viewModel.importRules() }) {
                        Icon(Icons.Default.Download, contentDescription = "导入")
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
                Icon(Icons.Default.Add, contentDescription = "添加规则")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索栏
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索关键词或回复内容") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // 分类筛选
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.categories) { category ->
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { viewModel.setSelectedCategory(category) },
                        label = { Text(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 规则列表
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredRules = viewModel.getFilteredRules()
                if (filteredRules.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.LibraryBooks,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无规则",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "点击右下角添加关键词规则",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredRules, key = { it.id }) { rule ->
                            RuleCard(
                                rule = rule,
                                onEdit = { viewModel.showEditDialog(rule) },
                                onDelete = { viewModel.deleteRule(rule.id) },
                                onToggleEnabled = { viewModel.toggleRuleEnabled(rule.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加/编辑规则对话框
    if (uiState.showAddDialog) {
        AddEditRuleDialog(
            rule = uiState.editingRule,
            categories = uiState.categories.filter { it != "全部" },
            onDismiss = { viewModel.hideDialog() },
            onSave = { viewModel.saveRule(it) }
        )
    }
}

@Composable
private fun RuleCard(
    rule: KeywordRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled)
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
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = rule.keyword,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (rule.matchType) {
                                MatchType.EXACT -> "精确"
                                MatchType.CONTAINS -> "包含"
                                MatchType.REGEX -> "正则"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Row {
                    IconButton(onClick = onToggleEnabled) {
                        Icon(
                            if (rule.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (rule.enabled) "禁用" else "启用",
                            tint = if (rule.enabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = rule.replyTemplate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = rule.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "优先级: ${rule.priority}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除关键词 \"${rule.keyword}\" 的规则吗？") },
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
private fun AddEditRuleDialog(
    rule: KeywordRule?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (KeywordRule) -> Unit
) {
    var keyword by remember { mutableStateOf(rule?.keyword ?: "") }
    var replyTemplate by remember { mutableStateOf(rule?.replyTemplate ?: "") }
    var selectedCategory by remember { mutableStateOf(rule?.category ?: categories.firstOrNull() ?: "售前咨询") }
    var selectedMatchType by remember { mutableStateOf(rule?.matchType ?: MatchType.CONTAINS) }
    var priority by remember { mutableStateOf(rule?.priority?.toFloat() ?: 1f) }
    var expanded by remember { mutableStateOf(false) }
    var matchTypeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) "添加规则" else "编辑规则") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("关键词") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = matchTypeExpanded,
                    onExpandedChange = { matchTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (selectedMatchType) {
                            MatchType.EXACT -> "精确匹配"
                            MatchType.CONTAINS -> "包含匹配"
                            MatchType.REGEX -> "正则表达式"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("匹配类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = matchTypeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = matchTypeExpanded,
                        onDismissRequest = { matchTypeExpanded = false }
                    ) {
                        MatchType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (type) {
                                            MatchType.EXACT -> "精确匹配"
                                            MatchType.CONTAINS -> "包含匹配"
                                            MatchType.REGEX -> "正则表达式"
                                        }
                                    )
                                },
                                onClick = {
                                    selectedMatchType = type
                                    matchTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = replyTemplate,
                    onValueChange = { replyTemplate = it },
                    label = { Text("回复模板") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "优先级: ${priority.toInt()}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = priority,
                        onValueChange = { priority = it },
                        valueRange = 0f..5f,
                        steps = 4
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keyword.isNotBlank() && replyTemplate.isNotBlank()) {
                        onSave(
                            KeywordRule(
                                id = rule?.id ?: 0,
                                keyword = keyword,
                                matchType = selectedMatchType,
                                replyTemplate = replyTemplate,
                                category = selectedCategory,
                                priority = priority.toInt(),
                                enabled = rule?.enabled ?: true
                            )
                        )
                    }
                },
                enabled = keyword.isNotBlank() && replyTemplate.isNotBlank()
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
