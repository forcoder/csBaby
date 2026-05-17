package com.csbaby.kefu.presentation.screens.knowledge

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.local.KefuDatabase
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.UserAuthManager
import com.csbaby.kefu.data.remote.dto.BackupDto
import com.csbaby.kefu.data.remote.dto.BlacklistDto
import com.csbaby.kefu.data.remote.dto.ModelConfigDto
import com.csbaby.kefu.data.remote.dto.RestoreRequest
import com.csbaby.kefu.data.remote.dto.RuleDto
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
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
    val isExporting: Boolean = false,
    val isClearing: Boolean = false,
    val noticeMessage: String? = null
)

/**
 * 导入模式：追加或覆盖
 */
enum class ImportMode {
    APPEND,  // 追加：保留现有规则，添加新规则
    OVERRIDE // 覆盖：先清空所有规则，再导入新规则
}



@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val knowledgeBaseManager: KnowledgeBaseManager,
    private val apiService: CsbabyApiService,
    private val userAuthManager: UserAuthManager,
    private val kefuDatabase: KefuDatabase
) : ViewModel() {

    companion object {
        private const val TAG = "KnowledgeViewModel"
    }




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
            // 重建 Trie 树，使新规则立即生效
            knowledgeBaseManager.initializeMatcher()
        }
    }

    fun importRules(uri: Uri, mode: ImportMode = ImportMode.APPEND) {
        if (_uiState.value.isClearing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, noticeMessage = null) }

            // 如果是覆盖模式，先清空现有规则
            if (mode == ImportMode.OVERRIDE) {
                val cleared = runCatching {
                    knowledgeBaseManager.clearAllRules()
                }.getOrDefault(0)
                Log.d(TAG, "Import override: cleared $cleared existing rules")
            }

            val format = resolveImportFormat(uri)

            // Check file size before opening (max 10MB for CSV/JSON, Excel has its own check)
            if (format != ImportFormat.EXCEL_XLSX) {
                val fileSize = runCatching {
                    appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                        } else -1L
                    } ?: -1L
                }.getOrDefault(-1L)

                if (fileSize > 10 * 1024 * 1024) {
                    _uiState.update { it.copy(isImporting = false, noticeMessage = "文件过大（最大10MB），请选择较小的文件") }
                    return@launch
                }
            }

            val result = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    when (format) {
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
                    if (mode == ImportMode.OVERRIDE) {
                        "已覆盖导入 ${result.successCount} 条规则"
                    } else {
                        "已成功导入 ${result.successCount} 条规则"
                    }
                }
                else -> {
                    result.errorMessage ?: "没有导入到任何规则"
                }
            }

            // 导入成功后，重建 Trie 树使新规则立即生效
            if (result.successCount > 0) {
                knowledgeBaseManager.initializeMatcher()
                // 自动同步到云端，防止卸载重装后数据丢失
                autoSyncToCloud()
            }

            _uiState.update {
                it.copy(
                    isImporting = false,
                    noticeMessage = noticeMessage
                )
            }
        }
    }

    /**
     * 导入成功后自动同步所有数据到云端
     */
    private fun autoSyncToCloud() {
        viewModelScope.launch {
            try {
                userAuthManager.ensureAuthenticated()
                val rules = kefuDatabase.keywordRuleDao().getAllRulesList().map { entity ->
                    RuleDto(
                        id = entity.id.toInt(), deviceId = "", keyword = entity.keyword,
                        matchType = entity.matchType, replyTemplate = entity.replyTemplate,
                        category = entity.category, targetType = entity.targetType,
                        targetNames = entity.targetNamesJson, priority = entity.priority,
                        enabled = if (entity.enabled) 1 else 0
                    )
                }
                val models = kefuDatabase.aiModelConfigDao().getAllModelsList().map { entity ->
                    ModelConfigDto(
                        id = entity.id.toInt(), deviceId = "", name = entity.modelName,
                        modelType = entity.modelType, model = entity.model,
                        apiKey = "", apiEndpoint = entity.apiEndpoint,
                        temperature = entity.temperature.toDouble(), maxTokens = entity.maxTokens,
                        isDefault = if (entity.isDefault) 1 else 0,
                        enabled = if (entity.isEnabled) 1 else 0
                    )
                }
                val blacklist = kefuDatabase.messageBlacklistDao().getAllList().map { entity ->
                    BlacklistDto(
                        id = entity.id, type = entity.type, value = entity.value,
                        description = entity.description, packageName = entity.packageName,
                        isEnabled = entity.isEnabled, createdAt = entity.createdAt
                    )
                }
                val backupData = BackupDto(
                    version = 2, deviceId = "",
                    rules = rules, models = models,
                    history = emptyList(), feedback = emptyList(), metrics = emptyList(),
                    blacklist = blacklist
                )
                apiService.restoreBackup(RestoreRequest(backup = backupData))
                Timber.d(TAG, "导入后自动云同步成功: rules=${rules.size}, models=${models.size}, blacklist=${blacklist.size}")
            } catch (e: Exception) {
                Timber.w(TAG, "导入后自动云同步失败（不影响本地导入）: ${e.message}")
            }
        }
    }

    // 兼容旧方法，默认使用追加模式
    fun importRules(uri: Uri) {
        importRules(uri, ImportMode.APPEND)
    }

    fun clearAllRules() {
        if (_uiState.value.isImporting || _uiState.value.isClearing) return

        viewModelScope.launch {
            if (allRules.isEmpty()) {
                _uiState.update { it.copy(noticeMessage = "知识库已经是空的") }
                return@launch
            }

            _uiState.update { it.copy(isClearing = true, noticeMessage = null) }
            val noticeMessage = runCatching {
                val removedCount = knowledgeBaseManager.clearAllRules()
                // 清空后重建空的 Trie 树
                knowledgeBaseManager.initializeMatcher()
                if (removedCount > 0) {
                    "已清空知识库，共删除 ${removedCount} 条规则"
                } else {
                    "知识库已经是空的"
                }
            }.getOrElse { exception ->
                exception.message ?: "清空知识库失败"
            }

            _uiState.update {
                it.copy(
                    isClearing = false,
                    noticeMessage = noticeMessage
                )
            }
        }
    }

    fun exportRules(uri: Uri, format: ExportFormat) {
        if (_uiState.value.isExporting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, noticeMessage = null) }

            val result = runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    when (format) {
                        ExportFormat.JSON -> knowledgeBaseManager.exportToJson(outputStream)
                        ExportFormat.CSV -> knowledgeBaseManager.exportToCsv(outputStream)
                    }
                } ?: throw Exception("无法创建文件")
            }

            val noticeMessage = result.fold(
                onSuccess = { "导出成功" },
                onFailure = { "导出失败：${it.message}" }
            )

            _uiState.update { it.copy(isExporting = false, noticeMessage = noticeMessage) }
        }
    }

    fun consumeNoticeMessage() {
        _uiState.update { it.copy(noticeMessage = null) }
    }


    fun deleteRule(id: Long) {
        viewModelScope.launch {
            knowledgeBaseManager.deleteRule(id)
            // 重建 Trie 树，移除已删除规则
            knowledgeBaseManager.initializeMatcher()
        }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            knowledgeBaseManager.toggleRule(id, enabled)
            // 重建 Trie 树，更新规则启用状态
            knowledgeBaseManager.initializeMatcher()
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

