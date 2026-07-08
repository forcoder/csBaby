package com.csbaby.kefu.data.sync

import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.local.entity.KeywordRuleEntity
import com.csbaby.kefu.data.remote.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 修复：立即同步按钮"新增 X 条，更新 Y 条，删除 Z 条"统计被分批用 ； 拼接成多段重复显示。
 *
 * 根因：
 *   pushAllLocalChanges / pushLocalChanges 按 PUSH_BATCH_SIZE=50 分批 doPush，
 *   每批 doPush 返回 "新增 50 条，更新 0 条，删除 0 条"，调用方用
 *   `allStats.joinToString("；")` 拼接，最终用户看到多段重复。
 *
 * 修复后行为：
 *   累加多批的 inserted/updated/deleted 数字（或本地推送数量兜底），
 *   最终返回单条 "新增 560 条，更新 0 条，删除 0 条"，绝不出现 ；。
 */
class SyncManagerStatsTest {

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
    private lateinit var mockAuthApi: AuthApiService
    private lateinit var mockSyncApi: SyncApiService

    private lateinit var dbStore: MutableMap<Long, KeywordRuleEntity>

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

        dbStore = mutableMapOf()

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

        coEvery { keywordRuleDao.insertRule(any()) } coAnswers {
            val rule = firstArg<KeywordRuleEntity>()
            if (rule.id != 0L) {
                dbStore[rule.id] = rule
                rule.id
            } else {
                val newId = (dbStore.keys.maxOrNull() ?: 0L) + 1
                dbStore[newId] = rule.copy(id = newId)
                newId
            }
        }
        coEvery { keywordRuleDao.insertRules(any()) } coAnswers {
            val rules = firstArg<List<KeywordRuleEntity>>()
            rules.forEach { dbStore[it.id] = it }
            Unit
        }
        coEvery { keywordRuleDao.getRulesByTenantSync(any()) } coAnswers {
            val tenant = firstArg<String>()
            dbStore.values.filter { it.tenantId == tenant }.toList()
        }
        coEvery { keywordRuleDao.updateSyncVersion(any(), any()) } coAnswers {
            val id = firstArg<Long>()
            val ver = secondArg<Long>()
            dbStore[id]?.let { dbStore[id] = it.copy(syncVersion = ver) }
            Unit
        }

        coEvery { aiModelConfigDao.getModelsByTenantSync(any()) } returns emptyList()
        coEvery { aiModelConfigDao.updateSyncVersion(any(), any()) } returns Unit
        coEvery { userStyleProfileDao.getProfileByTenantIdSync(any()) } returns null
        coEvery { appConfigDao.getAppsByTenantSync(any()) } returns emptyList()
        coEvery { appConfigDao.updateSyncVersion(any(), any()) } returns Unit
        coEvery { scenarioDao.getScenariosByTenantSync(any()) } returns emptyList()
        coEvery { scenarioDao.updateSyncVersion(any(), any()) } returns Unit
        coEvery { replyHistoryDao.getRepliesByTenantSync(any()) } returns emptyList()
        coEvery { replyHistoryDao.updateSyncVersion(any(), any()) } returns Unit
        coEvery { messageBlacklistDao.getByTenantSync(any()) } returns emptyList()
        coEvery { messageBlacklistDao.updateSyncVersion(any(), any()) } returns Unit

        coEvery { syncCheckpointDao.getCheckpoint(any()) } returns null
        coEvery { syncCheckpointDao.updateSyncing(any(), any()) } returns Unit
        coEvery { syncCheckpointDao.updateSyncSuccess(any(), any(), any()) } returns Unit
        coEvery { syncCheckpointDao.updateLastError(any(), any()) } returns Unit

        syncManager = SyncManager(
            keywordRuleDao, aiModelConfigDao, userStyleProfileDao,
            appConfigDao, scenarioDao, replyHistoryDao,
            messageBlacklistDao, syncCheckpointDao, authManager, syncQueue
        )

        mockAuthApi = mockk(relaxed = true)
        mockSyncApi = mockk(relaxed = true)

