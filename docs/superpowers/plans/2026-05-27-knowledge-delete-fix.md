# 修复知识库删除按钮无响应

## 问题根因

`KnowledgeViewModel.deleteRule()` 没有错误处理，异常被协程静默吞掉，用户看不到任何反馈，误以为"没反应"。

## 修复方案

### 1. 修改 `KeywordRuleDao.softDelete` 返回受影响行数

```kotlin
// KeywordRuleDao.kt
@Query("UPDATE keyword_rules SET deleted = 1, syncVersion = 0 WHERE id = :id")
suspend fun softDelete(id: Long): Int  // 返回受影响行数
```

### 2. 修改 `KeywordRuleRepositoryImpl.deleteRule` 添加事务和异常处理

```kotlin
// KeywordRuleRepositoryImpl.kt
@Transaction
override suspend fun deleteRule(id: Long): Result<Unit> = runCatching {
    // 先检查是否存在
    val rule = keywordRuleDao.getById(id)
    if (rule == null) {
        throw Exception("规则不存在 (id=$id)")
    }
    scenarioDao.deleteRelationsForRule(id)
    val affectedRows = keywordRuleDao.softDelete(id)
    if (affectedRows == 0) {
        throw Exception("删除失败：未找到匹配的规则")
    }
    syncManager.triggerSync()
}
```

### 3. 修改 `KnowledgeViewModel.deleteRule` 添加用户反馈

```kotlin
// KnowledgeViewModel.kt
data class KnowledgeUiState(
    // ... 现有字段 ...
    val deleteErrorMessage: String? = null
)

fun deleteRule(id: Long) {
    viewModelScope.launch {
        knowledgeBaseManager.deleteRule(id)
            .onSuccess {
                _uiState.update { it.copy(deleteErrorMessage = null) }
                // 可选：显示成功提示
            }
            .onFailure { error ->
                _uiState.update { it.copy(deleteErrorMessage = error.message ?: "删除失败") }
            }
        triggerAutoSyncForLoggedInUser()
    }
}
```

### 4. 修改 `KnowledgeScreen` 显示删除错误

```kotlin
// KnowledgeScreen.kt
val uiState by viewModel.uiState.collectAsState()

// 在 UI 中添加错误显示
uiState.deleteErrorMessage?.let { error ->
    Text(
        text = error,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(16.dp)
    )
}
```

## 验证点

1. 删除存在的规则：应该成功，无错误提示
2. 删除不存在的规则：应该显示错误提示
3. 删除过程中断网：应该显示错误提示
4. 删除后列表自动刷新（Room Flow 自动更新）
