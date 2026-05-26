package com.csbaby.kefu.presentation.screens.knowledge

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KnowledgeUiState(
    val rules: List<KeywordRule> = emptyList(),
    val categories: List<String> = emptyList(),
    val totalRuleCount: Int = 0,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val isClearing: Boolean = false,
    val noticeMessage: String? = null,
    val deleteErrorMessage: String? = null
)



@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val knowledgeBaseManager: KnowledgeBaseManager,
    private val syncManager: SyncManager
) : ViewModel() {


    private val _uiState = MutableStateFlow(KnowledgeUiState())
    val uiState: StateFlow<KnowledgeUiState> = _uiState.asStateFlow()

    private var allRules: List<KeywordRule> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            knowledgeBaseManager.getAllRules().collect { rules ->
                allRules = rules
                _uiState.update {
                    it.copy(
                        rules = rules,
                        totalRuleCount = rules.size,
                        isLoading = false
                    )
                }
            }

        }

        viewModelScope.launch {
            knowledgeBaseManager.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    fun search(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            _uiState.update { it.copy(rules = allRules) }
            return
        }

        val results = allRules.filter { rule ->
            rule.keyword.contains(normalizedQuery, ignoreCase = true) ||
                rule.category.contains(normalizedQuery, ignoreCase = true) ||
                rule.replyTemplate.contains(normalizedQuery, ignoreCase = true) ||
                rule.targetNames.any { it.contains(normalizedQuery, ignoreCase = true) }
        }
        _uiState.update { it.copy(rules = results) }
    }


    fun saveRule(rule: KeywordRule) {
        viewModelScope.launch {
            if (rule.id == 0L) {
                knowledgeBaseManager.createRule(rule)
            } else {
                knowledgeBaseManager.updateRule(rule)
            }
            // 自动同步到服务端
            triggerAutoSyncForLoggedInUser()
        }
    }

    fun importRules(uri: Uri) {
        if (_uiState.value.isClearing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, noticeMessage = null) }

            val result = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    when (resolveImportFormat(uri)) {
                        ImportFormat.CSV -> knowledgeBaseManager.importFromCsv(inputStream)
                        ImportFormat.EXCEL_XLSX -> knowledgeBaseManager.importFromExcel(inputStream)
                        ImportFormat.EXCEL_XLS -> KnowledgeBaseManager.ImportResult(
                            0,
                            1,
                            "暂不支持旧版 .xls，请另存为 .xlsx 后再导入"
                        )
                        ImportFormat.JSON -> knowledgeBaseManager.importFromJson(inputStream)
                    }
                } ?: KnowledgeBaseManager.ImportResult(0, 1, "无法打开所选文件")


            }.getOrElse { exception ->
                KnowledgeBaseManager.ImportResult(0, 1, exception.message ?: "导入失败")
            }

            val noticeMessage = when {
                result.errorMessage != null && result.successCount == 0 -> {
                    "导入失败：${result.errorMessage}"
                }
                result.successCount > 0 && result.errorCount > 0 -> {
                    "导入完成：成功 ${result.successCount} 条，失败 ${result.errorCount} 条"
                }
                result.successCount > 0 -> {
                    "已成功导入 ${result.successCount} 条规则"
                }
                else -> {
                    result.errorMessage ?: "没有导入到任何规则"
                }
            }

            _uiState.update {
                it.copy(
                    isImporting = false,
                    noticeMessage = noticeMessage
                )
            }

            // 导入成功后，如果已登录，自动触发增量同步到服务端
            if (result.successCount > 0) {
                triggerAutoSyncForLoggedInUser()
            }
        }
    }

    fun clearAllRules() {
        if (_uiState.value.isImporting || _uiState.value.isClearing) return

        viewModelScope.launch {
            if (allRules.isEmpty()) {
                _uiState.update { it.copy(noticeMessage = "知识库已经是空的") }
                return@launch
            }

            _uiState.update { it.copy(isClearing = true, noticeMessage = null) }
            val result = runCatching { knowledgeBaseManager.clearAllRules() }
            val noticeMessage = if (result.isSuccess) {
                val removedCount = result.getOrNull() ?: 0
                if (removedCount > 0) "已清空知识库，共删除 ${removedCount} 条规则" else "知识库已经是空的"
            } else {
                result.exceptionOrNull()?.message ?: "清空知识库失败"
            }
            // 自动同步到服务端
            if (result.isSuccess) triggerAutoSyncForLoggedInUser()

            _uiState.update {
                it.copy(isClearing = false, noticeMessage = noticeMessage)
            }
        }
    }

    fun consumeNoticeMessage() {
        _uiState.update { it.copy(noticeMessage = null) }
    }


    fun deleteRule(id: Long) {
        viewModelScope.launch {
            knowledgeBaseManager.deleteRule(id)
                .onSuccess {
                    _uiState.update { it.copy(deleteErrorMessage = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(deleteErrorMessage = error.message ?: "删除失败") }
                }
            triggerAutoSyncForLoggedInUser()
        }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            knowledgeBaseManager.toggleRule(id, enabled)
            triggerAutoSyncForLoggedInUser()
        }
    }

    /**
     * 写入操作后触发自动同步（debounce 2s，由 SyncManager.triggerSync 处理）
     */
    private fun triggerAutoSyncForLoggedInUser() {
        if (syncManager.isLoggedIn()) {
            syncManager.triggerSync()
        }
    }

    private fun resolveImportFormat(uri: Uri): ImportFormat {
        val mimeType = appContext.contentResolver.getType(uri).orEmpty().lowercase()
        when {
            mimeType.contains("csv") -> return ImportFormat.CSV
            mimeType.contains("spreadsheetml") -> return ImportFormat.EXCEL_XLSX
            mimeType.contains("ms-excel") -> return ImportFormat.EXCEL_XLS
        }

        val extension = uri.lastPathSegment.orEmpty().substringAfterLast('.', missingDelimiterValue = "")
        return when {
            extension.equals("csv", ignoreCase = true) -> ImportFormat.CSV
            extension.equals("xlsx", ignoreCase = true) -> ImportFormat.EXCEL_XLSX
            extension.equals("xls", ignoreCase = true) -> ImportFormat.EXCEL_XLS
            else -> ImportFormat.JSON
        }
    }

    private enum class ImportFormat {
        JSON,
        CSV,
        EXCEL_XLSX,
        EXCEL_XLS
    }


}

