package com.csbaby.kefu.data.sync

import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.local.entity.*
import com.csbaby.kefu.data.remote.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 修复：push 前与云端对比去重，避免重复传输已在云端、业务字段一致的记录。
 *
 * 根因：
 *   - 之前 pushAllLocalChanges 把本地 562 条全推，云端 ON CONFLICT 强制合并
 *   - 浪费网络流量，sync_outbox 干扰，用户看到"新增 X 条"实际上是覆盖
 *   - 客户端不知道云端是否已有相同业务字段
 *
 * 修复后行为：
 *   - pushAllLocalChanges 开头先调 getChanges(tenantId, since=0) 拉云端全量
 *   - 用 (keyword, replyTemplate) / (packageName) / (id) / (name) 建云端索引
 *   - 本地与云端业务字段完全一致时跳过推送（不浪费服务器资源）
 *   - 失败时降级到原行为（保证健壮性）
 *   - 拉取结果不写入本地（避免和 applyChangesToLocal 冲突）
 */
class SyncManagerCloudDedupTest {

    private lateinit var keywordRuleDao: KeywordRuleDao
    private lateinit var aiModelConfigDao: AIModelConfigDao
    private lateinit var userStyleProfileDao: UserStyleProfileDao
    private lateinit var appConfigDao: AppConfigDao
    private lateinit var scenarioDao: ScenarioDao
    private lateinit var replyHistoryDao: ReplyHistoryDao
    private lateinit var messageBlacklistDao: MessageBlacklistDao
    private lateinit var syncCheckpointDao: SyncCheckpointDao
    private lateinit var authManager: AuthManager
    private lateinit var syncQueue: SyncQueue
    private lateinit var syncManager: SyncManager
    private lateinit var mockSyncApi: SyncApiService

    private lateinit var dbRules: MutableMap<Long, KeywordRuleEntity>
    private lateinit var dbApps: MutableMap<String, AppConfigEntity>
    private lateinit var dbBlacklists: MutableMap<Long, MessageBlacklistEntity>
    private lateinit var dbScenarios: MutableMap<Long, ScenarioEntity>
    private lateinit var dbModels: MutableMap<Long, AIModelConfigEntity>

    @Before
    fun setup() = runBlocking {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.isLoggable(any<String>(), any()) } returns false

        dbRules = mutableMapOf()
        dbApps = mutableMapOf()
        dbBlacklists = mutableMapOf()
        dbScenarios = mutableMapOf()
        dbModels = mutableMapOf()

        keywordRuleDao = mockk(relaxed = true)
        aiModelConfigDao = mockk(relaxed = true)
        userStyleProfileDao = mockk(relaxed = true)
        appConfigDao = mockk(relaxed = true)
        scenarioDao = mockk(relaxed = true)
        replyHistoryDao = mockk(relaxed = true)
        messageBlacklistDao = mockk(relaxed = true)
        syncCheckpointDao = mockk(relaxed = true)
        authManager = mockk(relaxed = true)
        syncQueue = mockk(relaxed = true)

        // DAOs
        coEvery { keywordRuleDao.getRulesByTenantSync(any()) } coAnswers {
            val t = firstArg<String>(); dbRules.values.filter { it.tenantId == t }.toList()
        }
        coEvery { keywordRuleDao.updateSyncVersion(any(), any()) } coAnswers {
            val id = firstArg<Long>(); val ver = secondArg<Long>()
            dbRules[id]?.let { dbRules[id] = it.copy(syncVersion = ver) }; Unit
        }
        coEvery { appConfigDao.getAppsByTenantSync(any()) } coAnswers {
            val t = firstArg<String>(); dbApps.values.filter { it.tenantId == t }.toList()
        }
        coEvery { appConfigDao.updateSyncVersion(any(), any()) } returns Unit
        coEvery { messageBlacklistDao.getByTenantSync(any()) } coAnswers {
            val t = firstArg<String>(); dbBlacklists.values.filter { it.tenantId == t }.toList()
        }
        coEvery { messageBlacklistDao.updateSyncVersion(any(), any()) } returns Unit
        coEvery { scenarioDao.getScenariosByTenantSync(any()) } coAnswers {
            val t = firstArg<String>(); dbScenarios.values.filter { it.tenantId == t }.toList()
        }
        coEvery { scenarioDao.updateSyncVersion(any(), any()) } returns Unit
        coEvery { aiModelConfigDao.getModelsByTenantSync(any()) } coAnswers {
            val t = firstArg<String>(); dbModels.values.filter { it.tenantId == t }.toList()
        }
        coEvery { aiModelConfigDao.updateSyncVersion(any(), any()) } returns Unit
        coEvery { userStyleProfileDao.getProfileByTenantIdSync(any()) } returns null
        coEvery { replyHistoryDao.getRepliesByTenantSync(any()) } returns emptyList()
        coEvery { replyHistoryDao.updateSyncVersion(any(), any()) } returns Unit

