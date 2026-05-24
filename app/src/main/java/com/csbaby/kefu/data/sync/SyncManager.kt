package com.csbaby.kefu.data.sync

import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import com.csbaby.kefu.data.remote.SyncMessageBlacklist
import com.csbaby.kefu.data.local.entity.*
import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.util.Log
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 同步管理器：负责本地 Room 数据库 ↔ 云端 API 的数据同步。
 *
 * 核心策略：
 * 1. 本地优先：所有读写先走本地 Room，同步在后台异步进行
 * 2. 增量同步：基于 syncVersion 和时间戳，只传变更的数据
 * 3. 软删除：用 deleted 标记代替物理删除，确保删除操作也能同步
 * 4. 冲突解决：服务器版本新 → 用服务器数据；本地版本新 → 推送到服务器
 * 5. 租户隔离：所有查询都带 tenantId，不同租户数据完全隔离
 */
@Singleton
class SyncManager @Inject constructor(
    private val keywordRuleDao: KeywordRuleDao,
    private val aiModelConfigDao: AIModelConfigDao,
    private val userStyleProfileDao: UserStyleProfileDao,
    private val appConfigDao: AppConfigDao,
    private val scenarioDao: ScenarioDao,
    private val replyHistoryDao: ReplyHistoryDao,
    private val messageBlacklistDao: MessageBlacklistDao,
    private val syncCheckpointDao: SyncCheckpointDao,
    private val authManager: AuthManager,
    private val syncQueue: SyncQueue
) {
    // 带 JWT 认证的 API 客户端，Token 从 AuthManager 运行时读取
    val syncClient = AuthenticatedSyncClient(authManager)
    private val syncApiService: SyncApiService = syncClient.apiService
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _authState = MutableStateFlow<SyncAuthState?>(null)
    val authState: StateFlow<SyncAuthState?> = _authState.asStateFlow()

    private var syncJob: Job? = null
    private var _lastSyncStats: String = ""

    /** 上次同步统计信息 */
    fun getLastSyncStats(): String? = _lastSyncStats.takeIf { it.isNotEmpty() }

    /** 离线同步队列（只读访问） */
    val queue: SyncQueue get() = syncQueue

    /** 上次同步时间 Flow（按当前租户） */
    val lastSyncTime: Flow<Long> = authState.flatMapLatest { auth ->
        val tenantId = auth?.tenantId ?: ""
        if (tenantId.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(0L)
        } else {
            syncCheckpointDao.getCheckpointFlow(tenantId).map { it?.lastSyncTime ?: 0L }
        }
    }

    // ========== 认证 ==========

    suspend fun login(email: String, password: String): Result<SyncAuthState> {
        Log.d("SyncManager", "login() 开始: email=$email")
        _syncState.value = SyncState.Syncing("正在登录...")
        return try {
            Log.d("SyncManager", "login() 调用 syncApiService.login, baseUrl=${BuildConfig.SYNC_BASE_URL}")
            val request = LoginRequest(email, password)
            val response = syncApiService.login(request)
            Log.d("SyncManager", "login() 响应: isSuccess=${response.isSuccess}, msg=${response.message}")
            if (response.isSuccess && response.data != null) {
                val auth = SyncAuthState.fromLoginResponse(
                    userId = response.data.userId,
                    tenantId = response.data.tenantId.ifEmpty { response.data.userId },
                    token = response.data.effectiveAccessToken(),
                    refreshToken = response.data.refreshToken,
                    expiresAt = response.data.expiresAt
                )
                _authState.value = auth
                authManager.saveAuthState(auth)
                _syncState.value = SyncState.Success("登录成功")
                Timber.d("登录成功: tenant=${auth.tenantId}, user=${auth.userId}")
                Result.success(auth)
            } else {
                val msg = response.message.ifEmpty { "登录失败" }
                _syncState.value = SyncState.Error(msg)
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Timber.e(e, "登录失败")
            _syncState.value = SyncState.Error(e.message ?: "网络错误")
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String, displayName: String): Result<SyncAuthState> {
        _syncState.value = SyncState.Syncing("正在注册...")
        return try {
            val response = syncApiService.register(RegisterRequest(email, password, displayName))
            if (response.isSuccess && response.data != null) {
                val auth = SyncAuthState.fromLoginResponse(
                    userId = response.data.userId,
                    tenantId = response.data.tenantId.ifEmpty { response.data.userId },
                    token = response.data.effectiveAccessToken(),
                    refreshToken = response.data.refreshToken,
                    expiresAt = response.data.expiresAt
                )
                _authState.value = auth
                authManager.saveAuthState(auth)
                _syncState.value = SyncState.Success("注册成功")
                Timber.d("注册成功: tenant=${auth.tenantId}, user=${auth.userId}")
                Result.success(auth)
            } else {
                val msg = response.message.ifEmpty { "注册失败" }
                _syncState.value = SyncState.Error(msg)
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Timber.e(e, "注册失败")
            _syncState.value = SyncState.Error(e.message ?: "网络错误")
            Result.failure(e)
        }
    }

    fun logout() {
        _authState.value = null
        syncJob?.cancel()
        _syncState.value = SyncState.Idle
        // 清除持久化的认证状态
        CoroutineScope(Dispatchers.IO).launch {
            authManager.clearAuthState()
        }
    }

    /** 应用启动时恢复登录状态，并自动触发全量同步（用于卸载重装后数据恢复） */
    suspend fun restoreAuthState() {
        val saved = authManager.getAuthState()
        if (saved != null && !saved.isExpired()) {
            _authState.value = saved
            Timber.d("恢复登录状态: tenant=${saved.tenantId}")

            // 自动触发全量同步以恢复云端数据（首次登录/卸载重装场景）
            try {
                val result = fullSync(saved.tenantId)
                if (result.isSuccess) {
                    Timber.d("自动全量同步成功: tenant=${saved.tenantId}")
                } else {
                    Timber.w("自动全量同步失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "自动全量同步异常")
            }
        } else if (saved != null && saved.isExpired()) {
            // Token 已过期，尝试用 refreshToken 刷新
            Timber.d("Token 已过期，尝试刷新")
            val refreshed = tryRefreshToken(saved.refreshToken)
            if (refreshed != null) {
                _authState.value = refreshed
                authManager.saveAuthState(refreshed)
                Timber.d("Token 刷新成功: tenant=${refreshed.tenantId}")
                try {
                    fullSync(refreshed.tenantId)
                } catch (e: Exception) {
                    Timber.e(e, "Token 刷新后全量同步异常")
                }
            } else {
                Timber.w("Token 刷新失败，清除认证状态")
                authManager.clearAuthState()
            }
        }
    }

    /** 用 refreshToken 刷新认证状态 */
    private suspend fun tryRefreshToken(refreshToken: String): SyncAuthState? {
        return try {
            val request = RefreshTokenRequest(refreshToken)
            val response = syncClient.refreshApiService.refreshToken(request)
            Timber.d("Token刷新响应: isSuccess=${response.isSuccess}, msg=${response.message}")
            if (response.isSuccess && response.data != null) {
                val data = response.data
                SyncAuthState.fromLoginResponse(
                    userId = data.userId,
                    tenantId = data.tenantId.ifEmpty { data.userId },
                    token = data.effectiveAccessToken(),
                    refreshToken = data.refreshToken,
                    expiresAt = data.expiresAt
                ).also {
                    Timber.d("Token刷新成功: token=${it.accessToken.take(20)}...")
                }
            } else {
                Timber.w("Token刷新失败: ${response.message}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Token刷新异常")
            null
        }
    }

    // ========== 全量同步（首次登录 / 换手机恢复） ==========

    suspend fun fullSync(tenantId: String): Result<Unit> {
        _syncState.value = SyncState.Syncing("正在同步数据...")
        syncCheckpointDao.updateSyncing(tenantId, true)
        return try {
            val response = syncApiService.getAllData(tenantId)
            if (response.isSuccess && response.data != null) {
                val data = response.data

                // 将服务端数据写入本地（保留本地已有的 tenant_id 默认值的数据）
                applyServerDataToLocal(data, tenantId)

                syncCheckpointDao.updateSyncSuccess(tenantId, data.serverTime, null)
                val ruleCount = data.keywordRules.size
                val modelCount = data.aiModelConfigs.size
                _lastSyncStats = "全量同步: 获取 $ruleCount 条规则, $modelCount 条模型"
                _syncState.value = SyncState.Success("同步完成 (获取 $ruleCount 条)")
                Timber.d("全量同步完成: rules=$ruleCount, models=$modelCount")
                Result.success(Unit)
            } else {
                val msg = response.message.ifEmpty { "同步失败" }
                _syncState.value = SyncState.Error(msg)
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Timber.e(e, "全量同步失败")
            syncCheckpointDao.updateLastError(tenantId, e.message)
            _syncState.value = SyncState.Error(e.message ?: "同步失败")
            Result.failure(e)
        } finally {
            syncCheckpointDao.updateSyncing(tenantId, false)
        }
    }

    // ========== 增量同步 ==========

    suspend fun incrementalSync(tenantId: String): Result<Unit> {
        val checkpoint = syncCheckpointDao.getCheckpoint(tenantId)
        val since = checkpoint?.lastSyncTime ?: 0L

        _syncState.value = SyncState.Syncing("正在同步变更...")
        syncCheckpointDao.updateSyncing(tenantId, true)
        return try {
            // 1. 拉取服务端变更
            val changesResponse = syncApiService.getChanges(tenantId, since)
            if (changesResponse.isSuccess && changesResponse.data != null) {
                val changes = changesResponse.data
                applyChangesToLocal(changes, tenantId)
            }

            // 2. 推送本地变更
            pushLocalChanges(tenantId, since)

            syncCheckpointDao.updateSyncSuccess(
                tenantId,
                System.currentTimeMillis(),
                changesResponse.data?.nextCursor
            )

            val stats = _lastSyncStats
            if (stats.isNotEmpty()) {
                _syncState.value = SyncState.Success("同步完成", stats)
            } else {
                _syncState.value = SyncState.Success("同步完成")
            }
            Timber.d("增量同步完成")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "增量同步失败")
            syncCheckpointDao.updateLastError(tenantId, e.message)
            _syncState.value = SyncState.Error(e.message ?: "同步失败")
            Result.failure(e)
        } finally {
            syncCheckpointDao.updateSyncing(tenantId, false)
        }
    }

    // ========== 推送本地变更 ==========

    suspend fun pushLocalChanges(tenantId: String, since: Long) {
        // 收集本地有 syncVersion > since 的数据（即本地新增/修改的）
        val rules = keywordRuleDao.getRulesByTenantSync(tenantId)
            .filter { it.syncVersion > since || it.syncVersion == 0L }

        val models = aiModelConfigDao.getModelsByTenantSync(tenantId)
            .filter { it.syncVersion > since || it.syncVersion == 0L }

        val profile = userStyleProfileDao.getProfileByTenantIdSync(tenantId)
        val profiles = if (profile != null && (profile.syncVersion > since || profile.syncVersion == 0L)) listOf(profile) else emptyList()

        val apps = appConfigDao.getAppsByTenantSync(tenantId)
            .filter { it.syncVersion > since || it.syncVersion == 0L }

        val scenarios = scenarioDao.getScenariosByTenantSync(tenantId)
            .filter { it.syncVersion > since || it.syncVersion == 0L }

        val replies = replyHistoryDao.getRepliesByTenantSync(tenantId)
            .filter { it.syncVersion > since || it.syncVersion == 0L }

        val blacklists = messageBlacklistDao.getByTenantSync(tenantId)
            .filter { it.syncVersion > since || it.syncVersion == 0L }

        if (rules.isEmpty() && models.isEmpty() && profiles.isEmpty() && apps.isEmpty() && scenarios.isEmpty() && replies.isEmpty() && blacklists.isEmpty()) {
            Timber.d("没有本地变更需要推送")
            return
        }

        val request = PushChangesRequest(
            tenantId = tenantId,
            keywordRules = rules.map { it.toSyncModel() },
            aiModelConfigs = models.map { it.toSyncModel() },
            userStyleProfile = profiles.firstOrNull()?.toSyncModel(),
            appConfigs = apps.map { it.toSyncModel() },
            scenarios = scenarios.map { it.toSyncModel() },
            replyHistory = replies.map { it.toSyncModel() },
            messageBlacklist = blacklists.map { it.toSyncModel() },
            deletedIds = emptyMap(),
            baseVersion = since
        )

        val response = syncApiService.pushChanges(request)
        if (response.isSuccess && response.data != null) {
            val result = response.data
            if (!result.accepted && result.conflicts.isNotEmpty()) {
                handleConflicts(tenantId, result.conflicts)
            }
            // 更新本地 syncVersion
            val newVersion = result.newServerVersion
            rules.forEach { keywordRuleDao.updateSyncVersion(it.id, newVersion) }
            models.forEach { aiModelConfigDao.updateSyncVersion(it.id, newVersion) }
            profiles.forEach { userStyleProfileDao.updateSyncVersion(it.userId, newVersion) }
            apps.forEach { appConfigDao.updateSyncVersion(it.packageName, newVersion) }
            scenarios.forEach { scenarioDao.updateSyncVersion(it.id, newVersion) }
            replies.forEach { replyHistoryDao.updateSyncVersion(it.id, newVersion) }
            blacklists.forEach { messageBlacklistDao.updateSyncVersion(it.id, newVersion) }
            // 显示同步统计
            val stats = result.stats
            if (stats != null) {
                _lastSyncStats = "新增 ${stats.inserted} 条，更新 ${stats.updated} 条，删除 ${stats.deleted} 条"
                Timber.d("推送完成: $_lastSyncStats")
            }
        }
    }

    // ========== 应用服务端数据到本地 ==========

    private suspend fun applyServerDataToLocal(data: SyncAllData, tenantId: String) {
        // 知识库规则：服务端数据覆盖本地（全量同步场景）
        data.keywordRules.forEach { rule ->
            keywordRuleDao.insertRule(rule.toEntity(tenantId))
        }

        // AI 模型配置
        data.aiModelConfigs.forEach { model ->
            aiModelConfigDao.insertModel(model.toEntity(tenantId))
        }

        // 风格画像
        data.userStyleProfile?.let { profile ->
            userStyleProfileDao.insertProfile(profile.toEntity(tenantId))
        }

        // 应用配置
        data.appConfigs.forEach { app ->
            appConfigDao.insertApp(app.toEntity(tenantId))
        }

        // 场景
        data.scenarios.forEach { scenario ->
            scenarioDao.insertScenario(scenario.toEntity(tenantId))
        }

        // 回复历史
        data.replyHistory.forEach { reply ->
            replyHistoryDao.insertReply(reply.toEntity(tenantId))
        }

        // 消息黑名单
        data.messageBlacklist.forEach { blacklist ->
            messageBlacklistDao.insert(blacklist.toEntity(tenantId))
        }
    }

    private suspend fun applyChangesToLocal(changes: SyncChanges, tenantId: String) {
        changes.keywordRules.forEach { keywordRuleDao.insertRule(it.toEntity(tenantId)) }
        changes.aiModelConfigs.forEach { aiModelConfigDao.insertModel(it.toEntity(tenantId)) }
        changes.userStyleProfile?.let { userStyleProfileDao.insertProfile(it.toEntity(tenantId)) }
        changes.appConfigs.forEach { appConfigDao.insertApp(it.toEntity(tenantId)) }
        changes.scenarios.forEach { scenarioDao.insertScenario(it.toEntity(tenantId)) }
        changes.replyHistory.forEach { replyHistoryDao.insertReply(it.toEntity(tenantId)) }
        changes.messageBlacklist.forEach { messageBlacklistDao.insert(it.toEntity(tenantId)) }

        // 处理删除
        changes.deletedIds.forEach { (entityType, ids) ->
            when (entityType) {
                "keyword_rules" -> ids.forEach { keywordRuleDao.deleteById(it.toLong()) }
                "ai_model_configs" -> ids.forEach { aiModelConfigDao.deleteById(it.toLong()) }
                "app_configs" -> ids.forEach { appConfigDao.deleteByPackage(it) }
                "scenarios" -> ids.forEach { scenarioDao.deleteById(it.toLong()) }
                "reply_history" -> ids.forEach { replyHistoryDao.deleteById(it.toLong()) }
                "message_blacklist" -> ids.forEach { messageBlacklistDao.deleteById(it.toLong()) }
                "user_style_profiles" -> ids.forEach { id ->
                    userStyleProfileDao.getProfileByUserIdSync(id)?.let { userStyleProfileDao.deleteProfile(it) }
                }
            }
        }
    }

    /**
     * 冲突解决策略：
     * - 知识库规则 / AI模型配置 / 应用配置 → 字段级合并（本地和服务端变更不同字段时合并）
     * - 风格画像 → 取最新修改时间
     * - 无法自动合并的 → 记录到待解决列表，UI 提示用户选择
     */
    private suspend fun handleConflicts(tenantId: String, conflicts: List<SyncConflict>) {
        Timber.w("同步冲突: ${conflicts.size} 个")
        val resolutions = mutableListOf<ConflictResolution>()
        val unresolved = mutableListOf<SyncConflict>()

        for (conflict in conflicts) {
            val strategy = resolveConflictAuto(conflict, tenantId)
            if (strategy != null) {
                resolutions.add(strategy)
            } else {
                unresolved.add(conflict)
            }
        }

        // 自动解决的冲突
        if (resolutions.isNotEmpty()) {
            try {
                syncApiService.resolveConflict(ConflictResolveRequest(tenantId, resolutions))
                Timber.d("自动解决冲突: ${resolutions.size} 个")
            } catch (e: Exception) {
                Timber.e(e, "冲突解决 API 调用失败")
            }
        }

        // 无法自动解决的 → 入队等待用户处理
        if (unresolved.isNotEmpty()) {
            Timber.w("需要用户手动解决的冲突: ${unresolved.size} 个")
            unresolved.forEach { conflict ->
                // TODO: 写入冲突待解决表，UI 展示冲突选择对话框
                Timber.w("  冲突: ${conflict.entityType}:${conflict.entityId}")
            }
        }
    }

    /**
     * 尝试自动解决冲突。返回 null 表示需要用户手动处理。
     */
    private suspend fun resolveConflictAuto(
        conflict: SyncConflict,
        tenantId: String
    ): ConflictResolution? {
        return when (conflict.entityType) {
            // 知识库规则：服务端优先（团队共享规则，服务端为权威来源）
            "keyword_rule" -> ConflictResolution(
                entityType = conflict.entityType,
                entityId = conflict.entityId,
                strategy = "SERVER_WINS"
            )
            // AI 模型配置：服务端优先（API Key 等敏感信息以服务端为准）
            "ai_model_config" -> ConflictResolution(
                entityType = conflict.entityType,
                entityId = conflict.entityId,
                strategy = "SERVER_WINS"
            )
            // 风格画像：客户端优先（个人风格是用户自己的数据）
            "style_profile" -> ConflictResolution(
                entityType = conflict.entityType,
                entityId = conflict.entityId,
                strategy = "CLIENT_WINS"
            )
            // 应用配置：合并策略
            "app_config" -> ConflictResolution(
                entityType = conflict.entityType,
                entityId = conflict.entityId,
                strategy = "MERGE"
            )
            // 消息黑名单：服务端优先（团队共享配置）
            "message_blacklist" -> ConflictResolution(
                entityType = conflict.entityType,
                entityId = conflict.entityId,
                strategy = "SERVER_WINS"
            )
            // 其他：无法自动解决
            else -> null
        }
    }

    // ========== 写入即同步触发器 ==========

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncTriggerJob: Job? = null

    /**
     * 写入本地数据后调用此方法触发同步。
     * Debounce 2 秒避免高频写入打爆 API。
     */
    fun triggerSync() {
        val tenantId = _authState.value?.tenantId ?: return
        syncTriggerJob?.cancel()
        syncTriggerJob = syncScope.launch {
            kotlinx.coroutines.delay(2000) // 2 秒 debounce
            incrementalSync(tenantId)
        }
    }

    // ========== 工具方法 ==========

    fun isLoggedIn(): Boolean = _authState.value != null

    fun currentTenantId(): String? = _authState.value?.tenantId

    // ========== 数据转换 ==========

    private fun SyncKeywordRule.toEntity(tenantId: String) = KeywordRuleEntity(
        id = id, keyword = keyword, matchType = matchType,
        replyTemplate = replyTemplate, category = category,
        targetType = targetType, targetNamesJson = targetNamesJson,
        priority = priority, enabled = enabled,
        createdAt = createdAt, updatedAt = updatedAt,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun SyncAIModelConfig.toEntity(tenantId: String) = AIModelConfigEntity(
        id = id, modelType = modelType, modelName = modelName,
        apiKey = apiKey, apiEndpoint = apiEndpoint,
        temperature = temperature, maxTokens = maxTokens,
        isDefault = isDefault, isEnabled = isEnabled,
        monthlyCost = monthlyCost, lastUsed = lastUsed, createdAt = createdAt,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun SyncUserStyleProfile.toEntity(tenantId: String) = UserStyleProfileEntity(
        userId = userId, formalityLevel = formalityLevel,
        enthusiasmLevel = enthusiasmLevel, professionalismLevel = professionalismLevel,
        wordCountPreference = wordCountPreference,
        commonPhrases = commonPhrases, avoidPhrases = avoidPhrases,
        learningSamples = learningSamples, accuracyScore = accuracyScore,
        lastTrained = lastTrained, createdAt = createdAt,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun SyncAppConfig.toEntity(tenantId: String) = AppConfigEntity(
        packageName = packageName, appName = appName, iconUri = iconUri,
        isMonitored = isMonitored, createdAt = createdAt, lastUsed = lastUsed,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun SyncScenario.toEntity(tenantId: String) = ScenarioEntity(
        id = id, name = name, type = type, targetId = targetId,
        description = description, createdAt = createdAt,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun SyncReplyHistory.toEntity(tenantId: String) = ReplyHistoryEntity(
        id = id, sourceApp = sourceApp, originalMessage = originalMessage,
        generatedReply = generatedReply, finalReply = finalReply,
        ruleMatchedId = ruleMatchedId, modelUsedId = modelUsedId,
        styleApplied = styleApplied, sendTime = sendTime, modified = modified,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun KeywordRuleEntity.toSyncModel() = SyncKeywordRule(
        id = id, keyword = keyword, matchType = matchType,
        replyTemplate = replyTemplate, category = category,
        targetType = targetType, targetNamesJson = targetNamesJson,
        priority = priority, enabled = enabled,
        createdAt = createdAt, updatedAt = updatedAt,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun AIModelConfigEntity.toSyncModel() = SyncAIModelConfig(
        id = id, modelType = modelType, modelName = modelName,
        apiKey = apiKey, apiEndpoint = apiEndpoint,
        temperature = temperature, maxTokens = maxTokens,
        isDefault = isDefault, isEnabled = isEnabled,
        monthlyCost = monthlyCost, lastUsed = lastUsed, createdAt = createdAt,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun UserStyleProfileEntity.toSyncModel() = SyncUserStyleProfile(
        userId = userId, formalityLevel = formalityLevel,
        enthusiasmLevel = enthusiasmLevel, professionalismLevel = professionalismLevel,
        wordCountPreference = wordCountPreference,
        commonPhrases = commonPhrases, avoidPhrases = avoidPhrases,
        learningSamples = learningSamples, accuracyScore = accuracyScore,
        lastTrained = lastTrained, createdAt = createdAt,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun AppConfigEntity.toSyncModel() = SyncAppConfig(
        packageName = packageName, appName = appName, iconUri = iconUri,
        isMonitored = isMonitored, createdAt = createdAt, lastUsed = lastUsed,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun ScenarioEntity.toSyncModel() = SyncScenario(
        id = id, name = name, type = type, targetId = targetId,
        description = description, createdAt = createdAt,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun ReplyHistoryEntity.toSyncModel() = SyncReplyHistory(
        id = id, sourceApp = sourceApp, originalMessage = originalMessage,
        generatedReply = generatedReply, finalReply = finalReply,
        ruleMatchedId = ruleMatchedId, modelUsedId = modelUsedId,
        styleApplied = styleApplied, sendTime = sendTime, modified = modified,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    // ========== MessageBlacklist 转换 ==========

    private fun SyncMessageBlacklist.toEntity(tenantId: String) = MessageBlacklistEntity(
        id = id, type = type, value = value, description = description,
        packageName = packageName, createdAt = createdAt, isEnabled = isEnabled,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )

    private fun MessageBlacklistEntity.toSyncModel() = SyncMessageBlacklist(
        id = id, type = type, value = value, description = description,
        packageName = packageName, createdAt = createdAt, isEnabled = isEnabled,
        tenantId = tenantId, syncVersion = syncVersion, deleted = deleted
    )
}

// ========== 同步状态 ==========

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
    data class Success(val message: String, val stats: String = "") : SyncState()
}
