package com.csbaby.kefu.presentation.screens.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.model.*
import com.csbaby.kefu.data.sync.AuthManager
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.data.sync.SyncState
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.backup.BackupManager
import com.csbaby.kefu.infrastructure.ota.OtaManager
import com.csbaby.kefu.infrastructure.style.StyleLearningEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ProfileUiState(
    val formalityLevel: Float = 0.5f,
    val enthusiasmLevel: Float = 0.5f,
    val professionalismLevel: Float = 0.5f,
    val learningSamples: Int = 0,
    val accuracyScore: Float = 0f,
    val commonPhrases: List<String> = emptyList(),
    val styleLearningEnabled: Boolean = true,
    val autoSendEnabled: Boolean = false,
    val wordCountPreference: Int = 50,
    val updateStatus: String = "空闲",
    val availableUpdate: OtaUpdateInfo? = null,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null,
    val syncState: SyncState = SyncState.Idle,
    val isLoggedIn: Boolean = false,
    val currentTenantId: String? = null,
    val pendingSyncCount: Int = 0,
    val lastSyncTime: Long = 0L,
    // 同步统计
    val syncStats: String = "",
    // 数据备份与恢复
    val backupStatus: BackupStatus = BackupStatus.IDLE,
    val backupMessage: String = "",
    val backupRecords: List<BackupRecord> = emptyList()
)