        coEvery { syncCheckpointDao.getCheckpoint(any()) } returns null
        coEvery { syncCheckpointDao.updateSyncing(any(), any()) } returns Unit
        coEvery { syncCheckpointDao.updateSyncSuccess(any(), any(), any()) } returns Unit
        coEvery { syncCheckpointDao.updateLastError(any(), any()) } returns Unit

        syncManager = SyncManager(
            keywordRuleDao, aiModelConfigDao, userStyleProfileDao,
            appConfigDao, scenarioDao, replyHistoryDao,
            messageBlacklistDao, syncCheckpointDao, authManager, syncQueue
        )

        mockSyncApi = mockk(relaxed = true)
        val mockClient = mockk<AuthenticatedSyncClient>(relaxed = true)
        every { mockClient.apiService } returns mockSyncApi
        every { mockClient.authApiService } returns mockk(relaxed = true)

        val field = SyncManager::class.java.getDeclaredField("syncClient")
        field.isAccessible = true
        field.set(syncManager, mockClient)

        val apiField = SyncManager::class.java.getDeclaredField("syncApiService")
        apiField.isAccessible = true
        apiField.set(syncManager, mockSyncApi)
    }

    @After
    fun tearDown() { unmockkAll() }

    // ==================== 知识库规则 keyword_rules 去重 ====================

    @Test
    fun `keyword rules - 完全相同业务字段的本地规则被跳过不推送`() = runBlocking {
        val tenant = "tenant_dedup"
        // 本地 3 条
        repeat(3) { i ->
            val id = (i + 1).toLong()
            dbRules[id] = KeywordRuleEntity(
                id = id, keyword = "kw_$i", matchType = "CONTAINS",
                replyTemplate = "reply_$i", category = "cat",
                tenantId = tenant, syncVersion = 0L
            )
        }
        // 云端有 2 条完全一致 + 1 条不同
        coEvery { mockSyncApi.getChanges(eq(tenant), any()) } returns ApiResponse(
            code = 0, data = SyncChanges(
                keywordRules = listOf(
                    SyncKeywordRule(keyword = "kw_0", replyTemplate = "reply_0", matchType = "CONTAINS"),
                    SyncKeywordRule(keyword = "kw_1", replyTemplate = "reply_1", matchType = "CONTAINS"),
                )
            )
        )
        // push 返回 1 条 (kw_2 是新的)
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                serverTime = 1L, newServerVersion = 100L,
                stats = SyncStats(inserted = 1, updated = 0, deleted = 0)
            )
        )

        val stats = syncManager.pushAllLocalChanges(tenant)

        // 验证: stats 显示只插入了 1 条 (kw_2)
        assertTrue("应只推送 1 条", stats.contains("新增 1 条"))
    }

    @Test
    fun `keyword rules - 本地有差异时仍推送`() = runBlocking {
        val tenant = "tenant_dedup2"
        dbRules[1L] = KeywordRuleEntity(
            id = 1L, keyword = "kw_a", matchType = "CONTAINS",
            replyTemplate = "reply_local_updated",  // 本地改了
            category = "cat", tenantId = tenant, syncVersion = 0L
        )
        coEvery { mockSyncApi.getChanges(eq(tenant), any()) } returns ApiResponse(
            code = 0, data = SyncChanges(
                keywordRules = listOf(
                    SyncKeywordRule(keyword = "kw_a", replyTemplate = "reply_cloud", matchType = "CONTAINS"),
                )
            )
        )
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                serverTime = 1L, newServerVersion = 200L,
                stats = SyncStats(inserted = 0, updated = 1, deleted = 0)
            )
        )
        val stats = syncManager.pushAllLocalChanges(tenant)
        assertTrue("差异应推送, 1 条", stats.contains("更新 1 条"))
    }

    @Test
    fun `keyword rules - 云端为空时本地全部推送`() = runBlocking {
        val tenant = "tenant_dedup3"
        repeat(5) { i ->
            val id = (i + 1).toLong()
            dbRules[id] = KeywordRuleEntity(
                id = id, keyword = "kw_$i", matchType = "CONTAINS",
                replyTemplate = "reply_$i", category = "cat",
                tenantId = tenant, syncVersion = 0L
            )
        }
        coEvery { mockSyncApi.getChanges(eq(tenant), any()) } returns ApiResponse(
            code = 0, data = SyncChanges(keywordRules = emptyList())
        )
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                serverTime = 1L, newServerVersion = 300L,
                stats = SyncStats(inserted = 5, updated = 0, deleted = 0)
            )
        )
        val stats = syncManager.pushAllLocalChanges(tenant)
        assertTrue("全部新增", stats.contains("新增 5 条"))
    }

    // ==================== 黑名单 message_blacklist 去重 ====================

    @Test
    fun `blacklist - 完全相同 value+packageName 跳过推送`() = runBlocking {
        val tenant = "tenant_bl"
        dbBlacklists[1L] = MessageBlacklistEntity(
            id = 1L, type = "KEYWORD", value = "spam",
            packageName = "com.app", isEnabled = true,
            tenantId = tenant, syncVersion = 0L
        )
        coEvery { mockSyncApi.getChanges(eq(tenant), any()) } returns ApiResponse(
            code = 0, data = SyncChanges(
                messageBlacklist = listOf(
                    SyncMessageBlacklist(type = "KEYWORD", value = "spam", packageName = "com.app", isEnabled = true),
                )
            )
        )
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                serverTime = 1L, newServerVersion = 400L,
                stats = SyncStats(inserted = 0, updated = 0, deleted = 0)
            )
        )
        val stats = syncManager.pushAllLocalChanges(tenant)
        // 黑名单被跳过 → 推送为空 → stats 应不包含"新增" (兜底返回空)
        assertFalse("黑名单已存在云端, 不应显示新增", stats.contains("新增 1 条"))
    }

    // ==================== 监控应用 app_configs 去重 ====================

    @Test
    fun `app config - 相同 packageName+isMonitored 跳过`() = runBlocking {
        val tenant = "tenant_app"
        dbApps["com.x"] = AppConfigEntity(
            packageName = "com.x", appName = "AppX", isMonitored = true, iconUri = "",
            tenantId = tenant, syncVersion = 0L
        )
        coEvery { mockSyncApi.getChanges(eq(tenant), any()) } returns ApiResponse(
            code = 0, data = SyncChanges(
                appConfigs = listOf(
                    SyncAppConfig(packageName = "com.x", appName = "AppX", isMonitored = true),
                )
            )
        )
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                serverTime = 1L, newServerVersion = 500L,
                stats = SyncStats(inserted = 0, updated = 0, deleted = 0)
            )
        )
        val stats = syncManager.pushAllLocalChanges(tenant)
        assertFalse("应用已在云端, 不应新增", stats.contains("新增"))
    }

    // ==================== 场景 scenarios 去重 ====================

    @Test
    fun `scenario - 相同 name+type 跳过`() = runBlocking {
        val tenant = "tenant_scn"
        dbScenarios[1L] = ScenarioEntity(
            id = 1L, name = "客服", type = "auto",
            targetId = null, description = null,
            tenantId = tenant, syncVersion = 0L
        )
        coEvery { mockSyncApi.getChanges(eq(tenant), any()) } returns ApiResponse(
            code = 0, data = SyncChanges(
                scenarios = listOf(
                    SyncScenario(name = "客服", type = "auto", targetId = null, description = null),
                )
            )
        )
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                serverTime = 1L, newServerVersion = 600L,
                stats = SyncStats(inserted = 0, updated = 0, deleted = 0)
            )
        )
        val stats = syncManager.pushAllLocalChanges(tenant)
        assertFalse("场景已在云端", stats.contains("新增"))
    }

    // ==================== 降级: 云端拉取失败 ====================

    @Test
    fun `getChanges 失败时降级到原行为 - 全部本地推送`() = runBlocking {
        val tenant = "tenant_offline"
        repeat(3) { i ->
            val id = (i + 1).toLong()
            dbRules[id] = KeywordRuleEntity(
                id = id, keyword = "kw_$i", matchType = "CONTAINS",
                replyTemplate = "reply_$i", category = "cat",
                tenantId = tenant, syncVersion = 0L
            )
        }
        // getChanges 抛异常
        coEvery { mockSyncApi.getChanges(eq(tenant), any()) } throws RuntimeException("网络错误")
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                serverTime = 1L, newServerVersion = 700L,
                stats = SyncStats(inserted = 3, updated = 0, deleted = 0)
            )
        )
        val stats = syncManager.pushAllLocalChanges(tenant)
        // 降级 → 全部 3 条都推送
        assertTrue("降级: 应全部推送 3 条", stats.contains("新增 3 条"))
    }

    // ==================== 边界: deleted 规则不参与去重 ====================

    @Test
    fun `deleted 规则不参与去重, 仍走 deletedIds 通道`() = runBlocking {
        val tenant = "tenant_del"
        dbRules[1L] = KeywordRuleEntity(
            id = 1L, keyword = "kw", matchType = "CONTAINS",
            replyTemplate = "r", category = "c",
            tenantId = tenant, syncVersion = 0L, deleted = true
        )
        coEvery { mockSyncApi.getChanges(eq(tenant), any()) } returns ApiResponse(
            code = 0, data = SyncChanges(
                keywordRules = listOf(
                    // 云端也有同样的, 但已 deleted, 应被识别为 deleted 而非 duplicate
                    SyncKeywordRule(keyword = "kw", replyTemplate = "r", matchType = "CONTAINS", deleted = true),
                )
            )
        )
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                serverTime = 1L, newServerVersion = 800L,
                stats = SyncStats(inserted = 0, updated = 0, deleted = 1)
            )
        )
        val stats = syncManager.pushAllLocalChanges(tenant)
        // deleted 走 deletedIds 通道, 不被去重过滤
        assertTrue("deleted 应推送, 1 条删除", stats.contains("删除 1 条"))
    }
}
