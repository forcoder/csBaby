package com.csbaby.kefu.infrastructure.backup

import android.content.Context
import android.os.Build
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.model.*
import com.csbaby.kefu.data.remote.SyncApiService
import com.csbaby.kefu.data.sync.AuthManager
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据备份管理器
 *
 * 负责将本地 Room 数据导出为 JSON 并上传到服务端，
 * 以及从服务端下载备份恢复到本地。
 *
 * 注意：备份恢复会覆盖本地同 tenant_id 的数据。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val keywordRuleDao: KeywordRuleDao,
    private val aiModelConfigDao: AIModelConfigDao,
    private val userStyleProfileDao: UserStyleProfileDao,
    private val appConfigDao: AppConfigDao,
    private val scenarioDao: ScenarioDao,
    private val replyHistoryDao: ReplyHistoryDao,
    private val messageBlacklistDao: MessageBlacklistDao
) {
    private val gson = Gson()

    // 通过 SyncApiService 访问备份 API（需要 JWT 认证）
    private var apiService: SyncApiService? = null

    fun setApiService(service: SyncApiService) {
        apiService = service
    }

    private val _backupStatus = MutableStateFlow(BackupStatus.IDLE)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    private val _backupMessage = MutableStateFlow("")
    val backupMessage: StateFlow<String> = _backupMessage.asStateFlow()

    private val _backupRecords = MutableStateFlow<List<BackupRecord>>(emptyList())
    val backupRecords: StateFlow<List<BackupRecord>> = _backupRecords.asStateFlow()

    // ========== 备份 ==========

    /**
     * 上传数据备份到服务端
     */
    suspend fun uploadBackup(): Result<BackupRecord> = withContext(Dispatchers.IO) {
        val api = apiService ?: return@withContext Result.failure(
            Exception("未登录，无法备份")
        )

        try {
            _backupStatus.value = BackupStatus.EXPORTING
            _backupMessage.value = "正在导出本地数据..."

            val tenantId = authManager.currentTenantId()
                ?: return@withContext Result.failure(Exception("未登录"))

            // 导出本地数据
            val content = exportLocalData(tenantId)
            val json = gson.toJson(content)
            val checksum = md5(json)

            _backupStatus.value = BackupStatus.UPLOADING
            _backupMessage.value = "正在上传到云端..."

            val request = BackupUploadRequest(
                deviceName = Build.MODEL,
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                data = content,
                checksum = checksum
            )

            val response = api.uploadBackup(request)
            if (response.isSuccess && response.data != null) {
                _backupStatus.value = BackupStatus.SUCCESS
                _backupMessage.value = "备份成功"
                Timber.d("备份成功: id=${response.data.id}")
                Result.success(response.data)
            } else {
                _backupStatus.value = BackupStatus.FAILED
                _backupMessage.value = response.message.ifEmpty { "备份失败" }
                Result.failure(Exception(_backupMessage.value))
            }
        } catch (e: Exception) {
            Timber.e(e, "备份失败")
            _backupStatus.value = BackupStatus.FAILED
            _backupMessage.value = "备份失败: ${e.message}"
            Result.failure(e)
        }
    }

    /**
     * 从服务端获取备份列表
     */
    suspend fun fetchBackupList(): Result<List<BackupRecord>> = withContext(Dispatchers.IO) {
        val api = apiService ?: return@withContext Result.failure(
            Exception("未登录，无法获取备份列表")
        )
        try {
            val response = api.getBackupList()
            if (response.isSuccess && response.data != null) {
                _backupRecords.value = response.data
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "获取备份列表失败" }))
            }
        } catch (e: Exception) {
            Timber.e(e, "获取备份列表失败")
            Result.failure(e)
        }
    }

    /**
     * 下载并恢复备份
     */
    suspend fun downloadAndRestore(backupId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val api = apiService ?: return@withContext Result.failure(
            Exception("未登录，无法下载备份")
        )

        try {
            _backupStatus.value = BackupStatus.DOWNLOADING
            _backupMessage.value = "正在下载备份..."

            val response = api.downloadBackup(backupId)
            if (response.isSuccess && response.data != null) {
                val backupData = response.data

                _backupStatus.value = BackupStatus.RESTORING
                _backupMessage.value = "正在恢复数据..."

                val tenantId = authManager.currentTenantId()
                    ?: return@withContext Result.failure(Exception("未登录"))

                restoreToLocal(backupData.data, tenantId)

                _backupStatus.value = BackupStatus.SUCCESS
                _backupMessage.value = "数据恢复完成"
                Timber.d("备份恢复成功: id=$backupId")
                Result.success(Unit)
            } else {
                _backupStatus.value = BackupStatus.FAILED
                _backupMessage.value = response.message.ifEmpty { "下载备份失败" }
                Result.failure(Exception(_backupMessage.value))
            }
        } catch (e: Exception) {
            Timber.e(e, "备份恢复失败")
            _backupStatus.value = BackupStatus.FAILED
            _backupMessage.value = "恢复失败: ${e.message}"
            Result.failure(e)
        }
    }

    /**
     * 删除服务端备份
     */
    suspend fun deleteBackup(backupId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val api = apiService ?: return@withContext Result.failure(Exception("未登录"))
        try {
            val response = api.deleteBackup(backupId)
            if (response.isSuccess) {
                // 刷新列表
                fetchBackupList()
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifEmpty { "删除失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 清除状态
     */
    fun clearStatus() {
        _backupStatus.value = BackupStatus.IDLE
        _backupMessage.value = ""
    }

    // ========== 数据导出 ==========

    private suspend fun exportLocalData(tenantId: String): BackupContent {
        val rules = keywordRuleDao.getRulesByTenantSync(tenantId)
        val models = aiModelConfigDao.getModelsByTenantSync(tenantId)
        val profile = userStyleProfileDao.getProfileByTenantIdSync(tenantId)
        val apps = appConfigDao.getAppsByTenantSync(tenantId)
        val scenarios = scenarioDao.getScenariosByTenantSync(tenantId)
        val replies = replyHistoryDao.getRepliesByTenantSync(tenantId)
        val blacklists = messageBlacklistDao.getByTenantSync(tenantId)

        return BackupContent(
            keywordRules = rules.map { entityToMap(it) },
            aiModelConfigs = models.map { entityToMap(it) },
            userStyleProfile = profile?.let { entityToMap(it) },
            appConfigs = apps.map { entityToMap(it) },
            scenarios = scenarios.map { entityToMap(it) },
            replyHistory = replies.map { entityToMap(it) },
            messageBlacklist = blacklists.map { entityToMap(it) }
        )
    }

    // ========== 数据恢复 ==========

    private suspend fun restoreToLocal(content: BackupContent?, tenantId: String) {
        if (content == null) return

        // 先清除该租户的现有数据
        keywordRuleDao.getRulesByTenant(tenantId).first().forEach { keywordRuleDao.deleteById(it.id) }
        aiModelConfigDao.getModelsByTenantSync(tenantId).forEach { aiModelConfigDao.deleteById(it.id) }
        appConfigDao.getAppsByTenantSync(tenantId).forEach { appConfigDao.deleteByPackage(it.packageName) }
        scenarioDao.getScenariosByTenantSync(tenantId).forEach { scenarioDao.deleteById(it.id) }
        replyHistoryDao.getRepliesByTenantSync(tenantId).forEach { replyHistoryDao.deleteById(it.id) }
        messageBlacklistDao.getByTenantSync(tenantId).forEach { messageBlacklistDao.deleteById(it.id) }

        // 写入备份数据
        for (map in content.keywordRules) {
            val entity = mapToKeywordRule(map, tenantId)
            if (entity != null) keywordRuleDao.insertRule(entity)
        }
        for (map in content.aiModelConfigs) {
            val entity = mapToAIModelConfig(map, tenantId)
            if (entity != null) aiModelConfigDao.insertModel(entity)
        }
        content.userStyleProfile?.let { map ->
            val entity = mapToStyleProfile(map, tenantId)
            if (entity != null) userStyleProfileDao.insertProfile(entity)
        }
        for (map in content.appConfigs) {
            val entity = mapToAppConfig(map, tenantId)
            if (entity != null) appConfigDao.insertApp(entity)
        }
        for (map in content.scenarios) {
            val entity = mapToScenario(map, tenantId)
            if (entity != null) scenarioDao.insertScenario(entity)
        }
        for (map in content.replyHistory) {
            val entity = mapToReplyHistory(map, tenantId)
            if (entity != null) replyHistoryDao.insertReply(entity)
        }
        for (map in content.messageBlacklist) {
            val entity = mapToMessageBlacklist(map, tenantId)
            if (entity != null) messageBlacklistDao.insert(entity)
        }

        Timber.d("备份恢复完成: rules=${content.keywordRules.size}, models=${content.aiModelConfigs.size}")
    }

    // ========== Entity ↔ Map 转换 ==========

    private fun entityToMap(entity: Any): Map<String, Any?> {
        val json = gson.toJson(entity)
        return gson.fromJson(json, Map::class.java) as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToKeywordRule(map: Map<String, Any?>, tenantId: String): com.csbaby.kefu.data.local.entity.KeywordRuleEntity? {
        return try {
            com.csbaby.kefu.data.local.entity.KeywordRuleEntity(
                id = (map["id"] as? Double)?.toLong() ?: return null,
                keyword = map["keyword"] as? String ?: "",
                matchType = map["matchType"] as? String ?: "CONTAINS",
                replyTemplate = map["replyTemplate"] as? String ?: "",
                category = map["category"] as? String ?: "",
                targetType = map["targetType"] as? String ?: "ALL",
                targetNamesJson = map["targetNamesJson"] as? String ?: "[]",
                priority = (map["priority"] as? Double)?.toInt() ?: 0,
                enabled = map["enabled"] as? Boolean ?: true,
                createdAt = (map["createdAt"] as? Double)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Double)?.toLong() ?: System.currentTimeMillis(),
                tenantId = tenantId,
                syncVersion = 0L,
                deleted = false
            )
        } catch (e: Exception) {
            Timber.e(e, "mapToKeywordRule failed")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToAIModelConfig(map: Map<String, Any?>, tenantId: String): com.csbaby.kefu.data.local.entity.AIModelConfigEntity? {
        return try {
            com.csbaby.kefu.data.local.entity.AIModelConfigEntity(
                id = (map["id"] as? Double)?.toLong() ?: return null,
                modelType = map["modelType"] as? String ?: "",
                modelName = map["modelName"] as? String ?: "",
                apiKey = map["apiKey"] as? String ?: "",
                apiEndpoint = map["apiEndpoint"] as? String ?: "",
                temperature = (map["temperature"] as? Double)?.toFloat() ?: 0.7f,
                maxTokens = (map["maxTokens"] as? Double)?.toInt() ?: 1000,
                isDefault = map["isDefault"] as? Boolean ?: false,
                isEnabled = map["isEnabled"] as? Boolean ?: true,
                monthlyCost = (map["monthlyCost"] as? Double) ?: 0.0,
                lastUsed = (map["lastUsed"] as? Double)?.toLong() ?: 0L,
                createdAt = (map["createdAt"] as? Double)?.toLong() ?: System.currentTimeMillis(),
                tenantId = tenantId,
                syncVersion = 0L,
                deleted = false
            )
        } catch (e: Exception) {
            Timber.e(e, "mapToAIModelConfig failed")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToStyleProfile(map: Map<String, Any?>, tenantId: String): com.csbaby.kefu.data.local.entity.UserStyleProfileEntity? {
        return try {
            com.csbaby.kefu.data.local.entity.UserStyleProfileEntity(
                userId = map["userId"] as? String ?: return null,
                formalityLevel = (map["formalityLevel"] as? Double)?.toFloat() ?: 0.5f,
                enthusiasmLevel = (map["enthusiasmLevel"] as? Double)?.toFloat() ?: 0.5f,
                professionalismLevel = (map["professionalismLevel"] as? Double)?.toFloat() ?: 0.5f,
                wordCountPreference = (map["wordCountPreference"] as? Double)?.toInt() ?: 50,
                commonPhrases = map["commonPhrases"] as? String ?: "",
                avoidPhrases = map["avoidPhrases"] as? String ?: "",
                learningSamples = (map["learningSamples"] as? Double)?.toInt() ?: 0,
                accuracyScore = (map["accuracyScore"] as? Double)?.toFloat() ?: 0f,
                lastTrained = (map["lastTrained"] as? Double)?.toLong() ?: 0L,
                createdAt = (map["createdAt"] as? Double)?.toLong() ?: System.currentTimeMillis(),
                tenantId = tenantId,
                syncVersion = 0L,
                deleted = false
            )
        } catch (e: Exception) {
            Timber.e(e, "mapToStyleProfile failed")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToAppConfig(map: Map<String, Any?>, tenantId: String): com.csbaby.kefu.data.local.entity.AppConfigEntity? {
        return try {
            com.csbaby.kefu.data.local.entity.AppConfigEntity(
                packageName = map["packageName"] as? String ?: return null,
                appName = map["appName"] as? String ?: "",
                iconUri = map["iconUri"] as? String,
                isMonitored = map["isMonitored"] as? Boolean ?: false,
                createdAt = (map["createdAt"] as? Double)?.toLong() ?: System.currentTimeMillis(),
                lastUsed = (map["lastUsed"] as? Double)?.toLong() ?: 0L,
                tenantId = tenantId,
                syncVersion = 0L,
                deleted = false
            )
        } catch (e: Exception) {
            Timber.e(e, "mapToAppConfig failed")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToScenario(map: Map<String, Any?>, tenantId: String): com.csbaby.kefu.data.local.entity.ScenarioEntity? {
        return try {
            com.csbaby.kefu.data.local.entity.ScenarioEntity(
                id = (map["id"] as? Double)?.toLong() ?: return null,
                name = map["name"] as? String ?: "",
                type = map["type"] as? String ?: "ALL_PROPERTIES",
                targetId = map["targetId"] as? String,
                description = map["description"] as? String,
                createdAt = (map["createdAt"] as? Double)?.toLong() ?: System.currentTimeMillis(),
                tenantId = tenantId,
                syncVersion = 0L,
                deleted = false
            )
        } catch (e: Exception) {
            Timber.e(e, "mapToScenario failed")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToReplyHistory(map: Map<String, Any?>, tenantId: String): com.csbaby.kefu.data.local.entity.ReplyHistoryEntity? {
        return try {
            com.csbaby.kefu.data.local.entity.ReplyHistoryEntity(
                id = (map["id"] as? Double)?.toLong() ?: return null,
                sourceApp = map["sourceApp"] as? String ?: "",
                originalMessage = map["originalMessage"] as? String ?: "",
                generatedReply = map["generatedReply"] as? String ?: "",
                finalReply = map["finalReply"] as? String ?: "",
                ruleMatchedId = (map["ruleMatchedId"] as? Double)?.toLong(),
                modelUsedId = (map["modelUsedId"] as? Double)?.toLong(),
                styleApplied = map["styleApplied"] as? Boolean ?: false,
                sendTime = (map["sendTime"] as? Double)?.toLong() ?: 0L,
                modified = map["modified"] as? Boolean ?: false,
                tenantId = tenantId,
                syncVersion = 0L,
                deleted = false
            )
        } catch (e: Exception) {
            Timber.e(e, "mapToReplyHistory failed")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToMessageBlacklist(map: Map<String, Any?>, tenantId: String): com.csbaby.kefu.data.local.entity.MessageBlacklistEntity? {
        return try {
            com.csbaby.kefu.data.local.entity.MessageBlacklistEntity(
                id = (map["id"] as? Double)?.toLong() ?: return null,
                type = map["type"] as? String ?: "",
                value = map["value"] as? String ?: "",
                description = map["description"] as? String ?: "",
                packageName = map["packageName"] as? String,
                createdAt = (map["createdAt"] as? Double)?.toLong() ?: System.currentTimeMillis(),
                isEnabled = map["isEnabled"] as? Boolean ?: true,
                tenantId = tenantId,
                syncVersion = 0L,
                deleted = false
            )
        } catch (e: Exception) {
            Timber.e(e, "mapToMessageBlacklist failed")
            null
        }
    }

    // ========== 工具 ==========

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
