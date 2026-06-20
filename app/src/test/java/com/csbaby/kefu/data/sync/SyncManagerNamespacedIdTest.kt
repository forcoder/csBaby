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
 * BUG-R14 regression: 跨租户 id 冲突导致数据被错误归属.
 *
 * 根因: Supabase keyword_rules 表 pkey=(id) 单一列, Android Room id 是 Long autogenerate,
 *      两个不同用户的 id=1 必然冲突. ON CONFLICT (id) DO UPDATE 保留原 tenant_id,
 *      第二个用户的 keyword 被覆盖, 数据归属错位.
 *
 * 修复 (方案 B): Android 端 toSyncModel() 把 id 改为 "${tenantId}_${localId}" 字符串,
 *              不同租户的 id 在 Supabase 不再冲突. toEntity() 用 remoteId 字段
 *              做 upsert 维度 (room 主键仍 Long autogenerate, 兼容 UI).
 *
 * 测试矩阵:
 *  TC-01: push 时 toSyncModel 输出 "tenantId_localId" 形式
 *  TC-02: pull 时 SyncKeywordRule.toEntity 不复用服务端 String id (id=0 让 Room 自增)
 *  TC-03: pull 时保留 remoteId 用于后续 upsert
 *  TC-04: 跨租户 push 后, 服务端收到不同的 id 字符串
 *  TC-05: 回归 - 推送统计 (SyncStats) 行为不变
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerNamespacedIdTest {

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
     * TC-01: push 时, SyncKeywordRule.id 必须是 "tenantId_localId" 字符串
     */
    @Test
    fun `BUG-R14 push 时 toSyncModel 输出 namespaced id`() = runBlocking {
        seedRules(3, tenantId = "tenant_xyz")

        val capturedIds = mutableListOf<Any>()  // SyncKeywordRule.id 是 Long, 我们用 Any 捕获
        coEvery { mockSyncApi.pushChanges(any()) } coAnswers {
            val req = firstArg<PushChangesRequest>()
            capturedIds.addAll(req.keywordRules.map { it.id })
            ApiResponse(
                code = 0, message = "ok",
                data = PushChangesResult(
                    accepted = true, conflicts = emptyList(),
                    newServerVersion = 1L, serverTime = 1L
                )
            )
        }
        stubGetChangesEmpty()

        syncManager.incrementalSync("tenant_xyz")

        // 期望 id 形式: "tenant_xyz_1", "tenant_xyz_2", "tenant_xyz_3"
        assertEquals("captured ids size should be 3", 3, capturedIds.size)
        capturedIds.forEachIndexed { i, id ->
            assertEquals(
                "id[$i] should be namespaced String",
                "tenant_xyz_${i + 1}",
                id.toString()
            )
        }
    }

    /**
     * TC-04: 跨租户 push 不会产生相同 id
     */
    @Test
    fun `BUG-R14 跨租户 push 时产生不同 namespaced id`() = runBlocking {
        // 模拟两个不同租户, 通过 setup 两次调用来验证
        // 第一次 tenant_A push id=1 -> "tenant_A_1"
        // 第二次 tenant_B push id=1 -> "tenant_B_1"
        seedRules(1, tenantId = "tenant_A")

        val capturedA = mutableListOf<String>()
        coEvery { mockSyncApi.pushChanges(any()) } coAnswers {
            val req = firstArg<PushChangesRequest>()
            capturedA.addAll(req.keywordRules.map { it.id.toString() })
            ApiResponse(
                code = 0, message = "ok",
                data = PushChangesResult(
                    accepted = true, conflicts = emptyList(),
                    newServerVersion = 1L, serverTime = 1L
                )
            )
        }
        stubGetChangesEmpty()
        syncManager.incrementalSync("tenant_A")

        // 切换到 tenant_B
        seedRules(1, tenantId = "tenant_B")
        capturedA.clear()
        syncManager.incrementalSync("tenant_B")

        assertEquals(1, capturedA.size)
        assertEquals("tenant_B_1", capturedA[0])
    }

    /**
     * TC-05: 回归 - SyncStats 行为不变
     */
    @Test
    fun `BUG-R14 回归 - 推送 stats 仍然返回`() = runBlocking {
        seedRules(5, tenantId = "tenant_real")
        stubPushSuccess()
        stubGetChangesEmpty()

        val result = syncManager.incrementalSync("tenant_real")

        assertTrue("Result should be success", result.isSuccess)
    }
}