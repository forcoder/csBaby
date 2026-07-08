package com.csbaby.kefu.presentation.screens.model

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import com.csbaby.kefu.domain.repository.AIModelRepository
import com.csbaby.kefu.infrastructure.ai.AIService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TestResult(
    val success: Boolean,
    val errorMessage: String? = null
)

data class ModelExportData(
    val version: Int = 1,
    val models: List<ExportedModel>
)

data class ExportedModel(
    val modelType: String,
    val modelName: String,
    val apiKey: String,
    val apiEndpoint: String,
    val temperature: Float,
    val maxTokens: Int,
    val isDefault: Boolean,
    val isEnabled: Boolean
)

data class ModelUiState(
    val models: List<AIModelConfig> = emptyList(),
    val isLoading: Boolean = false,
    val testResults: Map<Long, TestResult> = emptyMap(),
    val exportImportMessage: String? = null
)

@HiltViewModel
class ModelViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val aiModelRepository: AIModelRepository,
    private val aiService: AIService,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _uiState.asStateFlow()

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    init {
        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            aiModelRepository.getAllModels().collect { models ->
                _uiState.update { it.copy(models = models, isLoading = false) }
            }
        }
    }

    fun saveModel(model: AIModelConfig) {
        viewModelScope.launch {
            if (model.id == 0L) {
                aiModelRepository.insertModel(model)
            } else {
                aiModelRepository.updateModel(model)
            }
            triggerAutoSync()
        }
    }

    fun deleteModel(id: Long) {
        viewModelScope.launch {
            aiModelRepository.deleteModel(id)
            triggerAutoSync()
        }
    }

    fun setDefaultModel(id: Long) {
        viewModelScope.launch {
            aiModelRepository.setDefaultModel(id)
            triggerAutoSync()
        }
    }

    private fun triggerAutoSync() {
        if (syncManager.isLoggedIn()) {
            syncManager.triggerSync()
        }
    }

    fun testConnection(modelId: Long) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Main) {
                aiService.testModelConnection(modelId)
            }
            val testResult = result.fold(
                onSuccess = { TestResult(success = true) },
                onFailure = { TestResult(success = false, errorMessage = it.message ?: "未知错误") }
            )
            _uiState.update {
                it.copy(testResults = it.testResults + (modelId to testResult))
            }
        }
    }

    /**
     * 导出所有模型配置为 JSON，写入到指定 Uri
     */
    fun exportModels(uri: Uri) {
        viewModelScope.launch {
            try {
                val currentModels = _uiState.value.models
                if (currentModels.isEmpty()) {
                    _uiState.update { it.copy(exportImportMessage = "没有可导出的模型") }
                    return@launch
                }
                val exportData = ModelExportData(
                    version = 1,
                    models = currentModels.map { model ->
                        ExportedModel(
                            modelType = model.modelType.name,
                            modelName = model.modelName,
                            apiKey = model.apiKey,
                            apiEndpoint = model.apiEndpoint,
                            temperature = model.temperature,
                            maxTokens = model.maxTokens,
                            isDefault = model.isDefault,
                            isEnabled = model.isEnabled
                        )
                    }
                )
                val jsonString = gson.toJson(exportData)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }
                _uiState.update {
                    it.copy(exportImportMessage = "已成功导出 ${currentModels.size} 个模型")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(exportImportMessage = "导出失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    /**
     * 从指定 Uri 读取 JSON 文件，导入模型配置
     */
    fun importModels(uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.readBytes().toString(Charsets.UTF_8)
                    } ?: throw Exception("无法打开所选文件")
                }

                val exportData = gson.fromJson(jsonString, ModelExportData::class.java)
                if (exportData.models.isEmpty()) {
                    _uiState.update { it.copy(exportImportMessage = "导入文件不包含任何模型") }
                    return@launch
                }

                var importedCount = 0
                var errorCount = 0
                var hasDefault = false

                for (exported in exportData.models) {
                    try {
                        val modelType = ModelType.valueOf(exported.modelType)
                        val isDefault = if (exported.isDefault && !hasDefault) {
                            hasDefault = true
                            true
                        } else {
                            false
                        }
                        val model = AIModelConfig(
                            id = 0,
                            modelType = modelType,
                            modelName = exported.modelName,
                            apiKey = exported.apiKey,
                            apiEndpoint = exported.apiEndpoint,
                            temperature = exported.temperature,
                            maxTokens = exported.maxTokens,
                            isDefault = isDefault,
                            isEnabled = exported.isEnabled
                        )
                        aiModelRepository.insertModel(model)
                        importedCount++
                    } catch (e: Exception) {
                        errorCount++
                    }
                }

                val message = when {
                    importedCount > 0 && errorCount > 0 -> "导入完成：成功 ${importedCount} 个，失败 ${errorCount} 个"
                    importedCount > 0 -> "已成功导入 ${importedCount} 个模型"
                    else -> "导入失败：所有模型均无法解析"
                }
                _uiState.update { it.copy(exportImportMessage = message) }

                if (importedCount > 0) {
                    triggerAutoSync()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(exportImportMessage = "导入失败：${e.message ?: "文件格式错误"}")
                }
            }
        }
    }

    fun consumeExportImportMessage() {
        _uiState.update { it.copy(exportImportMessage = null) }
    }
}
