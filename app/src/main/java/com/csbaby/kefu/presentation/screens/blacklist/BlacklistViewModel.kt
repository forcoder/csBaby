package com.csbaby.kefu.presentation.screens.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.local.dao.MessageBlacklistDao
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlacklistUiState(
    val items: List<MessageBlacklistEntity> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class BlacklistViewModel @Inject constructor(
    private val blacklistDao: MessageBlacklistDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlacklistUiState())
    val uiState: StateFlow<BlacklistUiState> = _uiState.asStateFlow()

    init {
        observeBlacklist()
    }

    private fun observeBlacklist() {
        viewModelScope.launch {
            blacklistDao.getAllFlow()
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "加载失败")
                    }
                }
                .collect { items ->
                    _uiState.update { it.copy(items = items, isLoading = false) }
                }
        }
    }

    fun addRule(type: String, value: String, description: String) {
        viewModelScope.launch {
            val entity = MessageBlacklistEntity(
                type = type,
                value = value.trim(),
                description = description.trim()
            )
            blacklistDao.insert(entity)
        }
    }

    fun toggleEnabled(item: MessageBlacklistEntity, enabled: Boolean) {
        viewModelScope.launch {
            blacklistDao.update(item.copy(isEnabled = enabled))
        }
    }

    fun deleteItem(item: MessageBlacklistEntity) {
        viewModelScope.launch {
            blacklistDao.delete(item)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
