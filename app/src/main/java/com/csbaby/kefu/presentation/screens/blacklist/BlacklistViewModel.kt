package com.csbaby.kefu.presentation.screens.blacklist

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import com.csbaby.kefu.domain.repository.MessageBlacklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class BlacklistUiState(
    val blacklists: List<MessageBlacklistEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val noticeMessage: String? = null
)

@HiltViewModel
class BlacklistViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val blacklistRepository: MessageBlacklistRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BlacklistUiState())
    val uiState: StateFlow<BlacklistUiState> = _uiState.asStateFlow()
    
    init {
        loadBlacklists()
    }
    
    private fun loadBlacklists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            blacklistRepository.getAll()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, noticeMessage = "加载黑名单失败: ${e.message}") }
                }
                .collect { blacklists ->
                    _uiState.update { it.copy(isLoading = false, blacklists = blacklists) }
                }
        }
    }
    
    fun addBlacklist(
        type: String,
        value: String,
        description: String = "",
        packageName: String? = null
    ) {
        if (value.isBlank()) {
            _uiState.update { it.copy(noticeMessage = "黑名单值不能为空") }
            return
        }
        
        viewModelScope.launch {
            blacklistRepository.addToBlacklist(
                type = type,
                value = value.trim(),
                description = description.trim(),
                packageName = packageName?.trim()
            )
            _uiState.update { it.copy(noticeMessage = "添加成功") }
        }
    }
    
    fun removeBlacklist(id: Long) {
        viewModelScope.launch {
            blacklistRepository.removeFromBlacklist(id)
            _uiState.update { it.copy(noticeMessage = "已删除") }
        }
    }
    
    fun toggleBlacklist(id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            blacklistRepository.toggleBlacklist(id, isEnabled)
            _uiState.update { 
                it.copy(noticeMessage = if (isEnabled) "已启用" else "已禁用") 
            }
        }
    }
    
    fun clearAll() {
        viewModelScope.launch {
            blacklistRepository.clearAll()
            _uiState.update { it.copy(noticeMessage = "已清空所有黑名单") }
        }
    }
    
    fun importBlacklists(uri: Uri) {
        if (_uiState.value.isImporting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, noticeMessage = null) }

            val result = runCatching {
                val json = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: throw Exception("无法打开文件")
                val arr = JSONArray(json)
                var successCount = 0
                var errorCount = 0
                for (i in 0 until arr.length()) {
                    try {
                        val obj = arr.getJSONObject(i)
                        blacklistRepository.addToBlacklist(
                            type = obj.optString("type", MessageBlacklistEntity.TYPE_KEYWORD),
                            value = obj.getString("value"),
                            description = obj.optString("description", ""),
                            packageName = obj.optString("packageName").ifBlank { null }
                        )
                        successCount++
                    } catch (e: Exception) {
                        errorCount++
                    }
                }
                ImportResult(successCount, errorCount, null)
            }.getOrElse { e -> ImportResult(0, 0, e.message) }

            val msg = when {
                result.errorMessage != null -> "导入失败：${result.errorMessage}"
                result.successCount > 0 && result.errorCount > 0 -> "导入完成：成功 ${result.successCount} 条，失败 ${result.errorCount} 条"
                result.successCount > 0 -> "已成功导入 ${result.successCount} 条黑名单"
                else -> "没有导入到任何黑名单"
            }

            _uiState.update { it.copy(isImporting = false, noticeMessage = msg) }
        }
    }

    fun exportBlacklists(uri: Uri) {
        if (_uiState.value.isExporting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, noticeMessage = null) }

            val result = runCatching {
                val allLists = blacklistRepository.getAll().first()
                val arr = JSONArray()
                allLists.forEach { item ->
                    val obj = JSONObject().apply {
                        put("type", item.type)
                        put("value", item.value)
                        put("description", item.description)
                        put("packageName", item.packageName ?: "")
                        put("isEnabled", item.isEnabled)
                        put("createdAt", item.createdAt)
                    }
                    arr.put(obj)
                }
                appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(arr.toString(2))
                } ?: throw Exception("无法创建文件")
            }

            val msg = result.fold(
                onSuccess = { "导出成功" },
                onFailure = { "导出失败：${it.message}" }
            )

            _uiState.update { it.copy(isExporting = false, noticeMessage = msg) }
        }
    }

    fun consumeNoticeMessage() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    private data class ImportResult(
        val successCount: Int,
        val errorCount: Int,
        val errorMessage: String? = null
    )
}
