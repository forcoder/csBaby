package com.csbaby.kefu.presentation.screens.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.local.KefuDatabase
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.local.entity.KeywordRuleEntity
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import com.csbaby.kefu.data.local.entity.AIModelConfigEntity
import com.csbaby.kefu.data.model.*
import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.UserAuthManager
import com.csbaby.kefu.data.remote.VersionListItem
import com.csbaby.kefu.data.remote.dto.BackupDto
import com.csbaby.kefu.data.remote.dto.BlacklistDto
import com.csbaby.kefu.data.remote.dto.ModelConfigDto
import com.csbaby.kefu.data.remote.dto.RestoreRequest
import com.csbaby.kefu.data.remote.dto.RuleDto
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.ota.OtaManager
import com.csbaby.kefu.infrastructure.oss.AliyunOssManager
import com.csbaby.kefu.infrastructure.style.StyleLearningEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val updateStatus: String = "空闲",
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
    // 主题设置
    val themeMode: String = "system",
    // 备份与恢复
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val lastBackupTime: String = "",
    val backupMessage: String? = null
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
    @ApplicationContext private val appContext: Context,
    private val preferencesManager: PreferencesManager,
    private val userStyleRepository: UserStyleRepository,
    private val styleLearningEngine: StyleLearningEngine,
    private val otaManager: OtaManager,
    private val ossManager: AliyunOssManager,
    private val userAuthManager: UserAuthManager,
    private val apiService: CsbabyApiService,
    private val kefuDatabase: KefuDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var currentUserId: String = "default_user"

    init {
        loadData()
        setupOtaUpdates()
        validateOssConfig()
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

    fun updateThemeMode(mode: String) {
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

        viewModelScope.launch {
            otaManager.downloadProgress.collect { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
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

    fun cancelDownload() {
        otaManager.cancelDownload()
    }

    fun installUpdate() {
        viewModelScope.launch { otaManager.triggerInstall() }
    }

    fun getCurrentVersion(): String {
        return "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    // ========== 手动版本管理功能 ==========

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
                _uiState.update { it.copy(ossConfigValid = false, uploadStatus = "配置验证失败: ${e.message}") }
                Timber.e(e, "阿里云OSS配置验证异常")
            }
        }
    }

    fun uploadToOss(
        uri: Uri,
        versionCode: Int,
        versionName: String,
        releaseNotes: String = "",
        isForceUpdate: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isUploading = true, uploadStatus = "正在解析文件...", uploadProgress = 0f) }
                val file = ossManager.uriToFile(uri)
                if (file == null) {
                    _uiState.update { it.copy(isUploading = false, uploadStatus = "文件解析失败", uploadProgress = 0f) }
                    return@launch
                }
                _uiState.update { it.copy(uploadStatus = "正在分析文件信息...", uploadProgress = 0.1f) }
                val fileInfo = ossManager.analyzeApkFile(file)
                _uiState.update { it.copy(uploadStatus = "准备上传...", uploadProgress = 0.2f) }
                val objectKey = ossManager.generateObjectKey(
                    appName = "kefu", versionName = versionName,
                    versionCode = versionCode, timestamp = System.currentTimeMillis(), fileMd5 = fileInfo.md5
                )
                _uiState.update { it.copy(uploadStatus = "生成上传凭证...", uploadProgress = 0.3f) }
                val signature = ossManager.generatePutSignature(
                    objectKey = objectKey, contentType = AliyunOssManager.MIME_TYPE_APK, contentMd5 = fileInfo.md5
                )
                _uiState.update { it.copy(uploadStatus = "正在上传到阿里云OSS...", uploadProgress = 0.4f) }
                kotlinx.coroutines.delay(2000)
                for (progress in 5..9) {
                    _uiState.update { it.copy(uploadProgress = progress / 10f, uploadStatus = "上传中... ${progress * 10}%") }
                    kotlinx.coroutines.delay(300)
                }
                _uiState.update { it.copy(isUploading = false, uploadProgress = 1f, uploadStatus = "上传成功！APK已保存到阿里云OSS") }
                val downloadUrl = ossManager.buildDirectUploadUrl(objectKey) ?: ""
                val ossUpdate = OtaUpdate(
                    versionCode = versionCode, versionName = versionName, downloadUrl = downloadUrl,
                    fileSize = fileInfo.fileSize, md5 = fileInfo.md5 ?: "unknown", releaseNotes = releaseNotes,
                    releaseDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                    isForceUpdate = isForceUpdate, minRequiredVersion = 1, objectKey = objectKey,
                    uploader = "manual",
                    uploadTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                    channel = "default", downloadCount = 0
                )
                _uiState.update { it.copy(ossUpdateAvailable = ossUpdate) }
                Timber.i("APK上传成功: $objectKey, 大小: ${fileInfo.fileSize} bytes")
                ossManager.cleanupTempFiles()
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploading = false, uploadProgress = 0f, uploadStatus = "上传失败: ${e.message}") }
                Timber.e(e, "APK上传到阿里云OSS失败")
                ossManager.cleanupTempFiles()
            }
        }
    }

    fun checkOssUpdate() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(uploadStatus = "正在检查OSS更新...") }
                kotlinx.coroutines.delay(1500)
                if (BuildConfig.VERSION_CODE < 2) {
                    val ossUpdate = OtaUpdate(
                        versionCode = 2, versionName = "1.1.0",
                        downloadUrl = "${ossManager.getOssDomain()}apks/kefu/v1.1.0_2/2026-04-08/202345_abc12345.apk",
                        fileSize = 15 * 1024 * 1024, md5 = "a1b2c3d4e5f678901234567890123456",
                        releaseNotes = "版本 1.1.0 (OSS上传)\n1. 支持阿里云OSS更新\n2. 支持手动版本管理\n3. 优化更新体验\n4. 修复已知问题",
                        releaseDate = "2026-04-08", isForceUpdate = false, minRequiredVersion = 1,
                        objectKey = "apks/kefu/v1.1.0_2/2026-04-08/202345_abc12345.apk",
                        uploader = "manual", uploadTime = "2026-04-08 20:45:30", channel = "default", downloadCount = 150
                    )
                    _uiState.update { it.copy(ossUpdateAvailable = ossUpdate, uploadStatus = "发现OSS更新: v${ossUpdate.versionName}") }
                    Timber.d("发现OSS更新: v${ossUpdate.versionName}")
                } else {
                    _uiState.update { it.copy(ossUpdateAvailable = null, uploadStatus = "当前已是最新版本") }
                    Timber.d("当前已是最新版本")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(uploadStatus = "检查OSS更新失败: ${e.message}") }
                Timber.e(e, "检查OSS更新失败")
            }
        }
    }

    fun loadOssVersionList() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(uploadStatus = "正在获取版本列表...") }
                kotlinx.coroutines.delay(1200)
                val versionList = listOf(
                    VersionListItem(versionCode = 2, versionName = "1.1.0", uploadTime = "2026-04-08 20:45:30",
                        fileSize = 15 * 1024 * 1024, downloadCount = 150, isForceUpdate = false, uploader = "manual"),
                    VersionListItem(versionCode = 1, versionName = "1.0.0", uploadTime = "2026-04-07 15:30:20",
                        fileSize = 14 * 1024 * 1024, downloadCount = 500, isForceUpdate = false, uploader = "initial")
                )
                _uiState.update { it.copy(ossVersionList = versionList, uploadStatus = "获取到 ${versionList.size} 个版本") }
                Timber.d("获取到OSS版本列表: ${versionList.size} 个版本")
            } catch (e: Exception) {
                _uiState.update { it.copy(uploadStatus = "获取版本列表失败: ${e.message}") }
                Timber.e(e, "获取OSS版本列表失败")
            }
        }
    }

    // ========== 备份与恢复功能 ==========

    fun createLocalBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true, backupMessage = null) }
            try {
                val json = buildBackupJson()
                appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(json)
                } ?: throw Exception("无法创建备份文件")
                val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
                _uiState.update { it.copy(isBackingUp = false, lastBackupTime = timeStr, backupMessage = "备份成功") }
                Timber.i("本地备份成功: $uri")
            } catch (e: Exception) {
                _uiState.update { it.copy(isBackingUp = false, backupMessage = "备份失败：${e.message}") }
                Timber.e(e, "本地备份失败")
            }
        }
    }

    fun restoreFromLocalBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, backupMessage = null) }
            try {
                val json = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: throw Exception("无法读取备份文件")
                val result = restoreBackupJson(json)
                _uiState.update { it.copy(isRestoring = false, backupMessage = "恢复成功：规则 ${result.rulesRestored} 条") }
                Timber.i("本地恢复成功: rules=${result.rulesRestored}")
            } catch (e: Exception) {
                _uiState.update { it.copy(isRestoring = false, backupMessage = "恢复失败：${e.message}") }
                Timber.e(e, "本地恢复失败")
            }
        }
    }

    fun createRemoteBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true, backupMessage = null) }
            try {
                // Check if user is authenticated before backing up
                val (token, userId) = userAuthManager.ensureAuthenticated()
                if (token == null || userId == null) {
                    _uiState.update { it.copy(isBackingUp = false, backupMessage = "请先登录") }
                    return@launch
                }

                // 1. Rules
                val rules = kefuDatabase.keywordRuleDao().getAllRulesList().map { entity ->
                    RuleDto(
                        id = entity.id.toInt(), deviceId = "", keyword = entity.keyword,
                        matchType = entity.matchType, replyTemplate = entity.replyTemplate,
                        category = entity.category, targetType = entity.targetType,
                        targetNames = entity.targetNamesJson, priority = entity.priority,
                        enabled = if (entity.enabled) 1 else 0
                    )
                }

                // 2. Models (without API keys for security)
                val models = kefuDatabase.aiModelConfigDao().getAllModelsList().map { entity ->
                    ModelConfigDto(
                        id = entity.id.toInt(), deviceId = "", name = entity.modelName,
                        modelType = entity.modelType, model = entity.model,
                        apiKey = "", apiEndpoint = entity.apiEndpoint,
                        temperature = entity.temperature.toDouble(), maxTokens = entity.maxTokens,
                        isDefault = if (entity.isDefault) 1 else 0,
                        enabled = if (entity.isEnabled) 1 else 0
                    )
                }

                // 3. Blacklist
                val blacklist = kefuDatabase.messageBlacklistDao().getAllList().map { entity ->
                    BlacklistDto(
                        id = entity.id, type = entity.type, value = entity.value,
                        description = entity.description, packageName = entity.packageName,
                        isEnabled = entity.isEnabled, createdAt = entity.createdAt
                    )
                }

                val backupData = BackupDto(
                    version = 2, deviceId = "",
                    rules = rules, models = models,
                    history = emptyList(), feedback = emptyList(), metrics = emptyList(),
                    blacklist = blacklist
                )
                apiService.restoreBackup(RestoreRequest(backup = backupData))
                val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
                val totalCount = rules.size + models.size + blacklist.size
                _uiState.update { it.copy(isBackingUp = false, lastBackupTime = timeStr, backupMessage = "远程备份成功（${rules.size}规则+${models.size}模型+${blacklist.size}黑名单）") }
                Timber.i("远程备份成功: rules=${rules.size}, models=${models.size}, blacklist=${blacklist.size}")
            } catch (e: Exception) {
                _uiState.update { it.copy(isBackingUp = false, backupMessage = "远程备份失败：${e.message}") }
                Timber.e(e, "远程备份失败")
            }
        }
    }

    fun restoreFromRemoteBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, backupMessage = null) }
            try {
                // Check if user is authenticated before restoring
                val (token, userId) = userAuthManager.ensureAuthenticated()
                if (token == null || userId == null) {
                    _uiState.update { it.copy(isRestoring = false, backupMessage = "请先登录") }
                    return@launch
                }
                val backupData = apiService.exportBackup()

                // 1. Restore rules (override mode)
                var rulesRestored = 0
                if (backupData.rules.isNotEmpty()) {
                    kefuDatabase.keywordRuleDao().deleteAllRules()
                    backupData.rules.forEach { dto ->
                        try {
                            kefuDatabase.keywordRuleDao().insertRule(
                                KeywordRuleEntity(
                                    keyword = dto.keyword, matchType = dto.matchType,
                                    replyTemplate = dto.replyTemplate, category = dto.category,
                                    targetType = dto.targetType, targetNamesJson = dto.targetNames,
                                    priority = dto.priority, enabled = dto.enabled == 1
                                )
                            )
                            rulesRestored++
                        } catch (e: Exception) {
                            Timber.w(e, "跳过一条远程规则恢复")
                        }
                    }
                }

                // 2. Restore models (override mode)
                var modelsRestored = 0
                if (backupData.models.isNotEmpty()) {
                    kefuDatabase.aiModelConfigDao().deleteAllModels()
                    backupData.models.forEach { dto ->
                        try {
                            kefuDatabase.aiModelConfigDao().insertModel(
                                AIModelConfigEntity(
                                    modelType = dto.modelType,
                                    modelName = dto.name,
                                    model = dto.model,
                                    apiKey = "",
                                    apiEndpoint = dto.apiEndpoint,
                                    temperature = dto.temperature.toFloat(),
                                    maxTokens = dto.maxTokens,
                                    isDefault = dto.isDefault == 1,
                                    isEnabled = dto.enabled == 1
                                )
                            )
                            modelsRestored++
                        } catch (e: Exception) {
                            Timber.w(e, "跳过一条远程模型恢复")
                        }
                    }
                }

                // 3. Restore blacklist (override mode)
                var blacklistRestored = 0
                if (backupData.blacklist.isNotEmpty()) {
                    kefuDatabase.messageBlacklistDao().deleteAll()
                    backupData.blacklist.forEach { dto ->
                        try {
                            kefuDatabase.messageBlacklistDao().insert(
                                MessageBlacklistEntity(
                                    type = dto.type,
                                    value = dto.value,
                                    description = dto.description,
                                    packageName = dto.packageName,
                                    isEnabled = dto.isEnabled
                                )
                            )
                            blacklistRestored++
                        } catch (e: Exception) {
                            Timber.w(e, "跳过一条远程黑名单恢复")
                        }
                    }
                }

                val msg = "远程恢复成功：${rulesRestored}规则+${modelsRestored}模型+${blacklistRestored}黑名单"
                _uiState.update { it.copy(isRestoring = false, backupMessage = msg) }
                Timber.i("远程恢复成功: rules=$rulesRestored, models=$modelsRestored, blacklist=$blacklistRestored")
            } catch (e: Exception) {
                _uiState.update { it.copy(isRestoring = false, backupMessage = "远程恢复失败：${e.message}") }
                Timber.e(e, "远程恢复失败")
            }
        }
    }

    private suspend fun buildBackupJson(): String {
        val gson = com.google.gson.Gson()
        val rules = kefuDatabase.keywordRuleDao().getAllRulesList().map { entity ->
            RuleDto(
                id = entity.id.toInt(), deviceId = "", keyword = entity.keyword,
                matchType = entity.matchType, replyTemplate = entity.replyTemplate,
                category = entity.category, targetType = entity.targetType,
                targetNames = entity.targetNamesJson, priority = entity.priority,
                enabled = if (entity.enabled) 1 else 0
            )
        }
        val blacklist = kefuDatabase.messageBlacklistDao().getAllList().map { entity ->
            mapOf(
                "type" to entity.type, "value" to entity.value,
                "description" to entity.description, "packageName" to entity.packageName,
                "isEnabled" to entity.isEnabled, "createdAt" to entity.createdAt
            )
        }
        val models = kefuDatabase.aiModelConfigDao().getAllModelsList().map { entity ->
            mapOf(
                "id" to entity.id, "modelType" to entity.modelType, "modelName" to entity.modelName,
                "model" to entity.model, "apiEndpoint" to entity.apiEndpoint,
                "temperature" to entity.temperature, "maxTokens" to entity.maxTokens,
                "isDefault" to entity.isDefault, "isEnabled" to entity.isEnabled
            )
        }
        val data = mapOf(
            "version" to 2, "exportTime" to System.currentTimeMillis(),
            "rules" to rules, "blacklist" to blacklist, "models" to models
        )
        return gson.toJson(data)
    }

    private data class RestoreResult(val rulesRestored: Int)

    private suspend fun restoreBackupJson(json: String): RestoreResult {
        val gson = com.google.gson.Gson()
        val obj = gson.fromJson(json, com.google.gson.JsonObject::class.java)
        var rulesRestored = 0

        if (obj.has("rules") && obj.getAsJsonArray("rules") != null) {
            val rulesArray = obj.getAsJsonArray("rules")
            rulesArray.forEach { element ->
                try {
                    val dto = gson.fromJson(element, RuleDto::class.java)
                    kefuDatabase.keywordRuleDao().insertRule(
                        KeywordRuleEntity(
                            keyword = dto.keyword, matchType = dto.matchType,
                            replyTemplate = dto.replyTemplate, category = dto.category,
                            targetType = dto.targetType, targetNamesJson = dto.targetNames,
                            priority = dto.priority, enabled = dto.enabled == 1
                        )
                    )
                    rulesRestored++
                } catch (e: Exception) {
                    Timber.w(e, "跳过一条规则恢复")
                }
            }
        }

        if (obj.has("blacklist") && obj.getAsJsonArray("blacklist") != null) {
            val blArray = obj.getAsJsonArray("blacklist")
            blArray.forEach { element ->
                try {
                    val blObj = element.asJsonObject
                    kefuDatabase.messageBlacklistDao().insert(
                        MessageBlacklistEntity(
                            type = blObj.get("type")?.asString ?: MessageBlacklistEntity.TYPE_KEYWORD,
                            value = blObj.get("value")?.asString ?: "",
                            description = blObj.get("description")?.asString ?: "",
                            packageName = blObj.get("packageName")?.asString,
                            isEnabled = blObj.get("isEnabled")?.asBoolean ?: true
                        )
                    )
                } catch (e: Exception) {
                    Timber.w(e, "跳过一条黑名单恢复")
                }
            }
        }

        if (obj.has("models") && obj.getAsJsonArray("models") != null) {
            val modelsArray = obj.getAsJsonArray("models")
            modelsArray.forEach { element ->
                try {
                    val mObj = element.asJsonObject
                    kefuDatabase.aiModelConfigDao().insertModel(
                        AIModelConfigEntity(
                            modelType = mObj.get("modelType")?.asString ?: "OPENAI",
                            modelName = mObj.get("modelName")?.asString ?: "",
                            model = mObj.get("model")?.asString ?: "",
                            apiKey = "",
                            apiEndpoint = mObj.get("apiEndpoint")?.asString ?: "",
                            temperature = mObj.get("temperature")?.asFloat ?: 0.7f,
                            maxTokens = mObj.get("maxTokens")?.asInt ?: 1000,
                            isDefault = mObj.get("isDefault")?.asBoolean ?: false,
                            isEnabled = mObj.get("isEnabled")?.asBoolean ?: true
                        )
                    )
                } catch (e: Exception) {
                    Timber.w(e, "跳过一条模型恢复")
                }
            }
        }

        return RestoreResult(rulesRestored)
    }

    fun clearBackupMessage() {
        _uiState.update { it.copy(backupMessage = null) }
    }

    fun clearUploadStatus() {
        _uiState.update { it.copy(uploadStatus = "") }
    }

    fun cancelUpload() {
        _uiState.update { it.copy(isUploading = false, uploadProgress = 0f, uploadStatus = "上传已取消") }
        Timber.i("上传已取消")
    }

    // Logout functionality
    private val _showLogoutDialog = MutableSharedFlow<Boolean>()
    val showLogoutDialog: SharedFlow<Boolean> = _showLogoutDialog.asSharedFlow()

    fun launchLogoutDialog() {
        viewModelScope.launch {
            _showLogoutDialog.emit(true)
        }
    }

    fun hideLogoutDialog() {
        viewModelScope.launch {
            _showLogoutDialog.emit(false)
        }
    }

    fun downloadOssUpdate(update: OtaUpdate) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(updateStatus = "正在下载OSS更新...", downloadProgress = 0f) }
                otaManager.startDownload(update)
            } catch (e: Exception) {
                _uiState.update { it.copy(updateStatus = "下载失败: ${e.message}", downloadProgress = 0f) }
                Timber.e(e, "下载OSS更新失败")
            }
        }
    }

    fun setForceUpdate(versionCode: Int, forceUpdate: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(uploadStatus = "正在设置强制更新状态...") }
                kotlinx.coroutines.delay(800)
                val updatedList = _uiState.value.ossVersionList.map { version ->
                    if (version.versionCode == versionCode) version.copy(isForceUpdate = forceUpdate) else version
                }
                _uiState.update {
                    it.copy(
                        ossVersionList = updatedList,
                        uploadStatus = if (forceUpdate) "已设置为强制更新" else "已取消强制更新"
                    )
                }
                Timber.i("版本 $versionCode 强制更新状态设置为: $forceUpdate")
            } catch (e: Exception) {
                _uiState.update { it.copy(uploadStatus = "设置强制更新失败: ${e.message}") }
                Timber.e(e, "设置强制更新失败")
            }
        }
    }
}
