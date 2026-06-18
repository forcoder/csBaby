package com.csbaby.kefu.data.sync

import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.remote.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * BUG-R8 regression: login() 成功后未触发 fullSync,导致 UI 只显示"登录成功"无具体同步信息
 *
 * 根因: SyncManager.login() 成功路径只调 migrateLocalDataIfNeeded(),没调 fullSync()。
 *      对比 restoreAuthState() 在恢复登录时会调 fullSync(saved.tenantId)。
 *
 * 修复后行为: login() 成功 → fullSync(auth.tenantId) → _syncState 更新为
 *            SyncState.Success("同步完成", "知识库 X 条, ...")
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginSyncTriggerTest {

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

    @Before
    fun setup() = runBlocking {
        // 单元测试环境 mock android.util.Log 调用
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

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

        // 默认迁移检查: 默认租户下没有数据 (跳过迁移)
        coEvery { keywordRuleDao.getRulesByTenantSync(any()) } returns emptyList()

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
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `BUG-R8 login成功后必须调用fullSync拉取云端数据`() = runBlocking {
        // 登录成功响应 (LoginResponse 兼容主 API token / accessToken 双格式)
        coEvery { mockAuthApi.login(any()) } returns LoginResponse(
            userId = "u1",
            tenantId = "t1",
            token = "tok",
            refreshToken = "ref",
            expiresIn = 3600
        )

        // 模拟 fullSync 拉到 3 条知识库规则
        coEvery { mockSyncApi.getAllData(any()) } returns ApiResponse(
            code = 0, message = "ok",
            data = SyncAllData(
                keywordRules = listOf(
                    SyncKeywordRule(id = 1L, keyword = "k1"),
                    SyncKeywordRule(id = 2L, keyword = "k2"),
                    SyncKeywordRule(id = 3L, keyword = "k3")
                ),
                aiModelConfigs = emptyList(),
                appConfigs = emptyList(),
                scenarios = emptyList(),
                replyHistory = emptyList(),
                messageBlacklist = emptyList(),
                userStyleProfile = null
            )
        )

        val result = syncManager.login("13800000000", "password")

        assertTrue("登录应该成功", result.isSuccess)
        coVerify(atLeast = 1) { mockSyncApi.getAllData(any()) }
        val stats = syncManager.getLastSyncStats()
        assertNotNull("BUG-R8: 登录后 _lastSyncStats 应包含具体同步内容", stats)
        assertTrue("BUG-R8: 同步内容应包含'知识库'", stats!!.contains("知识库"))
    }

    @Test
    fun `BUG-R8 login成功但fullSync失败时仍返回登录成功`() = runBlocking {
        coEvery { mockAuthApi.login(any()) } returns LoginResponse(
            userId = "u1", tenantId = "t1",
            token = "tok", refreshToken = "ref", expiresIn = 3600
        )
        // fullSync 失败
        coEvery { mockSyncApi.getAllData(any()) } throws RuntimeException("网络超时")

        val result = syncManager.login("13800000000", "password")

        assertTrue("BUG-R8: fullSync 失败不应阻断登录成功", result.isSuccess)
        coVerify(atLeast = 1) { mockSyncApi.getAllData(any()) }
    }

    @Test
    fun `BUG-R8 login失败时不应触发fullSync`() = runBlocking {
        coEvery { mockAuthApi.login(any()) } returns LoginResponse(
            error = "密码错误"
        )

        val result = syncManager.login("13800000000", "wrong")

        assertTrue("登录应失败", result.isFailure)
        coVerify(exactly = 0) { mockSyncApi.getAllData(any()) }
    }
}
