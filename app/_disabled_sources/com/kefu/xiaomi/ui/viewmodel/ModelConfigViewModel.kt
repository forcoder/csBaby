package com.kefu.xiaomi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefu.xiaomi.data.model.AIModelConfig
import com.kefu.xiaomi.data.model.ModelType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelConfigUiState(
    val models: List<AIModelConfig> = emptyList(),
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false,
    val editingModel: AIModelConfig? = null,
    val testResult: TestResult? = null
)

sealed class TestResult {
    data object Loading : TestResult()
    data class Success(val message: String) : TestResult()
    data class Error(val message: String) : TestResult()
}

@HiltViewModel
class ModelConfigViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ModelConfigUiState())
    val uiState: StateFlow<ModelConfigUiState> = _uiState.asStateFlow()

    init {
        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // 模拟加载数据
            val models = listOf(
                AIModelConfig(
                    id = 1,
                    modelType = ModelType.OPENAI,
                    modelName = "GPT-4",
                    apiKey = "sk-****",
                    apiEndpoint = "https://api.openai.com/v1",
                    temperature = 0.7f,
                    maxTokens = 1000,
                    isDefault = true,
                    isEnabled = true,
                    monthlyCost = 25.50
                ),
                AIModelConfig(
                    id = 2,
                    modelType = ModelType.CLAUDE,
                    modelName = "Claude-3",
                    apiKey = "sk-ant-****",
                    apiEndpoint = "https://api.anthropic.com",
                    temperature = 0.7f,
                    maxTokens = 1000,
                    isDefault = false,
                    isEnabled = true,
                    monthlyCost = 15.00
                ),
                AIModelConfig(
                    id = 3,
                    modelType = ModelType.ZHIPU,
                    modelName = "智谱 GLM-4",
                    apiKey = "****",
                    apiEndpoint = "https://open.bigmodel.cn/api/paas/v4",
                    temperature = 0.8f,
                    maxTokens = 500,
                    isDefault = false,
                    isEnabled = false,
                    monthlyCost = 0.0
                )
            )
            _uiState.value = _uiState.value.copy(
                models = models,
                isLoading = false
            )
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingModel = null)
    }

    fun showEditDialog(model: AIModelConfig) {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingModel = model)
    }

    fun hideDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, editingModel = null)
    }

    fun saveModel(model: AIModelConfig) {
        viewModelScope.launch {
            val models = if (model.id == 0L) {
                val newModel = model.copy(id = System.currentTimeMillis())
                // 如果设为默认，取消其他默认
                val updatedModels = if (newModel.isDefault) {
                    _uiState.value.models.map { it.copy(isDefault = false) }
                } else {
                    _uiState.value.models
                }
                updatedModels + newModel
            } else {
                _uiState.value.models.map {
                    if (it.id == model.id) {
                        // 如果设为默认，取消其他默认
                        if (model.isDefault) {
                            model.copy(isDefault = true)
                        } else {
                            model
                        }
                    } else {
                        if (model.isDefault) it.copy(isDefault = false) else it
                    }
                }
            }
            _uiState.value = _uiState.value.copy(models = models, showAddDialog = false, editingModel = null)
        }
    }

    fun deleteModel(modelId: Long) {
        viewModelScope.launch {
            val models = _uiState.value.models.filter { it.id != modelId }
            _uiState.value = _uiState.value.copy(models = models)
        }
    }

    fun toggleModelEnabled(modelId: Long) {
        viewModelScope.launch {
            val models = _uiState.value.models.map { model ->
                if (model.id == modelId) {
                    model.copy(isEnabled = !model.isEnabled)
                } else {
                    model
                }
            }
            _uiState.value = _uiState.value.copy(models = models)
        }
    }

    fun setDefaultModel(modelId: Long) {
        viewModelScope.launch {
            val models = _uiState.value.models.map { model ->
                model.copy(isDefault = model.id == modelId)
            }
            _uiState.value = _uiState.value.copy(models = models)
        }
    }

    fun testConnection(model: AIModelConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(testResult = TestResult.Loading)
            // 模拟测试连接
            kotlinx.coroutines.delay(2000)
            _uiState.value = _uiState.value.copy(
                testResult = if ((1..10).random() > 2) {
                    TestResult.Success("连接成功！响应时间: ${(100..500).random()}ms")
                } else {
                    TestResult.Error("连接失败，请检查API密钥和网络")
                }
            )
        }
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(testResult = null)
    }
}
