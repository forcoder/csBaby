package com.csbaby.kefu.presentation.screens.knowledge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.csbaby.kefu.R
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.model.MatchType
import com.csbaby.kefu.domain.model.RuleTargetType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    viewModel: KnowledgeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<KeywordRule?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<KeywordRule?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importRules(uri)
        }
    }

    LaunchedEffect(uiState.noticeMessage) {
        val message = uiState.noticeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeNoticeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.knowledge_base)) },
                actions = {
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = uiState.totalRuleCount > 0 && !uiState.isImporting && !uiState.isClearing
                    ) {

                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清空知识库"
                        )
                    }
                    IconButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/csv",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "text/plain",
                                    "*/*"
                                )
                            )

                        },
                        enabled = !uiState.isImporting && !uiState.isClearing
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = stringResource(R.string.import_data)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading || uiState.isImporting || uiState.isClearing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.search(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("搜索关键词、分类或对象...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            if (uiState.isImporting || uiState.isClearing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp))
                    Text(
                        text = if (uiState.isClearing) {
                            "正在清空知识库规则..."
                        } else {
                            "正在导入规则文件（支持 JSON / CSV / Excel .xlsx）..."


                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.rules.isEmpty()) {
                val isSearching = searchQuery.isNotBlank()
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
                        Text(
                            text = if (isSearching) "没有找到匹配规则" else "知识库还是空的",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isSearching) {
                                "换个关键词试试，或者清空搜索后查看全部规则。"
                            } else {
                                "可以先手动新增规则，或者从 JSON / CSV / Excel .xlsx 文件导入。"


                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {

                // 删除错误提示
                uiState.deleteErrorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = uiState.rules, key = { it.id }) { rule ->
                        RuleItem(
                            rule = rule,
                            onEdit = { editingRule = rule },
                            onDelete = { viewModel.deleteRule(rule.id) },
                            onToggle = { viewModel.toggleRule(rule.id, !rule.enabled) },
                            onDuplicate = { viewModel.duplicateRule(rule.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || editingRule != null) {
        RuleEditDialog(
            rule = editingRule,
            onDismiss = {
                showAddDialog = false
                editingRule = null
            },
            onSave = { rule ->
                viewModel.saveRule(rule)
                showAddDialog = false
                editingRule = null
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空知识库") },
            text = {
                Text(
                    "将删除当前全部 ${uiState.totalRuleCount} 条规则，包括手动新增和导入的内容；场景配置不会被删除。确认继续吗？"

                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllRules()
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
fun RuleItem(
    rule: KeywordRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    onDuplicate: () -> Unit
) {
    androidx.compose.material3.Card(
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
                    Text(
                        text = rule.keyword,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (rule.category.isNotBlank() && rule.category != "通用问题") {
                        Text(
                            text = rule.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggle() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = rule.replyTemplate,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = rule.targetSummary(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(

                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制")
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
fun RuleEditDialog(
    rule: KeywordRule?,
    onDismiss: () -> Unit,
    onSave: (KeywordRule) -> Unit
) {
    var keyword by remember { mutableStateOf(rule?.keyword ?: "") }
    var matchType by remember { mutableStateOf(rule?.matchType ?: MatchType.CONTAINS) }
    var replyTemplate by remember { mutableStateOf(rule?.replyTemplate ?: "") }
    var category by remember { mutableStateOf(rule?.category ?: "") }
    var priority by remember { mutableStateOf(rule?.priority?.toString() ?: "0") }
    val initialTargetNamesText = remember(rule) {
        when (rule?.targetType) {
            RuleTargetType.PROPERTY,
            RuleTargetType.CONTACT,
            RuleTargetType.GROUP -> rule.targetNames.joinToString("，")
            RuleTargetType.ALL,
            null -> ""
        }
    }
    var applyToAllProperties by remember(rule) {
        mutableStateOf(
            rule == null ||
                rule.targetType == RuleTargetType.ALL ||
                rule.targetNames.isEmpty()
        )
    }
    var targetNamesText by remember(rule) { mutableStateOf(initialTargetNamesText) }

    AlertDialog(

        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) stringResource(R.string.add_rule) else stringResource(R.string.edit_rule)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text(stringResource(R.string.keyword)) },
                    supportingText = { Text("多个关键词可用逗号分隔，命中任意一个都会触发这条规则") },
                    modifier = Modifier.fillMaxWidth()
                )


                var matchTypeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = matchTypeExpanded,
                    onExpandedChange = { matchTypeExpanded = !matchTypeExpanded }
                ) {
                    OutlinedTextField(
                        value = matchType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("匹配类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = matchTypeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = matchTypeExpanded,
                        onDismissRequest = { matchTypeExpanded = false }
                    ) {

                        MatchType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    matchType = type
                                    matchTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = replyTemplate,
                    onValueChange = { replyTemplate = it },
                    label = { Text(stringResource(R.string.reply_template)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类（可选）") },
                    placeholder = { Text("如：入住咨询、退房问题、价格咨询等") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it },
                    label = { Text(stringResource(R.string.priority)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("0-999，数字越大优先级越高") }
                )

                var propertyScopeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = propertyScopeExpanded,
                    onExpandedChange = { propertyScopeExpanded = !propertyScopeExpanded }
                ) {
                    OutlinedTextField(
                        value = if (applyToAllProperties) "全部房源" else "指定房源",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("适用房源") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = propertyScopeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = propertyScopeExpanded,
                        onDismissRequest = { propertyScopeExpanded = false }
                    ) {
                        listOf(true to "全部房源", false to "指定房源").forEach { (applyAll, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    applyToAllProperties = applyAll
                                    if (applyAll) {
                                        targetNamesText = ""
                                    }
                                    propertyScopeExpanded = false
                                }
                            )
                        }
                    }
                }

                if (!applyToAllProperties) {
                    OutlinedTextField(
                        value = targetNamesText,
                        onValueChange = { targetNamesText = it },
                        label = { Text("房源名称") },
                        placeholder = { Text("多个房源请用逗号分隔") },
                        supportingText = { Text("留空则表示全部房源") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }


            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedKeyword = keyword.trim()
                    val trimmedReply = replyTemplate.trim()
                    if (trimmedKeyword.isBlank() || trimmedReply.isBlank()) return@TextButton
                    val targetNames = targetNamesText
                        .split(Regex("[,，\n]"))
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    val resolvedTargetType = if (applyToAllProperties || targetNames.isEmpty()) {
                        RuleTargetType.ALL
                    } else {
                        RuleTargetType.PROPERTY
                    }

                    val newRule = KeywordRule(
                        id = rule?.id ?: 0,
                        keyword = trimmedKeyword,
                        matchType = matchType,
                        replyTemplate = trimmedReply,
                        category = category.trim(),
                        targetType = resolvedTargetType,
                        targetNames = if (resolvedTargetType == RuleTargetType.ALL) emptyList() else targetNames,
                        priority = priority.toIntOrNull() ?: 0,
                        enabled = rule?.enabled ?: true,
                        createdAt = rule?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )

                    onSave(newRule)
                },
                enabled = keyword.trim().isNotBlank() && replyTemplate.trim().isNotBlank()
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

private fun KeywordRule.targetSummary(): String {
    return if (targetNames.isEmpty()) {
        "适用房源：全部房源"
    } else {
        "适用房源：${targetNames.joinToString("、")}"
    }
}


