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
import com.csbaby.kefu.presentation.theme.ThemeMode
import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.local.dao.AppConfigDao
import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.dao.MessageBlacklistDao
import com.csbaby.kefu.data.local.dao.ScenarioDao
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
    val currentAccount: String? = null,
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
    private val styleLearningEngine: StyleLearningEngine,
    private val otaManager: OtaManager,
    private val syncManager: SyncManager,
    private val authManager: AuthManager,
    private val backupManager: BackupManager,
    private val keywordRuleDao: KeywordRuleDao,
    private val messageBlacklistDao: MessageBlacklistDao,
    private val aiModelConfigDao: AIModelConfigDao,
    private val appConfigDao: AppConfigDao,
    private val scenarioDao: ScenarioDao
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
            val tenantId = authManager.currentTenantId() ?: return@launch
            val knowledgeCount = runCatching { keywordRuleDao.getRuleCount() }.getOrDefault(0)
            val blacklistCount = runCatching { messageBlacklistDao.getEnabledCount() }.getOrDefault(0)
            val modelCount = aiModelConfigDao.getModelsByTenantSync(tenantId).size
            val appCount = appConfigDao.getAppsByTenantSync(tenantId).size
            val scenarioCount = scenarioDao.getScenariosByTenantSync(tenantId).size
            _uiState.update {
                it.copy(
                    dataStats = DataStats(
                        knowledgeCount = knowledgeCount,
                        blacklistCount = blacklistCount,
                        modelCount = modelCount,
                        appCount = appCount,
                        scenarioCount = scenarioCount
                    )
                )
            }
        }
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                _uiState.update {
                    it.copy(
                        syncState = state,
                        syncStats = if (state is SyncState.Success) state.stats else it.syncStats,
                        lastSyncStats = if (state is SyncState.Success) state.stats else it.lastSyncStats
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
                        currentTenantId = auth?.tenantId,
                        currentAccount = auth?.account
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

    fun toggleThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.updateThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
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

    // 云端同步

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
            val tenantId = authManager.currentTenantId()
            if (tenantId == null) {
                _uiState.update { it.copy(syncState = SyncState.Error("请先登录后再同步")) }
                return@launch
            }
            syncManager.incrementalSync(tenantId)
        }
    }

    fun logout() { syncManager.logout() }

    // 数据备份与恢复

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

    fun uploadBackup() {
        viewModelScope.launch {
            val result = backupManager.uploadBackup()
            backupManager.clearStatus()
            result.onFailure { e ->
                Timber.e(e, "备份失败")
            }
        }
    }

    fun fetchBackupList() {
        viewModelScope.launch {
            backupManager.clearStatus()
            backupManager.fetchBackupList()
        }
    }

    fun restoreBackup(backupId: Int) {
        viewModelScope.launch {
            backupManager.downloadAndRestore(backupId)
        }
    }

    fun deleteBackup(backupId: Int) {
        viewModelScope.launch {
            backupManager.deleteBackup(backupId)
        }
    }

    fun clearBackupStatus() {
        backupManager.clearStatus()
    }
}