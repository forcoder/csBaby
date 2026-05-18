package com.csbaby.kefu.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.repository.KeywordRuleRepository
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MonitoredAppUiModel(
    val packageName: String,
    val displayName: String,
    val isSelected: Boolean
)

data class HomeUiState(
    val isMonitoringEnabled: Boolean = false,
    val isFloatingIconEnabled: Boolean = false,
    val totalReplies: Int = 0,
    val todayReplies: Int = 0,
    val knowledgeBaseCount: Int = 0,
    val recentReplies: List<ReplyHistory> = emptyList(),
    val monitoredApps: List<MonitoredAppUiModel> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val replyHistoryRepository: ReplyHistoryRepository,
    private val keywordRuleRepository: KeywordRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            preferencesManager.userPreferencesFlow.collect { prefs ->
                _uiState.update {
                    it.copy(
                        isMonitoringEnabled = prefs.monitoringEnabled,
                        isFloatingIconEnabled = prefs.floatingIconEnabled,
                        monitoredApps = buildMonitoredApps(prefs.selectedApps)
                    )
                }
            }
        }

        viewModelScope.launch {
            replyHistoryRepository.getRecentReplies(10).collect { replies ->
                _uiState.update { state ->
                    state.copy(
                        recentReplies = replies,
                        totalReplies = replyHistoryRepository.getTotalCount(),
                        todayReplies = replies.count {
                            isToday(it.sendTime)
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            keywordRuleRepository.getRuleCountFlow().collect { count ->
                _uiState.update { it.copy(knowledgeBaseCount = count) }
            }
        }
    }

    fun toggleMonitoring() {
        viewModelScope.launch {
            val currentState = _uiState.value.isMonitoringEnabled
            preferencesManager.updateMonitoringEnabled(!currentState)
        }
    }

    fun updateFloatingIconEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updateFloatingIconEnabled(enabled)
        }
    }

    fun updateSelectedApps(selectedApps: Set<String>) {
        viewModelScope.launch {
            preferencesManager.updateSelectedApps(selectedApps)
        }
    }

    private fun buildMonitoredApps(selectedApps: Set<String>): List<MonitoredAppUiModel> {
        return supportedApps.map { app ->
            MonitoredAppUiModel(
                packageName = app.packageName,
                displayName = app.displayName,
                isSelected = app.packageName in selectedApps
            )
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        val now = System.currentTimeMillis()
        val dayInMillis = 24 * 60 * 60 * 1000
        return (now - timestamp) < dayInMillis
    }

    private data class SupportedApp(
        val packageName: String,
        val displayName: String
    )

    companion object {
        private val supportedApps = listOf(
            SupportedApp(PreferencesManager.WECHAT_PACKAGE, "微信"),
            SupportedApp(PreferencesManager.BAIJUYI_PACKAGE, "百居易"),
            SupportedApp(PreferencesManager.MEITUAN_MINSU_PACKAGE, "美团民宿"),
            SupportedApp(PreferencesManager.TUJIA_MINSU_PACKAGE, "途家民宿")
        )
    }
}

