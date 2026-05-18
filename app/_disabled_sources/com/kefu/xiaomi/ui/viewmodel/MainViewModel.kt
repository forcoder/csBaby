package com.kefu.xiaomi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefu.xiaomi.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val monitoredApps: List<AppConfig> = emptyList(),
    val recentMessages: List<NewMessage> = emptyList(),
    val isMonitoringEnabled: Boolean = true,
    val todayReplyCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadMonitoredApps()
        loadRecentMessages()
    }

    private fun loadMonitoredApps() {
        viewModelScope.launch {
            // 模拟加载数据
            val apps = listOf(
                AppConfig("com.tencent.mm", "微信", isMonitored = true),
                AppConfig("com.alibaba.android.rimet", "钉钉", isMonitored = true),
                AppConfig("com.tencent.mobileqq", "QQ", isMonitored = false),
                AppConfig("com.sina.weibo", "微博", isMonitored = false)
            )
            _uiState.value = _uiState.value.copy(monitoredApps = apps)
        }
    }

    private fun loadRecentMessages() {
        viewModelScope.launch {
            val messages = listOf(
                NewMessage(
                    id = "1",
                    appPackage = "com.tencent.mm",
                    appName = "微信",
                    content = "您好，请问产品的价格是多少？",
                    sender = "客户张三",
                    timestamp = System.currentTimeMillis() - 60000
                ),
                NewMessage(
                    id = "2",
                    appPackage = "com.tencent.mm",
                    appName = "微信",
                    content = "请问什么时候可以发货？",
                    sender = "客户李四",
                    timestamp = System.currentTimeMillis() - 180000
                )
            )
            _uiState.value = _uiState.value.copy(
                recentMessages = messages,
                todayReplyCount = 42
            )
        }
    }

    fun toggleMonitoring(appPackage: String) {
        viewModelScope.launch {
            val apps = _uiState.value.monitoredApps.map { app ->
                if (app.packageName == appPackage) {
                    app.copy(isMonitored = !app.isMonitored)
                } else {
                    app
                }
            }
            _uiState.value = _uiState.value.copy(monitoredApps = apps)
        }
    }

    fun toggleGlobalMonitoring() {
        _uiState.value = _uiState.value.copy(
            isMonitoringEnabled = !_uiState.value.isMonitoringEnabled
        )
    }

    fun dismissMessage(messageId: String) {
        viewModelScope.launch {
            val messages = _uiState.value.recentMessages.filter { it.id != messageId }
            _uiState.value = _uiState.value.copy(recentMessages = messages)
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            loadMonitoredApps()
            loadRecentMessages()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
