package com.kefu.xiaomi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefu.xiaomi.data.model.Scenario
import com.kefu.xiaomi.data.model.ScenarioType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScenarioConfigUiState(
    val scenarios: List<Scenario> = emptyList(),
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false,
    val editingScenario: Scenario? = null
)

@HiltViewModel
class ScenarioConfigViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ScenarioConfigUiState())
    val uiState: StateFlow<ScenarioConfigUiState> = _uiState.asStateFlow()

    init {
        loadScenarios()
    }

    private fun loadScenarios() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // 模拟加载数据
            val scenarios = listOf(
                Scenario(
                    id = 1,
                    name = "全局场景",
                    type = ScenarioType.ALL_PROPERTIES,
                    description = "适用于所有房源和产品的通用场景"
                ),
                Scenario(
                    id = 2,
                    name = "新房销售",
                    type = ScenarioType.SPECIFIC_PRODUCT,
                    targetId = "product_001",
                    description = "新房项目销售相关问题"
                ),
                Scenario(
                    id = 3,
                    name = "二手房-A区",
                    type = ScenarioType.SPECIFIC_PROPERTY,
                    targetId = "property_a",
                    description = "A区二手房咨询"
                ),
                Scenario(
                    id = 4,
                    name = "租房业务",
                    type = ScenarioType.SPECIFIC_PRODUCT,
                    targetId = "product_rent",
                    description = "租房相关问题"
                )
            )
            _uiState.value = _uiState.value.copy(
                scenarios = scenarios,
                isLoading = false
            )
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingScenario = null)
    }

    fun showEditDialog(scenario: Scenario) {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingScenario = scenario)
    }

    fun hideDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, editingScenario = null)
    }

    fun saveScenario(scenario: Scenario) {
        viewModelScope.launch {
            val scenarios = if (scenario.id == 0L) {
                val newScenario = scenario.copy(id = System.currentTimeMillis())
                _uiState.value.scenarios + newScenario
            } else {
                _uiState.value.scenarios.map {
                    if (it.id == scenario.id) scenario else it
                }
            }
            _uiState.value = _uiState.value.copy(scenarios = scenarios, showAddDialog = false, editingScenario = null)
        }
    }

    fun deleteScenario(scenarioId: Long) {
        viewModelScope.launch {
            val scenarios = _uiState.value.scenarios.filter { it.id != scenarioId }
            _uiState.value = _uiState.value.copy(scenarios = scenarios)
        }
    }
}
