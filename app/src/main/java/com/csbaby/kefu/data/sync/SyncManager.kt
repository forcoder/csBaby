package com.csbaby.kefu.data.sync

import android.database.sqlite.SQLiteDatabase
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import com.csbaby.kefu.data.remote.SyncMessageBlacklist
import com.csbaby.kefu.data.local.entity.*
import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.remote.*
import com.csbaby.kefu.domain.model.SyncState
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
    // syncApiService 用 lateinit var: 保持字段名 syncApiService 让测试反射可设值
    // 真正的取值函数: 字段未初始化时 fallback 到 syncClient.apiService (兼容 LoginSyncTriggerTest)
    @Suppress("LateinitUsageOverridesNothing")
    private lateinit var syncApiService: SyncApiService

    private fun getSyncApiService(): SyncApiService =
        if (::syncApiService.isInitialized) syncApiService else syncClient.apiService
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _authState = MutableStateFlow<SyncAuthState?>(null)
    val authState: StateFlow<SyncAuthState?> = _authState.asStateFlow()

    private var syncJob: Job? = null
    private var _lastSyncStats: String = ""

    /** 避免 migrateLocalDataIfNeeded 反复推送遗留数据 (按 tenantId 记录, 支持多账号) */
    private val legacyPushAttemptedTenants = mutableSetOf<String>()

    /** 上次同步统计信息 */
    fun getLastSyncStats(): String? = _lastSyncStats.takeIf { it.isNotEmpty() }

    /** 离线同步队列（只读访问） */
    val queue: SyncQueue get() = syncQueue

    /** 上次同步时间 Flow（按当前租户） */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lastSyncTime: Flow<Long> = authState.flatMapLatest { auth ->
        val tenantId = auth?.tenantId ?: ""
        if (tenantId.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(0L)
        } else {
            syncCheckpointDao.getCheckpointFlow(tenantId).map { it?.lastSyncTime ?: 0L }
        }
    }

    // ========== 认证（统一走主 API 的 /api/auth/user/*） ==========

    suspend fun login(identifier: String, password: String): Result<SyncAuthState> {
        Log.d("SyncManager", "login() 开始: identifier=$identifier")
        _syncState.value = SyncState.Syncing("正在登录...")
        return try {
            Log.d("SyncManager", "login() 调用 authApiService.login, baseUrl=${BuildConfig.API_BASE_URL}")
            val response = syncClient.authApiService.login(
                com.csbaby.kefu.data.remote.LoginRequest(
                    identifier = identifier, password = password
                )
            )
            Log.d("SyncManager", "login() 响应: isSuccess=${response.isSuccess()}, err=${response.errorMessage()}")
            if (response.isSuccess()) {
                val auth = SyncAuthState.fromLoginResponse(
                    userId = response.effectiveUserId(),
                    tenantId = response.effectiveTenantId(),
                    token = response.effectiveToken(),
                    refreshToken = response.refreshToken ?: "",
expiresAt = response.expiresAt ?: 0L,
                    displayName = identifier
                )
                _authState.value = auth
                authManager.saveAuthState(auth)
                _syncState.value = SyncState.Success("登录成功", "")
                Timber.d("登录成功: tenant=${auth.tenantId}, user=${auth.userId}")
                // 迁移本地数据到真实租户（仅首次登录/新设备场景）
                migrateLocalDataIfNeeded(auth.tenantId)
                // BUG-R8 修复: 登录成功后触发 fullSync 拉取云端数据
                // (与 restoreAuthState 行为一致；fullSync 失败不应阻断登录成功)
                try {
                    val result = fullSync(auth.tenantId)
                    if (result.isSuccess) {
                        Timber.d("登录后全量同步成功: tenant=${auth.tenantId}")
                    } else {
                        Timber.w("登录后全量同步失败: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "登录后全量同步异常")
                }
                Result.success(auth)
            } else {
                val msg = response.errorMessage() ?: "登录失败"
                _syncState.value = SyncState.Error(msg)
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "登录失败: ${e.message}")
            _syncState.value = SyncState.Error(e.message ?: "网络错误")
            Result.failure(e)
        }
    }

    suspend fun register(identifier: String, password: String, displayName: String): Result<SyncAuthState> {
        _syncState.value = SyncState.Syncing("正在注册...")
        return try {
            // auto-detect phone vs email: 包含 '@' 视为 email, 否则 phone
            val isEmail = identifier.contains("@")
            val req = com.csbaby.kefu.data.remote.RegisterRequest(
                email = if (isEmail) identifier else null,
                phone = if (isEmail) null else identifier,
                password = password,
                name = displayName
            )
            val response = syncClient.authApiService.register(req)
            if (response.isSuccess()) {
                val auth = SyncAuthState.fromLoginResponse(
                    userId = response.effectiveUserId(),
                    tenantId = response.effectiveTenantId(),
                    token = response.effectiveToken(),
                    refreshToken = response.refreshToken ?: "",
                    expiresAt = response.expiresAt ?: 0L
                )
                _authState.value = auth
                authManager.saveAuthState(auth)
                _syncState.value = SyncState.Success("注册成功", "")
                Timber.d("注册成功: tenant=${auth.tenantId}, user=${auth.userId}")
                Result.success(auth)
            } else {
                val msg = response.errorMessage() ?: "注册失败"
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

            // 迁移本地 default_tenant 数据到真实租户（首次登录/卸载重装场景）
            migrateLocalDataIfNeeded(saved.tenantId)

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

                // 迁移本地 default_tenant 数据到真实租户
                migrateLocalDataIfNeeded(refreshed.tenantId)

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

    // ========== 本地数据迁移（default_tenant → 真实租户） ==========

    companion object {
        const val DEFAULT_TENANT_ID = "default_tenant"
        private const val PUSH_BATCH_SIZE = 50
    }

    /**
     * 将本地 default_tenant 的数据迁移到真实租户。
     * 解决卸载重装后本地数据 tenantId 不正确导致同步返回 0 的问题。
     */
    private suspend fun migrateLocalDataIfNeeded(tenantId: String) {
        if (tenantId == DEFAULT_TENANT_ID) {
            Log.d("SyncManager", "migrateLocalDataIfNeeded: tenant is DEFAULT, skip")
            return
        }

        var totalMigrated = 0
        Log.d("SyncManager", "幂等迁移开始: default_tenant -> $tenantId")

        // 知识库规则: 按 id 去重迁移
        val defaultRules = keywordRuleDao.getRulesByTenantSync(DEFAULT_TENANT_ID)
        Log.d("SyncManager", "default_tenant 规则数: ${defaultRules.size}")
        if (defaultRules.isNotEmpty()) {
            val existingIds = keywordRuleDao.getRulesByTenantSync(tenantId).map { it.id }.toHashSet()
            Log.d("SyncManager", "真实租户已有规则数: ${existingIds.size}")
            val toMigrate = defaultRules.filter { it.id !in existingIds }
            Log.d("SyncManager", "需要迁移的规则数: ${toMigrate.size}")
            if (toMigrate.isNotEmpty()) {
                toMigrate.forEach { keywordRuleDao.insertRule(it.copy(tenantId = tenantId, syncVersion = 0L)) }
                totalMigrated += toMigrate.size
                Timber.d("迁移 ${toMigrate.size} 条知识库规则到 tenant=$tenantId")
            }
        }

        // 空 tenantId 规则迁移: 旧版本数据可能 tenantId 为空字符串
        val emptyTenantRules = keywordRuleDao.getRulesWithEmptyTenant()
        Log.d("SyncManager", "空 tenantId 规则数: ${emptyTenantRules.size}")
        if (emptyTenantRules.isNotEmpty()) {
            val existingIds = keywordRuleDao.getRulesByTenantSync(tenantId).map { it.id }.toHashSet()
            val toMigrate = emptyTenantRules.filter { it.id !in existingIds }
            if (toMigrate.isNotEmpty()) {
                toMigrate.forEach { keywordRuleDao.insertRule(it.copy(tenantId = tenantId, syncVersion = 0L)) }
                totalMigrated += toMigrate.size
                Timber.d("迁移 ${toMigrate.size} 条空 tenantId 规则到 tenant=$tenantId")
            }
        }

        // 其他租户规则迁移: 旧手机上可能绑定了不同的 tenantId
        val otherTenantRules = keywordRuleDao.getRulesFromOtherTenants(tenantId)
        Log.d("SyncManager", "其他租户规则数: ${otherTenantRules.size}")
        if (otherTenantRules.isNotEmpty()) {
            val existingIds = keywordRuleDao.getRulesByTenantSync(tenantId).map { it.id }.toHashSet()
            val toMigrate = otherTenantRules.filter { it.id !in existingIds }
            if (toMigrate.isNotEmpty()) {
                toMigrate.forEach { keywordRuleDao.insertRule(it.copy(tenantId = tenantId, syncVersion = 0L)) }
                totalMigrated += toMigrate.size
                Timber.d("迁移 ${toMigrate.size} 条其他租户规则到 tenant=$tenantId")
            }
        }

        // 旧 tenantId 纠正: Room 里的 tenant UUID 可能和 JWT 的不完全一致
        // (如缺末尾字符),用相同前缀匹配防止数据写到不同租户
        val localPrefix = tenantId.take(30)
        val misalignedCount = keywordRuleDao.getRulesByTenantSync(tenantId).size
        if (misalignedCount < 10 && otherTenantRules.isNotEmpty()) {
            // 当前租户数据很少,而其他租户有大量数据 → 可能是 UUID 前缀匹配但末尾不同
            val similarRules = otherTenantRules.filter { it.tenantId.take(30) == localPrefix }
            if (similarRules.isNotEmpty()) {
                val existingIds = keywordRuleDao.getRulesByTenantSync(tenantId).map { it.id }.toHashSet()
                val toFix = similarRules.filter { it.id !in existingIds }
                if (toFix.isNotEmpty()) {
                    toFix.forEach { keywordRuleDao.insertRule(it.copy(tenantId = tenantId, syncVersion = 0L)) }
                    totalMigrated += toFix.size
                    Log.d("SyncManager", "纠正 ${toFix.size} 条租户 UUID 不匹配的规则")
                }
            }
        }

        // AI 模型配置
        val defaultModels = aiModelConfigDao.getModelsByTenantSync(DEFAULT_TENANT_ID)
        if (defaultModels.isNotEmpty()) {
            val existingIds = aiModelConfigDao.getModelsByTenantSync(tenantId).map { it.id }.toHashSet()
            val toMigrate = defaultModels.filter { it.id !in existingIds }
            if (toMigrate.isNotEmpty()) {
                toMigrate.forEach { aiModelConfigDao.insertModel(it.copy(tenantId = tenantId, syncVersion = 0L)) }
                totalMigrated += toMigrate.size
                Timber.d("迁移 ${toMigrate.size} 条 AI 模型配置")
            }
        }

        // 风格画像: 只有一条,不存在才迁移
        val defaultProfile = userStyleProfileDao.getProfileByTenantIdSync(DEFAULT_TENANT_ID)
        if (defaultProfile != null) {
            val existing = userStyleProfileDao.getProfileByTenantIdSync(tenantId)
            if (existing == null) {
                userStyleProfileDao.insertProfile(defaultProfile.copy(tenantId = tenantId, syncVersion = 0L))
                totalMigrated++
                Timber.d("迁移风格画像")
            }
        }

        // 应用配置: 按 packageName 去重迁移
        val defaultApps = appConfigDao.getAppsByTenantSync(DEFAULT_TENANT_ID)
        if (defaultApps.isNotEmpty()) {
            val existingPkgs = appConfigDao.getAppsByTenantSync(tenantId).map { it.packageName }.toHashSet()
            val toMigrate = defaultApps.filter { it.packageName !in existingPkgs }
            if (toMigrate.isNotEmpty()) {
                toMigrate.forEach { appConfigDao.insertApp(it.copy(tenantId = tenantId, syncVersion = 0L)) }
                totalMigrated += toMigrate.size
                Timber.d("迁移 ${toMigrate.size} 条应用配置")
            }
        }

        // 场景: 按 id 去重迁移
        val defaultScenarios = scenarioDao.getScenariosByTenantSync(DEFAULT_TENANT_ID)
        if (defaultScenarios.isNotEmpty()) {
            val existingIds = scenarioDao.getScenariosByTenantSync(tenantId).map { it.id }.toHashSet()
            val toMigrate = defaultScenarios.filter { it.id !in existingIds }
            if (toMigrate.isNotEmpty()) {
                toMigrate.forEach { scenarioDao.insertScenario(it.copy(tenantId = tenantId, syncVersion = 0L)) }
                totalMigrated += toMigrate.size
                Timber.d("迁移 ${toMigrate.size} 条场景")
            }
        }

        // 回复历史: 按 id 去重迁移
        val defaultReplies = replyHistoryDao.getRepliesByTenantSync(DEFAULT_TENANT_ID)
        if (defaultReplies.isNotEmpty()) {
            val existingIds = replyHistoryDao.getRepliesByTenantSync(tenantId).map { it.id }.toHashSet()
            val toMigrate = defaultReplies.filter { it.id !in existingIds }
            if (toMigrate.isNotEmpty()) {
                toMigrate.forEach { replyHistoryDao.insertReply(it.copy(tenantId = tenantId, syncVersion = 0L)) }
                totalMigrated += toMigrate.size
                Timber.d("迁移 ${toMigrate.size} 条回复历史")
            }
        }

        // 遗留数据库导入: 检查 kefu_pull.db / kefu_main_pull.db (旧数据)
        val existingRuleIds = keywordRuleDao.getRulesByTenantSync(tenantId).map { it.id }.toHashSet()
        val legacyDbPaths = listOf(
            "/data/data/${BuildConfig.APPLICATION_ID}/files/kefu_pull.db",
            "/data/data/${BuildConfig.APPLICATION_ID}/kefu_main_pull.db"
        )
        for (legacyPath in legacyDbPaths) {
            val file = java.io.File(legacyPath)
            if (!file.exists()) {
                Log.d("SyncManager", "遗留数据库不存在: $legacyPath")
                continue
            }
            try {
                val legacyDb = SQLiteDatabase.openDatabase(legacyPath, null, SQLiteDatabase.OPEN_READONLY)
                try {
                    val cursor = legacyDb.rawQuery(
                        "SELECT * FROM keyword_rules WHERE tenantId IS NULL OR tenantId = ''",
                        null
                    )
                    val entries = mutableListOf<KeywordRuleEntity>()
                    while (cursor.moveToNext()) {
                        val legacyId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                        if (legacyId in existingRuleIds) continue  // 已存在,跳过
                        existingRuleIds.add(legacyId)
                        entries.add(KeywordRuleEntity(
                            id = legacyId,
                            keyword = cursor.getString(cursor.getColumnIndexOrThrow("keyword"))
                                .orEmpty(),
                            matchType = cursor.getString(cursor.getColumnIndexOrThrow("matchType"))
                                .orEmpty().ifEmpty { "CONTAINS" },
                            replyTemplate = cursor.getString(
                                cursor.getColumnIndexOrThrow("replyTemplate")
                            ).orEmpty(),
                            category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
                                .orEmpty(),
                            targetType = cursor.getString(cursor.getColumnIndexOrThrow("targetType"))
                                .orEmpty().ifEmpty { "ALL" },
                            targetNamesJson = cursor.getString(
                                cursor.getColumnIndexOrThrow("targetNamesJson")
                            ).orEmpty().ifEmpty { "[]" },
                            priority = cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
                            enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0,
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")),
                            tenantId = tenantId,
                            syncVersion = 0L,
                            deleted = cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) != 0
                        ))
                    }
                    cursor.close()
                    if (entries.isNotEmpty()) {
                        keywordRuleDao.insertRules(entries)
                        totalMigrated += entries.size
                        Log.d("SyncManager", "从遗留数据库导入 ${entries.size} 条知识库规则: $legacyPath")
                    }
                } finally {
                    legacyDb.close()
                }
            } catch (e: Exception) {
                Log.w("SyncManager", "遗留数据库导入失败: $legacyPath - ${e.message}")
            }
        }

        // 推送所有本地数据: 分批(每批50条),避免一次性发366+条导致超时
        // 不使用 syncVersion 过滤,因为之前部分推送可能更新了 version 但数据没到服务器
        if (tenantId !in legacyPushAttemptedTenants) {
            val pushCandidates = keywordRuleDao.getRulesByTenantSync(tenantId)
                .filter { !it.deleted }
            if (pushCandidates.isNotEmpty()) {
                Log.d("SyncManager", "分批推送所有 ${pushCandidates.size} 条(每批50)")
                pushCandidates.chunked(PUSH_BATCH_SIZE).forEach { batch ->
                    try {
                        doPush(tenantId, batch, emptyList(), emptyList(),
                            emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), 0L)
                    } catch (e: Exception) {
                        Log.w("SyncManager", "分批推送失败: ${e.message}")
                    }
                }
            } else {
                Log.d("SyncManager", "无数据需推送")
            }
            syncCheckpointDao.updateSyncSuccess(tenantId, System.currentTimeMillis(), null)
            legacyPushAttemptedTenants.add(tenantId)
        }
        // BUG-R12 修复: 迁移后必须触发增量同步, 拉取云端变更到本地
        // (与 restoreAuthState 行为一致; 增量同步失败不应阻断登录/迁移)
        try {
            incrementalSync(tenantId)
        } catch (e: Exception) {
            Log.e("SyncManager", "迁移后增量同步异常: ${e.message}")
        }
    }

    // ========== 全量同步（首次登录 / 换手机恢复） ==========

    suspend fun fullSync(tenantId: String): Result<Unit> {
        _syncState.value = SyncState.Syncing("正在同步数据...")
        syncCheckpointDao.updateSyncing(tenantId, true)
        return try {
            val response = getSyncApiService().getAllData(tenantId)
            Log.d("SyncManager", "全量同步 API 响应: isSuccess=${response.isSuccess}, msg=${response.message}, data=${response.data != null}")
            if (response.isSuccess && response.data != null) {
                val data = response.data
                Log.d("SyncManager", "服务端返回: keywordRules=${data.keywordRules.size}, aiModelConfigs=${data.aiModelConfigs.size}")

                // 将服务端数据写入本地（保留本地已有的 tenant_id 默认值的数据）
                applyServerDataToLocal(data, tenantId)
                Log.d("SyncManager", "applyServerDataToLocal 完成")

                syncCheckpointDao.updateSyncSuccess(tenantId, data.serverTime, null)
                val ruleCount = data.keywordRules.size
                val modelCount = data.aiModelConfigs.size
                val appCount = data.appConfigs.size
                val scenarioCount = data.scenarios.size
                val blacklistCount = data.messageBlacklist.size
                val profileCount = if (data.userStyleProfile != null) 1 else 0
                // 生成简短汇总 (只显示数量最多的类型)
                val stats = buildString {
                    if (ruleCount > 0) append("知识库${ruleCount}条")
                    if (blacklistCount > 0) append("，黑名单${blacklistCount}条")
                    if (modelCount > 0) append("，模型${modelCount}条")
                    if (appCount > 0) append("，监控应用${appCount}条")
                    if (scenarioCount > 0) append("，场景${scenarioCount}条")
                    if (profileCount > 0) append("，风格画像${profileCount}条")
                }
                _lastSyncStats = stats
                _syncState.value = SyncState.Success("同步完成", stats)
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
        val localRuleCount = keywordRuleDao.getRulesByTenantSync(tenantId).size
        val defaultRuleCount = keywordRuleDao.getRulesByTenantSync(DEFAULT_TENANT_ID).size
        Log.d("SyncManager", "incrementalSync: localRuleCount=$localRuleCount, defaultRuleCount=$defaultRuleCount, since=$since, tenantId=$tenantId")

        _syncState.value = SyncState.Syncing("正在同步变更...")
        syncCheckpointDao.updateSyncing(tenantId, true)
        _lastSyncStats = ""
        return try {
            // 1. 优先推送本地数据到云端
            // 如果从未同步过（since=0），推送所有本地数据；否则只推送变更
            val isFirstSync = since == 0L
            val pushStats = if (isFirstSync) {
                pushAllLocalChanges(tenantId)  // 首次同步，推送所有
            } else {
                pushLocalChanges(tenantId, since)  // 增量同步
            }
            Log.d("SyncManager", "推送结果: pushStats='$pushStats'")

            // 2. 拉取云端变更到本地
            val changesResponse = getSyncApiService().getChanges(tenantId, since)
            if (changesResponse.isSuccess && changesResponse.data != null) {
                val changes = changesResponse.data
                applyChangesToLocal(changes, tenantId)
            }

            syncCheckpointDao.updateSyncSuccess(
                tenantId,
                System.currentTimeMillis(),
                changesResponse.data?.nextCursor
            )

            // 生成统计信息
            val changes = changesResponse.data
            val pullRuleCount = changes?.keywordRules?.size ?: 0
            val pullModelCount = changes?.aiModelConfigs?.size ?: 0
            val pullAppCount = changes?.appConfigs?.size ?: 0
            val pullScenarioCount = changes?.scenarios?.size ?: 0
            val pullBlacklistCount = changes?.messageBlacklist?.size ?: 0
            val pullProfileCount = if (changes?.userStyleProfile != null) 1 else 0
            val pullReplyCount = changes?.replyHistory?.size ?: 0

            // 生成简短汇总 (推送/拉取分开，只列有变化的类型)
            val stats = buildString {
                if (pushStats.isNotEmpty()) {
                    append("推送：$pushStats")
                }
                if (pullRuleCount > 0 || pullBlacklistCount > 0 || pullModelCount > 0 ||
                    pullAppCount > 0 || pullScenarioCount > 0 || pullReplyCount > 0 || pullProfileCount > 0) {
                    if (isNotEmpty()) append("，")
                    append("拉取：")
                    val details = mutableListOf<String>()
                    if (pullRuleCount > 0) details.add("知识库${pullRuleCount}条")
                    if (pullBlacklistCount > 0) details.add("黑名单${pullBlacklistCount}条")
                    if (pullModelCount > 0) details.add("模型${pullModelCount}条")
                    if (pullAppCount > 0) details.add("监控应用${pullAppCount}条")
                    if (pullScenarioCount > 0) details.add("场景${pullScenarioCount}条")
                    if (pullReplyCount > 0) details.add("回复历史${pullReplyCount}条")
                    if (pullProfileCount > 0) details.add("风格画像${pullProfileCount}条")
                    if (details.isEmpty()) details.add("无变更")
                    append(details.joinToString("，"))
                }
            }
            _syncState.value = SyncState.Success("同步完成", stats)
            Log.d("SyncManager", "增量同步完成: $stats")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SyncManager", "增量同步失败: ${e.message}")
            syncCheckpointDao.updateLastError(tenantId, e.message)
            _syncState.value = SyncState.Error(e.message ?: "同步失败")
            Result.failure(e)
        } finally {
            syncCheckpointDao.updateSyncing(tenantId, false)
        }
    }

    // ========== 推送本地变更（增量，分批避免超时）==========

    suspend fun pushLocalChanges(tenantId: String, since: Long): String {
        // 收集本地有变更的数据（syncVersion == 0 表示新增/修改待同步）
        val rules = keywordRuleDao.getRulesByTenantSync(tenantId)
            .filter { it.syncVersion >= since || it.syncVersion == 0L }
        val models = aiModelConfigDao.getModelsByTenantSync(tenantId)
            .filter { it.syncVersion >= since || it.syncVersion == 0L }
        val profile = userStyleProfileDao.getProfileByTenantIdSync(tenantId)
        val profiles = if (profile != null && (profile.syncVersion >= since || profile.syncVersion == 0L)) listOf(profile) else emptyList()
        val apps = appConfigDao.getAppsByTenantSync(tenantId)
            .filter { it.syncVersion >= since || it.syncVersion == 0L }
        val scenarios = scenarioDao.getScenariosByTenantSync(tenantId)
            .filter { it.syncVersion >= since || it.syncVersion == 0L }
        val replies = replyHistoryDao.getRepliesByTenantSync(tenantId)
            .filter { it.syncVersion >= since || it.syncVersion == 0L }
        val blacklists = messageBlacklistDao.getByTenantSync(tenantId)
            .filter { it.syncVersion >= since || it.syncVersion == 0L }

        // 分离正常数据和已删除数据
        val activeRules = rules.filter { !it.deleted }
        // BUG-R14: deleted id 也要用 namespaced id (与服务端 pkey 保持一致)
        val deletedRules = rules.filter { it.deleted }.map { "${it.tenantId}_${it.id}" }
        val activeModels = models.filter { !it.deleted }
        val deletedModels = models.filter { it.deleted }.map { it.id.toString() }
        val activeApps = apps.filter { !it.deleted }
        val deletedApps = apps.filter { it.deleted }.map { it.packageName }
        val activeScenarios = scenarios.filter { !it.deleted }
        val deletedScenarios = scenarios.filter { it.deleted }.map { it.id.toString() }
        val activeReplies = replies.filter { !it.deleted }
        val deletedReplies = replies.filter { it.deleted }.map { it.id.toString() }
        val activeBlacklists = blacklists.filter { !it.deleted }
        val deletedBlacklists = blacklists.filter { it.deleted }.map { it.id.toString() }

        val deletedIds = buildMap {
            if (deletedRules.isNotEmpty()) put("keyword_rules", deletedRules)
            if (deletedModels.isNotEmpty()) put("ai_model_configs", deletedModels)
            if (deletedApps.isNotEmpty()) put("app_configs", deletedApps)
            if (deletedScenarios.isNotEmpty()) put("scenarios", deletedScenarios)
            if (deletedReplies.isNotEmpty()) put("reply_history", deletedReplies)
            if (deletedBlacklists.isNotEmpty()) put("message_blacklist", deletedBlacklists)
        }

        if (activeRules.isEmpty() && activeModels.isEmpty() && profiles.isEmpty() && activeApps.isEmpty() && activeScenarios.isEmpty() && activeReplies.isEmpty() && activeBlacklists.isEmpty() && deletedIds.isEmpty()) {
            Log.d("SyncManager", "没有本地变更需要推送")
            return ""
        }

        // 分批推送: 知识库规则按 PUSH_BATCH_SIZE 分批，其他数据量小的一次推送
        val allStats = mutableListOf<String>()

        // 知识库规则分批
        val ruleBatches = activeRules.chunked(PUSH_BATCH_SIZE)
        for ((i, ruleBatch) in ruleBatches.withIndex()) {
            val isFirstBatch = i == 0
            val batchStats = doPush(
                tenantId, ruleBatch,
                if (isFirstBatch) activeModels else emptyList(),
                if (isFirstBatch) profiles else emptyList(),
                if (isFirstBatch) activeApps else emptyList(),
                if (isFirstBatch) activeScenarios else emptyList(),
                if (isFirstBatch) activeReplies else emptyList(),
                if (isFirstBatch) activeBlacklists else emptyList(),
                if (isFirstBatch) deletedIds else emptyMap(),
                since
            )
            if (batchStats.isNotEmpty()) allStats.add(batchStats)
        }

        return allStats.joinToString("；")
    }

    // ========== 推送所有本地数据（首次同步）==========

    suspend fun pushAllLocalChanges(tenantId: String): String {
        val allRules = keywordRuleDao.getRulesByTenantSync(tenantId)
        val allModels = aiModelConfigDao.getModelsByTenantSync(tenantId)
        val profile = userStyleProfileDao.getProfileByTenantIdSync(tenantId)
        val profiles = if (profile != null) listOf(profile) else emptyList()
        val allApps = appConfigDao.getAppsByTenantSync(tenantId)
        val allScenarios = scenarioDao.getScenariosByTenantSync(tenantId)
        val allReplies = replyHistoryDao.getRepliesByTenantSync(tenantId)
        val allBlacklists = messageBlacklistDao.getByTenantSync(tenantId)

        // 分离正常数据和已删除数据
        val activeRules = allRules.filter { !it.deleted }
        // BUG-R14: deleted id 用 namespaced 形式
        val deletedRules = allRules.filter { it.deleted }.map { "${it.tenantId}_${it.id}" }
        val activeModels = allModels.filter { !it.deleted }
        val deletedModels = allModels.filter { it.deleted }.map { it.id.toString() }
        val activeApps = allApps.filter { !it.deleted }
        val deletedApps = allApps.filter { it.deleted }.map { it.packageName }
        val activeScenarios = allScenarios.filter { !it.deleted }
        val deletedScenarios = allScenarios.filter { it.deleted }.map { it.id.toString() }
        val activeReplies = allReplies.filter { !it.deleted }
        val deletedReplies = allReplies.filter { it.deleted }.map { it.id.toString() }
        val activeBlacklists = allBlacklists.filter { !it.deleted }
        val deletedBlacklists = allBlacklists.filter { it.deleted }.map { it.id.toString() }

        val deletedIds = buildMap {
            if (deletedRules.isNotEmpty()) put("keyword_rules", deletedRules)
            if (deletedModels.isNotEmpty()) put("ai_model_configs", deletedModels)
            if (deletedApps.isNotEmpty()) put("app_configs", deletedApps)
            if (deletedScenarios.isNotEmpty()) put("scenarios", deletedScenarios)
            if (deletedReplies.isNotEmpty()) put("reply_history", deletedReplies)
            if (deletedBlacklists.isNotEmpty()) put("message_blacklist", deletedBlacklists)
        }

        if (activeRules.isEmpty() && activeModels.isEmpty() && profiles.isEmpty() && activeApps.isEmpty() && activeScenarios.isEmpty() && activeReplies.isEmpty() && activeBlacklists.isEmpty() && deletedIds.isEmpty()) {
            Log.d("SyncManager", "本地无数据可推送")
            return ""
        }

        // 分批推送: 知识库规则按 PUSH_BATCH_SIZE 分批(避免单次 HTTP 推送 360+ 条超时)
        // 修复 BUG-R13: 原本一次性 doPush(activeRules=360) 导致服务端 batch_conn 在 60s 内无法执行全部 INSERT, 超时失败 → 本地 syncVersion=0 永远没更新 → Supabase 始终 180+ 条
        // 其他数据量小, 仅在第一批次一起推送
        val ruleBatches = activeRules.chunked(PUSH_BATCH_SIZE)
        val allStats = mutableListOf<String>()
        for ((i, ruleBatch) in ruleBatches.withIndex()) {
            val isFirstBatch = i == 0
            val batchStats = doPush(
                tenantId, ruleBatch,
                if (isFirstBatch) activeModels else emptyList(),
                if (isFirstBatch) profiles else emptyList(),
                if (isFirstBatch) activeApps else emptyList(),
                if (isFirstBatch) activeScenarios else emptyList(),
                if (isFirstBatch) activeReplies else emptyList(),
                if (isFirstBatch) activeBlacklists else emptyList(),
                if (isFirstBatch) deletedIds else emptyMap(),
                0L
            )
            if (batchStats.isNotEmpty()) allStats.add(batchStats)
        }
        return allStats.joinToString("；")
    }

    private suspend fun doPush(
        tenantId: String,
        rules: List<KeywordRuleEntity>,
        models: List<AIModelConfigEntity>,
        profiles: List<UserStyleProfileEntity>,
        apps: List<AppConfigEntity>,
        scenarios: List<ScenarioEntity>,
        replies: List<ReplyHistoryEntity>,
        blacklists: List<MessageBlacklistEntity>,
        deletedIds: Map<String, List<String>>,
        baseVersion: Long
    ): String {
        val request = PushChangesRequest(
            tenantId = tenantId,
            keywordRules = rules.map { it.toSyncModel() },
            aiModelConfigs = models.map { it.toSyncModel() },
            userStyleProfile = profiles.firstOrNull()?.toSyncModel(),
            appConfigs = apps.map { it.toSyncModel() },
            scenarios = scenarios.map { it.toSyncModel() },
            replyHistory = replies.map { it.toSyncModel() },
            messageBlacklist = blacklists.map { it.toSyncModel() },
            deletedIds = deletedIds,
            baseVersion = baseVersion
        )
        Log.d("SyncManager", "doPush: rules=${rules.size}, models=${models.size}, deletedIds=$deletedIds")

        val response = try {
            getSyncApiService().pushChanges(request)
        } catch (e: Exception) {
            Timber.e(e, "推送失败: ${e.message}")
            throw e
        }
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
            // 返回同步统计
            val stats = result.stats
            return if (stats != null) {
                "新增 ${stats.inserted} 条，更新 ${stats.updated} 条，删除 ${stats.deleted} 条"
            } else {
                buildString {
                    val details = mutableListOf<String>()
                    if (rules.isNotEmpty()) details.add("知识库${rules.size}条")
                    if (blacklists.isNotEmpty()) details.add("黑名单${blacklists.size}条")
                    if (models.isNotEmpty()) details.add("模型${models.size}条")
                    if (apps.isNotEmpty()) details.add("监控应用${apps.size}条")
                    if (scenarios.isNotEmpty()) details.add("场景${scenarios.size}条")
                    if (replies.isNotEmpty()) details.add("回复历史${replies.size}条")
                    if (profiles.isNotEmpty()) details.add("风格画像${profiles.size}条")
                    if (deletedIds.isNotEmpty()) {
                        val totalDeleted = deletedIds.values.sumOf { it.size }
                        if (totalDeleted > 0) details.add("删除${totalDeleted}条")
                    }
                    if (details.isNotEmpty()) details.joinToString("，") else ""
                }
            }
        }
        return ""
    }
    // ========== 应用服务端数据到本地 ==========

    private suspend fun applyServerDataToLocal(data: SyncAllData, tenantId: String) {
        // 知识库规则：服务端数据 upsert 到本地 (全量同步场景)
        // BUG-R14: 用 remoteId 做 upsert 维度, 避免重复插入
        data.keywordRules.forEach { rule ->
            val entity = rule.toEntity(tenantId)
            val remoteId = entity.remoteId
            if (remoteId != null) {
                val existing = keywordRuleDao.getByRemoteId(tenantId, remoteId)
                if (existing != null) {
                    // 已有: 更新内容保留本地 id
                    keywordRuleDao.updateByRemoteId(
                        tenantId, remoteId,
                        entity.keyword, entity.matchType, entity.replyTemplate, entity.category,
                        entity.targetType, entity.targetNamesJson, entity.priority, entity.enabled,
                        entity.updatedAt, entity.syncVersion, entity.deleted
                    )
                } else {
                    // 新增: 让 Room autogenerate Long id
                    keywordRuleDao.insertRule(entity)
                }
            } else {
                // 无 remoteId (旧数据): 直接 insert
                keywordRuleDao.insertRule(entity)
            }
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
        // BUG-R14: 增量同步用 remoteId 做 upsert 维度, 避免重复插入
        changes.keywordRules.forEach { rule ->
            val entity = rule.toEntity(tenantId)
            val remoteId = entity.remoteId
            if (remoteId != null) {
                val existing = keywordRuleDao.getByRemoteId(tenantId, remoteId)
                if (existing != null) {
                    keywordRuleDao.updateByRemoteId(
                        tenantId, remoteId,
                        entity.keyword, entity.matchType, entity.replyTemplate, entity.category,
                        entity.targetType, entity.targetNamesJson, entity.priority, entity.enabled,
                        entity.updatedAt, entity.syncVersion, entity.deleted
                    )
                } else {
                    keywordRuleDao.insertRule(entity)
                }
            } else {
                keywordRuleDao.insertRule(entity)
            }
        }
        changes.aiModelConfigs.forEach { aiModelConfigDao.insertModel(it.toEntity(tenantId)) }
        changes.userStyleProfile?.let { userStyleProfileDao.insertProfile(it.toEntity(tenantId)) }
        changes.appConfigs.forEach { appConfigDao.insertApp(it.toEntity(tenantId)) }
        changes.scenarios.forEach { scenarioDao.insertScenario(it.toEntity(tenantId)) }
        changes.replyHistory.forEach { replyHistoryDao.insertReply(it.toEntity(tenantId)) }
        changes.messageBlacklist.forEach { messageBlacklistDao.insert(it.toEntity(tenantId)) }

        // 处理删除 (服务端 String id → 通过 remoteId 查本地)
        changes.deletedIds.forEach { (entityType, ids) ->
            when (entityType) {
                "keyword_rules" -> ids.forEach { remoteId ->
                    keywordRuleDao.getByRemoteId(tenantId, remoteId)?.let {
                        keywordRuleDao.deleteById(it.id)
                    }
                }
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
                getSyncApiService().resolveConflict(ConflictResolveRequest(tenantId, resolutions))
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
        // BUG-R14 修复: 不复用服务端 String id (会破坏本地 UI 主键 Long 兼容性)
        // 改用 id=0 让 Room autogenerate; 服务端 id 存到 remoteId 字段做 upsert 维度
        id = 0,
        keyword = keyword.orEmpty(),
        matchType = matchType.orEmpty().ifEmpty { "CONTAINS" },
        replyTemplate = replyTemplate.orEmpty(),
        category = category.orEmpty(),
        targetType = targetType.orEmpty(),
        targetNamesJson = targetNamesJson.orEmpty(),
        priority = priority,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted,
        remoteId = id.takeIf { it.isNotEmpty() }
    )

    private fun SyncAIModelConfig.toEntity(tenantId: String) = AIModelConfigEntity(
        id = id,
        modelType = modelType.orEmpty(),
        modelName = modelName.orEmpty(),
        apiKey = apiKey.orEmpty(),
        apiEndpoint = apiEndpoint.orEmpty(),
        temperature = temperature,
        maxTokens = maxTokens,
        isDefault = isDefault,
        isEnabled = isEnabled,
        monthlyCost = monthlyCost,
        lastUsed = lastUsed,
        createdAt = createdAt,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )

    private fun SyncUserStyleProfile.toEntity(tenantId: String) = UserStyleProfileEntity(
        userId = userId.orEmpty(),
        formalityLevel = formalityLevel,
        enthusiasmLevel = enthusiasmLevel,
        professionalismLevel = professionalismLevel,
        wordCountPreference = wordCountPreference,
        commonPhrases = commonPhrases.orEmpty(),
        avoidPhrases = avoidPhrases.orEmpty(),
        learningSamples = learningSamples,
        accuracyScore = accuracyScore,
        lastTrained = lastTrained,
        createdAt = createdAt,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )

    private fun SyncAppConfig.toEntity(tenantId: String) = AppConfigEntity(
        packageName = packageName.orEmpty(),
        appName = appName.orEmpty(),
        iconUri = iconUri,
        isMonitored = isMonitored,
        createdAt = createdAt,
        lastUsed = lastUsed,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )

    private fun SyncScenario.toEntity(tenantId: String) = ScenarioEntity(
        id = id,
        name = name.orEmpty(),
        type = type.orEmpty().ifEmpty { "ALL_PROPERTIES" },
        targetId = targetId,
        description = description,
        createdAt = createdAt,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )

    private fun SyncReplyHistory.toEntity(tenantId: String) = ReplyHistoryEntity(
        id = id,
        sourceApp = sourceApp.orEmpty(),
        originalMessage = originalMessage.orEmpty(),
        generatedReply = generatedReply.orEmpty(),
        finalReply = finalReply.orEmpty(),
        ruleMatchedId = ruleMatchedId,
        modelUsedId = modelUsedId,
        styleApplied = styleApplied,
        sendTime = sendTime,
        modified = modified,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )

    private fun KeywordRuleEntity.toSyncModel() = SyncKeywordRule(
        // BUG-R14 修复: 用 "${tenantId}_${id}" 作为服务端唯一 id, 避免跨租户 id 冲突
        // Room 主键仍是 Long, 但同步到 Supabase 时变成全局唯一字符串 id
        id = "${tenantId}_${id}",
        keyword = keyword, matchType = matchType,
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

// SyncState 已迁至 com.csbaby.kefu.domain.model.SyncState（domain 层共享类型）
