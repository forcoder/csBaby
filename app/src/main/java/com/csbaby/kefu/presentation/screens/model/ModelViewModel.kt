package com.csbaby.kefu.presentation.screens.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.repository.AIModelRepository
import com.csbaby.kefu.infrastructure.ai.AIService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TestResult(
    val success: Boolean,
    val errorMessage: String? = null
)

data class ModelUiState(
    val models: List<AIModelConfig> = emptyList(),
    val isLoading: Boolean = false,
    val testResults: Map<Long, TestResult> = emptyMap()
)

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val aiModelRepository: AIModelRepository,
    private val aiService: AIService,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _uiState.asStateFlow()

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
            val result = aiService.testModelConnection(modelId)
            val testResult = result.fold(
                onSuccess = { TestResult(success = true) },
                onFailure = { TestResult(success = false, errorMessage = it.message ?: "未知错误") }
            )
            _uiState.update {
                it.copy(testResults = it.testResults + (modelId to testResult))
            }
        }
    }
}
