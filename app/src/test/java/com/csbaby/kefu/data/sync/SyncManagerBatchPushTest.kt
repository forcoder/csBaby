package com.csbaby.kefu.data.sync

import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.local.entity.KeywordRuleEntity
import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.remote.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * BUG-R13 regression: pushAllLocalChanges 必须分批推送,
 *                  否则 300+ 条规则一次性 doPush 会超时失败导致本地 180+ 服务端.
 *
 * 根因: 增量同步路径 pushLocalChanges() 已经分批(chunked 50), 但
 *      首次同步路径 pushAllLocalChanges() 直接一次 doPush 全部规则。
 *      服务端 batch_conn 在 60s 内无法执行 360 条 INSERT, 整个请求超时。
 *
 * 修复后行为: pushAllLocalChanges() 也按 PUSH_BATCH_SIZE=50 分批 doPush。
 *
 * 测试策略: 通过 incrementalSync(since=0) 触发 pushAllLocalChanges 路径,
 *          然后用 coVerify 验证 pushChanges 调用次数 (mockk 1.13 + suspend fun
 *          在协程内调用时 coVerify 正常工作)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerBatchPushTest {

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
        every { keywordRuleDao.getRulesByTenant(any()) } answers {
            val tenant = firstArg<String>()
            flowOf(dbStore.values.filter { it.tenantId == tenant }.toList())
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

        // 默认 checkpoint 不存在 → 首次同步路径 (pushAllLocalChanges)
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

        // syncApiService 是 init 时赋值的 val, 也需要反射替换, 否则指向真实的 OkHttp client
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

    private fun stubPushSuccess() {
        coEvery { mockSyncApi.pushChanges(any()) } returns ApiResponse(
            code = 0, message = "ok",
            data = PushChangesResult(
                accepted = true, conflicts = emptyList(),
                newServerVersion = 1L, serverTime = 1L
            )
        )
    }

    private fun stubGetChangesEmpty() {
        coEvery { mockSyncApi.getChanges(any(), any()) } returns ApiResponse(
            code = 0, message = "ok",
            data = SyncChanges(
                keywordRules = emptyList(), aiModelConfigs = emptyList(),
                appConfigs = emptyList(), scenarios = emptyList(),
                replyHistory = emptyList(), messageBlacklist = emptyList(),
                userStyleProfile = null, deletedIds = emptyMap(),
                nextCursor = null, hasMore = false
            )
        )
    }

    /**
     * TC-01: 首次同步 360 条规则必须分批, pushChanges 调用次数 = ceil(360/50) = 8
     */
    @Test
    fun `BUG-R13 pushAllLocalChanges 360 条规则 - 必须分 8 次 pushChanges`() = runBlocking {
        seedRules(360, tenantId = "tenant_real")
        stubPushSuccess()
        stubGetChangesEmpty()

        syncManager.incrementalSync("tenant_real")

        // 360 / 50 = 7.2 → 向上取整 = 8
        coVerify(exactly = 8) { mockSyncApi.pushChanges(any()) }
    }

    /**
     * TC-02: 360 条规则的累计推送量必须等于 360 (无丢失)
     */
    @Test
    fun `BUG-R13 pushAllLocalChanges 累计推送数量等于本地数量 - 无丢失`() = runBlocking {
        seedRules(360, tenantId = "tenant_real")

        val capturedBatches = mutableListOf<Int>()
        coEvery { mockSyncApi.pushChanges(any()) } coAnswers {
            capturedBatches.add(firstArg<PushChangesRequest>().keywordRules.size)
            ApiResponse(
                code = 0, message = "ok",
                data = PushChangesResult(
                    accepted = true, conflicts = emptyList(),
                    newServerVersion = 1L, serverTime = 1L
                )
            )
        }
        stubGetChangesEmpty()

        syncManager.incrementalSync("tenant_real")

        val totalPushed = capturedBatches.sum()
        assertEquals("累计推送数量必须等于本地数量 360, 实际=$totalPushed, batches=$capturedBatches",
            360, totalPushed)

        // 每个 batch 不应超过 PUSH_BATCH_SIZE=50
        assertTrue("每个 batch 不应超过 50, batches=$capturedBatches",
            capturedBatches.all { it <= 50 })
    }

    /**
     * TC-03: 1 条规则 - 1 次 pushChanges
     */
    @Test
    fun `BUG-R13 pushAllLocalChanges 1 条规则 - 1 次 pushChanges`() = runBlocking {
        seedRules(1, tenantId = "tenant_real")
        stubPushSuccess()
        stubGetChangesEmpty()

        syncManager.incrementalSync("tenant_real")

        coVerify(exactly = 1) { mockSyncApi.pushChanges(any()) }
    }

    /**
     * TC-04: 边界 - 50 条规则恰好 1 批
     */
    @Test
    fun `BUG-R13 pushAllLocalChanges 50 条规则 - 1 次 pushChanges`() = runBlocking {
        seedRules(50, tenantId = "tenant_real")
        stubPushSuccess()
        stubGetChangesEmpty()

        syncManager.incrementalSync("tenant_real")

        coVerify(exactly = 1) { mockSyncApi.pushChanges(any()) }
    }

    /**
     * TC-05: 边界 - 51 条规则分 2 批 (50 + 1)
     */
    @Test
    fun `BUG-R13 pushAllLocalChanges 51 条规则 - 2 次 pushChanges`() = runBlocking {
        seedRules(51, tenantId = "tenant_real")
        stubPushSuccess()
        stubGetChangesEmpty()

        syncManager.incrementalSync("tenant_real")

        coVerify(exactly = 2) { mockSyncApi.pushChanges(any()) }
    }

    /**
     * TC-06: 空数据 - 不调用 pushChanges
     */
    @Test
    fun `BUG-R13 pushAllLocalChanges 0 条规则 - 0 次 pushChanges`() = runBlocking {
        stubPushSuccess()
        stubGetChangesEmpty()

        syncManager.incrementalSync("tenant_real")

        coVerify(exactly = 0) { mockSyncApi.pushChanges(any()) }
    }

    /**
     * TC-07: 1000 条大批量 - 20 次 pushChanges (无截断)
     */
    @Test
    fun `BUG-R13 pushAllLocalChanges 1000 条规则 - 20 次 pushChanges`() = runBlocking {
        seedRules(1000, tenantId = "tenant_real")

        val capturedBatches = mutableListOf<Int>()
        coEvery { mockSyncApi.pushChanges(any()) } coAnswers {
            capturedBatches.add(firstArg<PushChangesRequest>().keywordRules.size)
            ApiResponse(
                code = 0, message = "ok",
                data = PushChangesResult(
                    accepted = true, conflicts = emptyList(),
                    newServerVersion = 1L, serverTime = 1L
                )
            )
        }
        stubGetChangesEmpty()

        syncManager.incrementalSync("tenant_real")

        coVerify(exactly = 20) { mockSyncApi.pushChanges(any()) }
        assertEquals(1000, capturedBatches.sum())
    }
}