data class OtaUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val fileSize: String,
    val releaseNotes: String,
    val isForceUpdate: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val userStyleRepository: UserStyleRepository,
    private val styleLearningEngine: StyleLearningEngine,
    private val otaManager: OtaManager,
    private val syncManager: SyncManager,
    private val authManager: AuthManager,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var currentUserId: String = "default_user"

    init {
        loadData()
        setupOtaUpdates()
        observeSyncState()
        observeAuthState()
        observeSyncQueue()
        observeLastSyncTime()
        observeBackupState()
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                _uiState.update {
                    it.copy(
                        syncState = state,
                        // 从成功状态中提取统计信息
                        syncStats = if (state is SyncState.Success) state.stats else it.syncStats
                    )
                }
            }
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authManager.authStateFlow.collect { auth ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = auth != null,
                        currentTenantId = auth?.tenantId
                    )
                }
            }
        }
    }

    private fun observeSyncQueue() {
        viewModelScope.launch {
            syncManager.queue.pendingCount.collect { count ->
                _uiState.update { it.copy(pendingSyncCount = count) }
            }
        }
    }

    private fun observeLastSyncTime() {
        viewModelScope.launch {
            syncManager.lastSyncTime.collect { time ->
                _uiState.update { it.copy(lastSyncTime = time) }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            preferencesManager.userPreferencesFlow.collect { prefs ->
                currentUserId = prefs.currentUserId
                _uiState.update {
                    it.copy(
                        styleLearningEnabled = prefs.styleLearningEnabled,
                        autoSendEnabled = prefs.autoSendEnabled
                    )
                }
            }
        }

        viewModelScope.launch {
            userStyleRepository.getProfile(currentUserId).collect { profile ->
                profile?.let {
                    _uiState.update { state ->
                        state.copy(
                            formalityLevel = it.formalityLevel,
                            enthusiasmLevel = it.enthusiasmLevel,
                            professionalismLevel = it.professionalismLevel,
                            learningSamples = it.learningSamples,
                            accuracyScore = it.accuracyScore,
                            commonPhrases = it.commonPhrases,
                            wordCountPreference = it.wordCountPreference
                        )
                    }
                }
            }
        }
    }

    fun updateFormality(value: Float) {
        viewModelScope.launch {
            styleLearningEngine.updateStyleParameters(userId = currentUserId, formality = value)
            _uiState.update { it.copy(formalityLevel = value) }
            triggerAutoSync()
        }
    }

    fun updateEnthusiasm(value: Float) {
        viewModelScope.launch {
            styleLearningEngine.updateStyleParameters(userId = currentUserId, enthusiasm = value)
            _uiState.update { it.copy(enthusiasmLevel = value) }
            triggerAutoSync()
        }
    }

    fun updateProfessionalism(value: Float) {
        viewModelScope.launch {
            styleLearningEngine.updateStyleParameters(userId = currentUserId, professionalism = value)
            _uiState.update { it.copy(professionalismLevel = value) }
            triggerAutoSync()
        }
    }

    private fun triggerAutoSync() {
        if (syncManager.isLoggedIn()) {
            syncManager.triggerSync()
        }
    }

    fun toggleStyleLearning(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updateStyleLearningEnabled(enabled)
            _uiState.update { it.copy(styleLearningEnabled = enabled) }
        }
    }

    fun toggleAutoSend(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updateAutoSendEnabled(enabled)
            _uiState.update { it.copy(autoSendEnabled = enabled) }
        }
    }

    private fun setupOtaUpdates() {
        viewModelScope.launch {
            otaManager.updateStatus.collect { status ->
                val statusText = when (status) {
                    UpdateStatus.IDLE -> "空闲"
                    UpdateStatus.CHECKING -> "检查更新中..."
                    UpdateStatus.UPDATE_AVAILABLE -> "有新版本可用"
                    UpdateStatus.DOWNLOADING -> "下载中..."
                    UpdateStatus.DOWNLOADED -> "下载完成"
                    UpdateStatus.INSTALLING -> "正在安装"
                    UpdateStatus.SUCCESS -> "更新成功"
                    UpdateStatus.FAILED -> "更新失败"
                }
                _uiState.update { it.copy(updateStatus = statusText) }
            }
        }

        viewModelScope.launch {
            otaManager.availableUpdate.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        availableUpdate = update?.let {
                            OtaUpdateInfo(
                                versionName = it.versionName,
                                versionCode = it.versionCode,
                                fileSize = formatFileSize(it.fileSize),
                                releaseNotes = it.releaseNotes,
                                isForceUpdate = it.isForceUpdate
                            )
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            otaManager.errorMessage.collect { error ->
                _uiState.update { it.copy(errorMessage = error) }
            }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch { otaManager.checkForUpdate() }
    }

    fun startDownloadUpdate() {
        viewModelScope.launch {
            otaManager.availableUpdate.value?.let { otaManager.startDownload(it) }
        }
    }

    fun cancelDownload() { otaManager.cancelDownload() }

    fun getCurrentVersion(): String = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    // ========== 云端同步 ==========

    fun login(email: String, password: String) {
        Log.d("ProfileViewModel", "login() 调用: email=$email")
        viewModelScope.launch {
            syncManager.login(email, password).fold(
                onSuccess = { syncManager.fullSync(it.tenantId) },
                onFailure = { e ->
                    Timber.e(e, "登录失败")
                    _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "登录失败，请检查网络")) }
                }
            )
        }
    }

    fun register(email: String, password: String, displayName: String) {
        Log.d("ProfileViewModel", "register() 调用: email=$email")
        viewModelScope.launch {
            syncManager.register(email, password, displayName).fold(
                onSuccess = { syncManager.fullSync(it.tenantId) },
                onFailure = { e ->
                    Timber.e(e, "注册失败")
                    _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "注册失败，请检查网络")) }
                }
            )
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val tenantId = authManager.currentTenantId() ?: return@launch
            syncManager.incrementalSync(tenantId)
        }
    }

    fun logout() { syncManager.logout() }

    // ========== 数据备份与恢复 ==========

    private fun observeBackupState() {
        viewModelScope.launch {
            backupManager.backupStatus.collect { status ->
                _uiState.update { it.copy(backupStatus = status) }
            }
        }
        viewModelScope.launch {
            backupManager.backupMessage.collect { msg ->
                _uiState.update { it.copy(backupMessage = msg) }
            }
        }
        viewModelScope.launch {
            backupManager.backupRecords.collect { records ->
                _uiState.update { it.copy(backupRecords = records) }
            }
        }
    }

    /**
     * 上传本地数据备份到云端
     */
    fun uploadBackup() {
        viewModelScope.launch {
            val result = backupManager.uploadBackup()
            // 操作完成后（无论成功或失败）都重置状态，使按钮恢复正常
            backupManager.clearStatus()
            // 如果失败，状态重置后显示错误消息由 UI 层处理
            result.onFailure { e ->
                Timber.e(e, "备份失败")
            }
        }
    }

    /**
     * 获取云端备份列表
     */
    fun fetchBackupList() {
        viewModelScope.launch {
            backupManager.clearStatus()
            backupManager.fetchBackupList()
        }
    }

    /**
     * 从云端恢复备份
     */
    fun restoreBackup(backupId: Int) {
        viewModelScope.launch {
            backupManager.downloadAndRestore(backupId)
        }
    }

    /**
     * 删除云端备份
     */
    fun deleteBackup(backupId: Int) {
        viewModelScope.launch {
            backupManager.deleteBackup(backupId)
        }
    }

    /**
     * 清除备份状态
     */
    fun clearBackupStatus() {
        backupManager.clearStatus()
    }
}
