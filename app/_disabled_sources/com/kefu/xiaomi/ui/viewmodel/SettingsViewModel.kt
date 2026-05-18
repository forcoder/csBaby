package com.kefu.xiaomi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isDarkTheme: Boolean = false,
    val notificationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val autoSendEnabled: Boolean = false,
    val showPreviewWindow: Boolean = true,
    val replyDelay: Int = 0,
    val maxHistoryDays: Int = 30
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun toggleDarkTheme() {
        _uiState.value = _uiState.value.copy(
            isDarkTheme = !_uiState.value.isDarkTheme
        )
    }

    fun toggleNotification() {
        _uiState.value = _uiState.value.copy(
            notificationEnabled = !_uiState.value.notificationEnabled
        )
    }

    fun toggleSound() {
        _uiState.value = _uiState.value.copy(
            soundEnabled = !_uiState.value.soundEnabled
        )
    }

    fun toggleVibration() {
        _uiState.value = _uiState.value.copy(
            vibrationEnabled = !_uiState.value.vibrationEnabled
        )
    }

    fun toggleAutoSend() {
        _uiState.value = _uiState.value.copy(
            autoSendEnabled = !_uiState.value.autoSendEnabled
        )
    }

    fun togglePreviewWindow() {
        _uiState.value = _uiState.value.copy(
            showPreviewWindow = !_uiState.value.showPreviewWindow
        )
    }

    fun setReplyDelay(delay: Int) {
        _uiState.value = _uiState.value.copy(replyDelay = delay)
    }

    fun setMaxHistoryDays(days: Int) {
        _uiState.value = _uiState.value.copy(maxHistoryDays = days)
    }
}
