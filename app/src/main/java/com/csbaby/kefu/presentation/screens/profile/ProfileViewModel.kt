package com.csbaby.kefu.presentation.screens.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.model.BackupRecord
import com.csbaby.kefu.data.model.BackupStatus
import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.domain.model.SyncState
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.domain.usecase.auth.GetCurrentTenantIdUseCase
import com.csbaby.kefu.domain.usecase.auth.ObserveAuthStateUseCase
import com.csbaby.kefu.domain.usecase.backup.ClearBackupStatusUseCase
import com.csbaby.kefu.domain.usecase.backup.DeleteBackupUseCase
import com.csbaby.kefu.domain.usecase.backup.FetchBackupListUseCase
import com.csbaby.kefu.domain.usecase.backup.ObserveBackupMessageUseCase
import com.csbaby.kefu.domain.usecase.backup.ObserveBackupRecordsUseCase
import com.csbaby.kefu.domain.usecase.backup.ObserveBackupStatusUseCase
import com.csbaby.kefu.domain.usecase.backup.RestoreBackupUseCase
import com.csbaby.kefu.domain.usecase.backup.UploadBackupUseCase
import com.csbaby.kefu.domain.usecase.ota.CancelDownloadUseCase
import com.csbaby.kefu.domain.usecase.ota.CheckForUpdateUseCase
import com.csbaby.kefu.domain.usecase.ota.ObserveAvailableUpdateUseCase
import com.csbaby.kefu.domain.usecase.ota.ObserveOtaErrorUseCase
import com.csbaby.kefu.domain.usecase.ota.ObserveOtaStatusUseCase
import com.csbaby.kefu.domain.usecase.ota.StartDownloadUpdateUseCase
import com.csbaby.kefu.domain.usecase.stats.GetDataStatsUseCase
import com.csbaby.kefu.domain.usecase.style.UpdateStyleParametersUseCase
import com.csbaby.kefu.domain.usecase.sync.IsLoggedInUseCase
import com.csbaby.kefu.domain.usecase.sync.LoginUseCase
import com.csbaby.kefu.domain.usecase.sync.LogoutUseCase
import com.csbaby.kefu.domain.usecase.sync.ObserveLastSyncTimeUseCase
import com.csbaby.kefu.domain.usecase.sync.ObserveSyncQueueUseCase
import com.csbaby.kefu.domain.usecase.sync.ObserveSyncStateUseCase
import com.csbaby.kefu.domain.usecase.sync.RegisterUseCase
import com.csbaby.kefu.domain.usecase.sync.SyncNowUseCase
import com.csbaby.kefu.domain.usecase.sync.TriggerSyncUseCase
import com.csbaby.kefu.presentation.theme.ThemeMode
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
    val currentUserName: String? = null,
    val pendingSyncCount: Int = 0,
    val lastSyncTime: Long = 0L,
    val syncStats: String = "",
    val lastSyncStats: String = "",
    val dataStats: DataStats = DataStats(),
    val backupStatus: BackupStatus = BackupStatus.IDLE,
    val backupMessage: String = "",
    val backupRecords: List<BackupRecord> = emptyList(),
    val themeMode: String = "system"
)

data class OtaUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val fileSize: String,
    val releaseNotes: String,
    val isForceUpdate: Boolean = false
)

