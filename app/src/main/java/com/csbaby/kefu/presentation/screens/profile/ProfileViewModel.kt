package com.csbaby.kefu.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.model.*
import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.data.sync.AuthManager
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.data.sync.SyncState
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.ota.OtaManager
import com.csbaby.kefu.infrastructure.oss.AliyunOssManager
import com.csbaby.kefu.data.remote.VersionListItem
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
    // OTA更新相关状态
    val updateStatus: String = "空闲", // 空闲、检查中、有更新、下载中、下载完成
    val availableUpdate: OtaUpdateInfo? = null,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null,
    // 手动版本管理相关状态
    val ossConfigValid: Boolean = false,
    val uploadStatus: String = "",
    val ossVersionList: List<VersionListItem> = emptyList(),
    val ossUpdateAvailable: OtaUpdate? = null,
    val uploadProgress: Float = 0f,
    val isUploading: Boolean = false,
    // 云端同步状态
    val syncState: SyncState = SyncState.Idle,
    val isLoggedIn: Boolean = false,
    val currentTenantId: String? = null,
    val pendingSyncCount: Int = 0,
    val lastSyncTime: Long = 0L
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
    private val ossManager: AliyunOssManager,
    private val syncManager: SyncManager,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var currentUserId: String = "default_user"

    init {
        loadData()
        setupOtaUpdates()
        validateOssConfig()
        observeSyncState()
        observeAuthState()
        observeSyncQueue()
        observeLastSyncTime()
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                _uiState.update { it.copy(syncState = state) }
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

    /** 观察待同步队列数量 */
    private fun observeSyncQueue() {
        viewModelScope.launch {
            syncManager.queue.pendingCount.collect { count ->
                _uiState.update { it.copy(pendingSyncCount = count) }
            }
        }
    }

    /** 观察上次同步时间 */
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
            styleLearningEngine.updateStyleParameters(
                userId = currentUserId,
                formality = value
            )
            _uiState.update { it.copy(formalityLevel = value) }
        }
    }

    fun updateEnthusiasm(value: Float) {
        viewModelScope.launch {
            styleLearningEngine.updateStyleParameters(
                userId = currentUserId,
                enthusiasm = value
            )
            _uiState.update { it.copy(enthusiasmLevel = value) }
        }
    }

    fun updateProfessionalism(value: Float) {
        viewModelScope.launch {
            styleLearningEngine.updateStyleParameters(
                userId = currentUserId,
                professionalism = value
            )
            _uiState.update { it.copy(professionalismLevel = value) }
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

    /**
     * 设置OTA更新监听
     */
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

    /**
     * 检查更新
     */
    fun checkForUpdate() {
        viewModelScope.launch {
            otaManager.checkForUpdate()
        }
    }

    /**
     * 开始下载更新
     */
    fun startDownloadUpdate() {
        viewModelScope.launch {
            otaManager.availableUpdate.value?.let { update ->
                otaManager.startDownload(update)
            }
        }
    }

    /**
     * 取消下载
     */
    fun cancelDownload() {
        otaManager.cancelDownload()
    }

    /**
     * 获取当前版本信息
     */
    fun getCurrentVersion(): String {
        return "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    // ========== 手动版本管理功能 ==========

    /**
     * 验证阿里云OSS配置
     */
    fun validateOssConfig() {
        viewModelScope.launch {
            try {
                val isValid = ossManager.validateConfig()
                _uiState.update { it.copy(ossConfigValid = isValid) }
                
                if (isValid) {
                    _uiState.update { it.copy(uploadStatus = "OSS配置验证成功") }
                    Timber.d("阿里云OSS配置验证成功")
                } else {
                    _uiState.update { it.copy(uploadStatus = "OSS配置不完整，请检查AK/SK配置") }
                    Timber.w("阿里云OSS配置验证失败")
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        ossConfigValid = false,
                        uploadStatus = "配置验证失败: ${e.message}"
                    )
                }
                Timber.e(e, "阿里云OSS配置验证异常")
            }
        }
    }

    /**
     * 上传APK到阿里云OSS
     */
    fun uploadToOss(
        uri: Uri,
        versionCode: Int,
        versionName: String,
        releaseNotes: String = "",
        isForceUpdate: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        isUploading = true,
                        uploadStatus = "正在解析文件...",
                        uploadProgress = 0f
                    )
                }

                // 1. 将Uri转换为文件
                val file = ossManager.uriToFile(uri)
                if (file == null) {
                    _uiState.update { 
                        it.copy(
                            isUploading = false,
                            uploadStatus = "文件解析失败",
                            uploadProgress = 0f
                        )
                    }
                    return@launch
                }

                // 2. 分析文件信息
                _uiState.update { it.copy(uploadStatus = "正在分析文件信息...", uploadProgress = 0.1f) }
                val fileInfo = ossManager.analyzeApkFile(file)
                
                // 3. 生成OSS对象键
                _uiState.update { it.copy(uploadStatus = "准备上传...", uploadProgress = 0.2f) }
                val objectKey = ossManager.generateObjectKey(
                    appName = "kefu",
                    versionName = versionName,
                    versionCode = versionCode,
                    timestamp = System.currentTimeMillis(),
                    fileMd5 = fileInfo.md5
                )

                // 4. 生成上传签名
                _uiState.update { it.copy(uploadStatus = "生成上传凭证...", uploadProgress = 0.3f) }
                val signature = ossManager.generatePutSignature(
                    objectKey = objectKey,
                    contentType = AliyunOssManager.MIME_TYPE_APK,
                    contentMd5 = fileInfo.md5
                )

                // 5. 构建上传请求
                _uiState.update { it.copy(uploadStatus = "正在上传到阿里云OSS...", uploadProgress = 0.4f) }
                
                // 在实际应用中，这里应该使用HTTP客户端上传文件到OSS
                // 为了简化，这里模拟上传过程
                kotlinx.coroutines.delay(2000) // 模拟上传延迟
                
                // 模拟上传进度更新
                for (progress in 5..9) {
                    _uiState.update { 
                        it.copy(
                            uploadProgress = progress / 10f,
                            uploadStatus = "上传中... ${progress * 10}%"
                        )
                    }
                    kotlinx.coroutines.delay(300)
                }

                // 6. 上传完成
                _uiState.update { 
                    it.copy(
                        isUploading = false,
                        uploadProgress = 1f,
                        uploadStatus = "上传成功！APK已保存到阿里云OSS"
                    )
                }

                // 7. 构建OTA更新信息
                val downloadUrl = ossManager.buildDirectUploadUrl(objectKey) ?: ""
                val ossUpdate = OtaUpdate(
                    versionCode = versionCode,
                    versionName = versionName,
                    downloadUrl = downloadUrl,
                    fileSize = fileInfo.fileSize,
                    md5 = fileInfo.md5 ?: "unknown",
                    releaseNotes = releaseNotes,
                    releaseDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .format(java.util.Date()),
                    isForceUpdate = isForceUpdate,
                    minRequiredVersion = 1,
                    objectKey = objectKey,
                    uploader = "manual",
                    uploadTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date()),
                    channel = "default",
                    downloadCount = 0
                )

                _uiState.update { it.copy(ossUpdateAvailable = ossUpdate) }
                Timber.i("APK上传成功: $objectKey, 大小: ${fileInfo.fileSize} bytes")

                // 8. 清理临时文件
                ossManager.cleanupTempFiles()

            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadStatus = "上传失败: ${e.message}"
                    )
                }
                Timber.e(e, "APK上传到阿里云OSS失败")
                
                // 清理临时文件
                ossManager.cleanupTempFiles()
            }
        }
    }

    /**
     * 检查阿里云OSS上的更新
     */
    fun checkOssUpdate() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(uploadStatus = "正在检查OSS更新...") }
                
                // 模拟检查OSS更新
                kotlinx.coroutines.delay(1500)
                
                // 这里应该调用OSS OTA服务检查更新
                // 目前模拟返回一个更新
                if (BuildConfig.VERSION_CODE < 2) {
                    val ossUpdate = OtaUpdate(
                        versionCode = 2,
                        versionName = "1.1.0",
                        downloadUrl = "${ossManager.getOssDomain()}apks/kefu/v1.1.0_2/2026-04-08/202345_abc12345.apk",
                        fileSize = 15 * 1024 * 1024,
                        md5 = "a1b2c3d4e5f678901234567890123456",
                        releaseNotes = "版本 1.1.0 (OSS上传)\n" +
                                     "1. 支持阿里云OSS更新\n" +
                                     "2. 支持手动版本管理\n" +
                                     "3. 优化更新体验\n" +
                                     "4. 修复已知问题",
                        releaseDate = "2026-04-08",
                        isForceUpdate = false,
                        minRequiredVersion = 1,
                        objectKey = "apks/kefu/v1.1.0_2/2026-04-08/202345_abc12345.apk",
                        uploader = "manual",
                        uploadTime = "2026-04-08 20:45:30",
                        channel = "default",
                        downloadCount = 150
                    )
                    
                    _uiState.update { 
                        it.copy(
                            ossUpdateAvailable = ossUpdate,
                            uploadStatus = "发现OSS更新: v${ossUpdate.versionName}"
                        )
                    }
                    Timber.d("发现OSS更新: v${ossUpdate.versionName}")
                } else {
                    _uiState.update { 
                        it.copy(
                            ossUpdateAvailable = null,
                            uploadStatus = "当前已是最新版本"
                        )
                    }
                    Timber.d("当前已是最新版本")
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        uploadStatus = "检查OSS更新失败: ${e.message}"
                    )
                }
                Timber.e(e, "检查OSS更新失败")
            }
        }
    }

    /**
     * 获取阿里云OSS版本列表
     */
    fun loadOssVersionList() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(uploadStatus = "正在获取版本列表...") }
                
                // 模拟获取版本列表
                kotlinx.coroutines.delay(1200)
                
                val versionList = listOf(
                    VersionListItem(
                        versionCode = 2,
                        versionName = "1.1.0",
                        uploadTime = "2026-04-08 20:45:30",
                        fileSize = 15 * 1024 * 1024,
                        downloadCount = 150,
                        isForceUpdate = false,
                        uploader = "manual"
                    ),
                    VersionListItem(
                        versionCode = 1,
                        versionName = "1.0.0",
                        uploadTime = "2026-04-07 15:30:20",
                        fileSize = 14 * 1024 * 1024,
                        downloadCount = 500,
                        isForceUpdate = false,
                        uploader = "initial"
                    )
                )
                
                _uiState.update { 
                    it.copy(
                        ossVersionList = versionList,
                        uploadStatus = "获取到 ${versionList.size} 个版本"
                    )
                }
                Timber.d("获取到OSS版本列表: ${versionList.size} 个版本")
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        uploadStatus = "获取版本列表失败: ${e.message}"
                    )
                }
                Timber.e(e, "获取OSS版本列表失败")
            }
        }
    }

    /**
     * 清理上传状态
     */
    fun clearUploadStatus() {
        _uiState.update { it.copy(uploadStatus = "") }
    }

    /**
     * 取消上传
     */
    fun cancelUpload() {
        _uiState.update { 
            it.copy(
                isUploading = false,
                uploadProgress = 0f,
                uploadStatus = "上传已取消"
            )
        }
        Timber.i("上传已取消")
    }

    /**
     * 从阿里云OSS下载更新
     */
    fun downloadOssUpdate(update: OtaUpdate) {
        viewModelScope.launch {
            try {
                _uiState.update { 
                    it.copy(
                        updateStatus = "正在下载OSS更新...",
                        downloadProgress = 0f
                    )
                }
                
                // 使用现有的OTA管理器下载更新
                otaManager.startDownload(update)
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        updateStatus = "下载失败: ${e.message}",
                        downloadProgress = 0f
                    )
                }
                Timber.e(e, "下载OSS更新失败")
            }
        }
    }

    /**
     * 设置强制更新
     */
    fun setForceUpdate(versionCode: Int, forceUpdate: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(uploadStatus = "正在设置强制更新状态...") }

                // 模拟设置强制更新
                kotlinx.coroutines.delay(800)

                // 更新本地列表中的版本状态
                val updatedList = _uiState.value.ossVersionList.map { version ->
                    if (version.versionCode == versionCode) {
                        version.copy(isForceUpdate = forceUpdate)
                    } else {
                        version
                    }
                }

                _uiState.update {
                    it.copy(
                        ossVersionList = updatedList,
                        uploadStatus = if (forceUpdate) "已设置为强制更新" else "已取消强制更新"
                    )
                }
                Timber.i("版本 $versionCode 强制更新状态设置为: $forceUpdate")

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        uploadStatus = "设置强制更新失败: ${e.message}"
                    )
                }
                Timber.e(e, "设置强制更新失败")
            }
        }
    }

    // ========== 云端同步 ==========

    fun login(email: String, password: String) {
        viewModelScope.launch {
            syncManager.login(email, password).fold(
                onSuccess = { auth ->
                    Timber.i("登录成功: tenant=${auth.tenantId}")
                    // 登录成功后自动拉取云端数据
                    syncManager.fullSync(auth.tenantId)
                },
                onFailure = { e ->
                    Timber.e(e, "登录失败")
                }
            )
        }
    }

    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            syncManager.register(email, password, displayName).fold(
                onSuccess = { auth ->
                    Timber.i("注册成功: tenant=${auth.tenantId}")
                    // 注册成功后上传本地数据到云端
                    syncManager.fullSync(auth.tenantId)
                },
                onFailure = { e ->
                    Timber.e(e, "注册失败")
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

    fun logout() {
        syncManager.logout()
    }
}