        val mockClient = mockk<AuthenticatedSyncClient>(relaxed = true)
        every { mockClient.apiService } returns mockSyncApi
        every { mockClient.authApiService } returns mockAuthApi

        val field = SyncManager::class.java.getDeclaredField("syncClient")
        field.isAccessible = true
        field.set(syncManager, mockClient)

        val apiField = SyncManager::class.java.getDeclaredField("syncApiService")
        apiField.isAccessible = true
        apiField.set(syncManager, mockSyncApi)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun seedRules(count: Int, tenantId: String = "tenant_real") {
        repeat(count) { i ->
            val id = (i + 1).toLong()
            dbStore[id] = KeywordRuleEntity(
                id = id,
                keyword = "kw_$i",
                matchType = "CONTAINS",
                replyTemplate = "reply_$i",
                category = "cat",
                tenantId = tenantId,
                syncVersion = 0L,
                deleted = false
            )
        }
    }

    /**
     * 构造服务端响应：每批都返回给定的 stats（累加通过这里注入逐批数字即可）
     */
    private fun stubPushBatchWithStats(serverStats: SyncStats) {
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, message = "ok",
            data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                newServerVersion = 1L, serverTime = 1L,
                stats = serverStats
            )
        )
    }

    private fun stubPushBatchNoStats() {
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, message = "ok",
            data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                newServerVersion = 1L, serverTime = 1L,
                stats = null
            )
        )
    }

    /**
     * 核心断言：返回值单条且无 分号
     */
    private fun assertSingleSummaryNoSemicolon(actual: String) {
        assertFalse(
            "返回值不应包含中文分号 ；, 实际=$actual",
            actual.contains("；")
        )
    }

    // ========== TC-01 ~ TC-03：正常场景 ==========

    /**
     * TC-01: 600 条规则分 12 批，服务端每批返回 inserted=50，
     *        汇总后应该是 "新增 600 条，更新 0 条，删除 0 条"。
     */
    @Test
    fun `pushAllLocalChanges 600 条规则 - 汇总单条且不出现 分号`() = runBlocking {
        seedRules(600, tenantId = "tenant_real")
        stubPushBatchWithStats(SyncStats(inserted = 50, updated = 0, deleted = 0))

        val stats = syncManager.pushAllLocalChanges("tenant_real")

        assertSingleSummaryNoSemicolon(stats)
        assertEquals("新增 600 条，更新 0 条，删除 0 条", stats)
    }

    /**
     * TC-02: 560 条规则，服务端每批返回 inserted=50, updated=0, deleted=0
     *        560 / 50 = 12 批（11 批 × 50 + 1 批 × 10），每批都返回 50
     *        → 累加 = 12 × 50 = 600
     */
    @Test
    fun `pushAllLocalChanges 累加各批 inserted updated deleted 数字`() = runBlocking {
        seedRules(560, tenantId = "tenant_real")
        // 服务端每批返回的 stats: 模拟 inserted=50, updated=0, deleted=0
        // mock 是固定的 50,与 batch 实际大小无关,所以累加 = 批数 × 50 = 12 × 50 = 600
        stubPushBatchWithStats(SyncStats(inserted = 50, updated = 0, deleted = 0))

        val stats = syncManager.pushAllLocalChanges("tenant_real")

        assertSingleSummaryNoSemicolon(stats)
        assertEquals("新增 600 条，更新 0 条，删除 0 条", stats)
    }

    /**
     * TC-03: 包含 inserted/updated/deleted 三个数字混合：每批 mock 返回
     *        (100, 50, 20)，推送 3 批后累计 (300, 150, 60)
     */
    @Test
    fun `pushAllLocalChanges 包含 inserted updated deleted 三个字段`() = runBlocking {
        seedRules(150, tenantId = "tenant_real") // 3 批
        stubPushBatchWithStats(SyncStats(inserted = 100, updated = 50, deleted = 20))

        val stats = syncManager.pushAllLocalChanges("tenant_real")

        assertSingleSummaryNoSemicolon(stats)
        assertEquals("新增 300 条，更新 150 条，删除 60 条", stats)
    }

    // ========== TC-04 ~ TC-05：边界场景 ==========

    /**
     * TC-04: 1 条规则 - 1 批 → "新增 1 条，更新 0 条，删除 0 条"
     */
    @Test
    fun `pushAllLocalChanges 1 条规则 - 单条汇总`() = runBlocking {
        seedRules(1, tenantId = "tenant_real")
        stubPushBatchWithStats(SyncStats(inserted = 1, updated = 0, deleted = 0))

        val stats = syncManager.pushAllLocalChanges("tenant_real")

        assertSingleSummaryNoSemicolon(stats)
        assertEquals("新增 1 条，更新 0 条，删除 0 条", stats)
    }

    /**
     * TC-05: 50 条规则恰好 1 批 → 单条 "新增 50 条..."
     */
    @Test
    fun `pushAllLocalChanges 50 条规则 - 边界恰好 1 批`() = runBlocking {
        seedRules(50, tenantId = "tenant_real")
        stubPushBatchWithStats(SyncStats(inserted = 50, updated = 0, deleted = 0))

        val stats = syncManager.pushAllLocalChanges("tenant_real")

        assertSingleSummaryNoSemicolon(stats)
        assertEquals("新增 50 条，更新 0 条，删除 0 条", stats)
    }

    // ========== TC-06 ~ TC-07：异常/兜底场景 ==========

    /**
     * TC-06: 服务端未返回 stats（stats==null），用本地推送数量兜底
     *        120 条规则分 3 批 → 兜底按类型累加（每批 50 条）→ 单条汇总
     */
    @Test
    fun `pushAllLocalChanges 服务端 stats 为 null - 兜底本地数量且无 分号`() = runBlocking {
        seedRules(120, tenantId = "tenant_real")
        stubPushBatchNoStats()

        val stats = syncManager.pushAllLocalChanges("tenant_real")

        assertSingleSummaryNoSemicolon(stats)
        // 兜底应包含 "知识库" 之类按类型汇总；不应是空字符串
        assertTrue("兜底汇总不应为空: $stats", stats.isNotEmpty())
        assertTrue("兜底汇总应包含推送数量信息: $stats", stats.contains("120") || stats.contains("50"))
    }

    /**
     * TC-07: 推送成功但服务端 accepted=false（pushChanges 失败路径）
     *        → 返回单条（或空）但不应抛异常
     */
    @Test
    fun `pushAllLocalChanges 推送响应 isSuccess=false - 不抛异常且无 分号`() = runBlocking {
        seedRules(50, tenantId = "tenant_real")
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 1, message = "server error", data = null
        )

        val stats = syncManager.pushAllLocalChanges("tenant_real")

        // 即使失败，返回值也不应包含 分号
        assertSingleSummaryNoSemicolon(stats)
    }

    // ========== TC-08：增量同步路径 ==========

    /**
     * TC-08: 增量同步 pushLocalChanges 同样单条汇总
     *        600 条规则从 since=0L 开始走 pushLocalChanges 路径
     *        600 / 50 = 12 批，每批 mock 返回 inserted=50 → 累加 600
     */
    @Test
    fun `pushLocalChanges 多批 - 单条汇总且不出现 分号`() = runBlocking {
        seedRules(600, tenantId = "tenant_real")
        stubPushBatchWithStats(SyncStats(inserted = 50, updated = 0, deleted = 0))

        val stats = syncManager.pushLocalChanges("tenant_real", since = 0L)

        assertSingleSummaryNoSemicolon(stats)
        // 12 批 × 50 = 600
        assertEquals("新增 600 条，更新 0 条，删除 0 条", stats)
    }

    /**
     * TC-09 (边界值): 空数据 - 返回空字符串
     */
    @Test
    fun `pushAllLocalChanges 空数据 - 返回空字符串`() = runBlocking {
        stubPushBatchWithStats(SyncStats(inserted = 0, updated = 0, deleted = 0))

        val stats = syncManager.pushAllLocalChanges("tenant_real")

        assertEquals("", stats)
    }
}