data class DataStats(
    val knowledgeCount: Int = 0,
    val blacklistCount: Int = 0,
    val modelCount: Int = 0,
    val appCount: Int = 0,
    val scenarioCount: Int = 0
) {
    fun toDisplayString(): String {
        val parts = mutableListOf<String>()
        if (knowledgeCount > 0) parts.add("知识库${knowledgeCount}条")
        if (blacklistCount > 0) parts.add("黑名单${blacklistCount}条")
        if (modelCount > 0) parts.add("模型${modelCount}条")
        if (appCount > 0) parts.add("监控应用${appCount}条")
        if (scenarioCount > 0) parts.add("场景${scenarioCount}条")
        return parts.joinToString("、")
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val userStyleRepository: UserStyleRepository,

    // 同步
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val syncNowUseCase: SyncNowUseCase,
    private val isLoggedInUseCase: IsLoggedInUseCase,
    private val observeSyncStateUseCase: ObserveSyncStateUseCase,
    private val observeSyncQueueUseCase: ObserveSyncQueueUseCase,
    private val observeLastSyncTimeUseCase: ObserveLastSyncTimeUseCase,
    private val triggerSyncUseCase: TriggerSyncUseCase,

    // 认证
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val getCurrentTenantIdUseCase: GetCurrentTenantIdUseCase,

    // 备份
    private val uploadBackupUseCase: UploadBackupUseCase,
    private val fetchBackupListUseCase: FetchBackupListUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val deleteBackupUseCase: DeleteBackupUseCase,
    private val clearBackupStatusUseCase: ClearBackupStatusUseCase,
    private val observeBackupStatusUseCase: ObserveBackupStatusUseCase,
    private val observeBackupMessageUseCase: ObserveBackupMessageUseCase,
    private val observeBackupRecordsUseCase: ObserveBackupRecordsUseCase,

    // OTA
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val startDownloadUpdateUseCase: StartDownloadUpdateUseCase,
    private val cancelDownloadUseCase: CancelDownloadUseCase,
    private val observeOtaStatusUseCase: ObserveOtaStatusUseCase,
    private val observeAvailableUpdateUseCase: ObserveAvailableUpdateUseCase,
    private val observeOtaErrorUseCase: ObserveOtaErrorUseCase,

    // 风格
    private val updateStyleParametersUseCase: UpdateStyleParametersUseCase,

    // 统计
    private val getDataStatsUseCase: GetDataStatsUseCase
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
        loadDataStats()
    }

    private fun loadDataStats() {
        viewModelScope.launch {
            val tenantId = getCurrentTenantIdUseCase() ?: return@launch
            val stats = getDataStatsUseCase(tenantId)
            _uiState.update {
                it.copy(
                    dataStats = DataStats(
                        knowledgeCount = stats.knowledgeCount,
                        blacklistCount = stats.blacklistCount,
                        modelCount = stats.modelCount,
                        appCount = stats.appCount,
                        scenarioCount = stats.scenarioCount
                    )
                )
            }
        }
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            observeSyncStateUseCase().collect { state ->
                _uiState.update {
                    it.copy(
                        syncState = state,
                        // BUG-R8 续: 空 stats 不覆盖已有非空 stats,
                        // 避免二次 fullSync 触发的空 stats 覆盖首次的具体同步内容
                        syncStats = if (state is SyncState.Success) state.stats.ifEmpty { it.syncStats } else it.syncStats,
                        lastSyncStats = if (state is SyncState.Success) state.stats.ifEmpty { it.lastSyncStats } else it.lastSyncStats
                    )
                }
            }
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            observeAuthStateUseCase().collect { auth ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = auth != null,
                        currentTenantId = auth?.tenantId,
                        currentUserName = auth?.displayName?.takeIf { it.isNotBlank() }
                    )
                }
            }
        }
    }

    private fun observeSyncQueue() {
        viewModelScope.launch {
            observeSyncQueueUseCase().collect { count ->
                _uiState.update { it.copy(pendingSyncCount = count) }
            }
        }
    }

    private fun observeLastSyncTime() {
        viewModelScope.launch {
            observeLastSyncTimeUseCase().collect { time ->
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
                        autoSendEnabled = prefs.autoSendEnabled,
                        themeMode = prefs.themeMode
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
            updateStyleParametersUseCase(userId = currentUserId, formality = value)
            _uiState.update { it.copy(formalityLevel = value) }
            triggerAutoSync()
        }
    }

    fun updateEnthusiasm(value: Float) {
        viewModelScope.launch {
            updateStyleParametersUseCase(userId = currentUserId, enthusiasm = value)
            _uiState.update { it.copy(enthusiasmLevel = value) }
            triggerAutoSync()
        }
    }

    fun updateProfessionalism(value: Float) {
        viewModelScope.launch {
            updateStyleParametersUseCase(userId = currentUserId, professionalism = value)
            _uiState.update { it.copy(professionalismLevel = value) }
            triggerAutoSync()
        }
    }

    private fun triggerAutoSync() {
        if (isLoggedInUseCase()) {
            triggerSyncUseCase()
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

    fun toggleThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.updateThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    private fun setupOtaUpdates() {
        viewModelScope.launch {
            observeOtaStatusUseCase().collect { status ->
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
            observeAvailableUpdateUseCase().collect { update ->
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
            observeOtaErrorUseCase().collect { error ->
                _uiState.update { it.copy(errorMessage = error) }
            }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch { checkForUpdateUseCase() }
    }

    fun startDownloadUpdate() {
        // 取最新 availableUpdate 启动下载
        viewModelScope.launch {
            val current = observeAvailableUpdateUseCase().firstOrNull()
            current?.let { startDownloadUpdateUseCase(it) }
        }
    }

    fun cancelDownload() { cancelDownloadUseCase() }

    fun getCurrentVersion(): String = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    // 云端同步

    fun login(email: String, password: String) {
        Log.d("ProfileViewModel", "login() 调用: email=$email")
        viewModelScope.launch {
            loginUseCase(email, password).fold(
                onSuccess = { auth ->
                    Log.d("ProfileViewModel", "登录成功: tenantId=${auth.tenantId}")
                },
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
            registerUseCase(email, password, displayName).fold(
                onSuccess = { /* SyncManager.register 内部已触发 fullSync */ },
                onFailure = { e ->
                    Timber.e(e, "注册失败")
                    _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "注册失败，请检查网络")) }
                }
            )
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncNowUseCase().fold(
                onSuccess = { /* 状态由 observeSyncState 收集 */ },
                onFailure = { e ->
                    _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "同步失败")) }
                }
            )
        }
    }

    fun logout() { logoutUseCase() }

    // 数据备份与恢复

    private fun observeBackupState() {
        viewModelScope.launch {
            observeBackupStatusUseCase().collect { status ->
                _uiState.update { it.copy(backupStatus = status) }
            }
        }
        viewModelScope.launch {
            observeBackupMessageUseCase().collect { msg ->
                _uiState.update { it.copy(backupMessage = msg) }
            }
        }
        viewModelScope.launch {
            observeBackupRecordsUseCase().collect { records ->
                _uiState.update { it.copy(backupRecords = records) }
            }
        }
    }

    fun uploadBackup() {
        viewModelScope.launch {
            val result = uploadBackupUseCase()
            clearBackupStatusUseCase()
            result.onFailure { e ->
                Timber.e(e, "备份失败")
            }
        }
    }

    fun fetchBackupList() {
        viewModelScope.launch {
            clearBackupStatusUseCase()
            fetchBackupListUseCase()
        }
    }

    fun restoreBackup(backupId: Int) {
        viewModelScope.launch {
            restoreBackupUseCase(backupId)
        }
    }

    fun deleteBackup(backupId: Int) {
        viewModelScope.launch {
            deleteBackupUseCase(backupId)
        }
    }

    fun clearBackupStatus() {
        clearBackupStatusUseCase()
    }
}
