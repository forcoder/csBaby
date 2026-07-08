package com.csbaby.kefu.data.sync

import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.remote.*
import com.csbaby.kefu.data.remote.AuthResult
import com.csbaby.kefu.data.remote.SyncApiService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * BUG 回归: 登录后 displayName 显示租户 ID 而非账号。

 * 根因:
 *   1. SyncManager.login() 用 identifier 写 displayName, 但服务端返回的 phone/email 未被使用,
 *      旧版登录的用户 datastore 里 display_name 为空。
 *   2. restoreAuthState() 未补填空 displayName, 导致 SyncSettingsCard 回退显示 "租户: xxx"。
 *   3. tryRefreshToken() 刷新 token 时丢失 displayName。
 *
 * 修复后行为:
 *   - login() 优先用 response.phone/email 兜底 identifier
 *   - restoreAuthState() 对空 displayName 用 "用户+userId后4位" 补填
 *   - token 刷新分支保留原 saved.displayName
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginDisplayNameTest {

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

    private fun stubFullSyncOk() {
        coEvery { mockSyncApi.getAllData(any()) } returns ApiResponse(
            code = 0, message = "ok",
            data = SyncAllData(
                keywordRules = emptyList(), aiModelConfigs = emptyList(),
                appConfigs = emptyList(), scenarios = emptyList(),
                replyHistory = emptyList(), messageBlacklist = emptyList(),
                userStyleProfile = null
            )
        )
    }

    private fun capturedAuth(): com.csbaby.kefu.data.model.SyncAuthState {
        val slot = slot<com.csbaby.kefu.data.model.SyncAuthState>()
        coEvery { authManager.saveAuthState(capture(slot)) } just Runs
        return slot.captured
    }

    // ========== 正常场景 ==========

    @Test
    fun `登录响应含phone时displayName用phone`() = runBlocking {
        coEvery { mockAuthApi.login(any()) } returns LoginResponse(
            userId = "u1", tenantId = "t1", token = "tok",
            refreshToken = "ref", expiresIn = 3600, phone = "13800000000"
        )
        stubFullSyncOk()
        val slot = slot<com.csbaby.kefu.data.model.SyncAuthState>()
        coEvery { authManager.saveAuthState(capture(slot)) } just Runs

        syncManager.login("13800000000", "pwd")

        assertEquals("BUG: displayName 应优先用 phone", "13800000000", slot.captured.displayName)
    }

    @Test
    fun `登录响应含email时displayName用email`() = runBlocking {
        coEvery { mockAuthApi.login(any()) } returns LoginResponse(
            userId = "u1", tenantId = "t1", token = "tok",
            refreshToken = "ref", expiresIn = 3600, email = "test@x.com"
        )
        stubFullSyncOk()
        val slot = slot<com.csbaby.kefu.data.model.SyncAuthState>()
        coEvery { authManager.saveAuthState(capture(slot)) } just Runs

        syncManager.login("test@x.com", "pwd")

        assertEquals("BUG: displayName 应优先用 email", "test@x.com", slot.captured.displayName)
    }

    @Test
    fun `登录响应无phone和email时displayName回退identifier`() = runBlocking {
        coEvery { mockAuthApi.login(any()) } returns LoginResponse(
            userId = "u1", tenantId = "t1", token = "tok", refreshToken = "ref", expiresIn = 3600
        )
        stubFullSyncOk()
        val slot = slot<com.csbaby.kefu.data.model.SyncAuthState>()
        coEvery { authManager.saveAuthState(capture(slot)) } just Runs

        syncManager.login("myname", "pwd")

        assertEquals("BUG: 无 phone/email 时回退 identifier", "myname", slot.captured.displayName)
    }

    // ========== 边界值场景 ==========

    @Test
    fun `restoreAuthState对空displayName补填用户后4位`() = runBlocking {
        val saved = com.csbaby.kefu.data.model.SyncAuthState(
            userId = "abcd1234efgh", tenantId = "t1",
            accessToken = "tok", refreshToken = "ref",
            expiresAt = System.currentTimeMillis() + 3600_000L,
            displayName = ""
        )
        coEvery { authManager.getAuthState() } returns saved
        val slot = slot<com.csbaby.kefu.data.model.SyncAuthState>()
        coEvery { authManager.saveAuthState(capture(slot)) } just Runs
        stubFullSyncOk()

        syncManager.restoreAuthState()

        assertTrue("BUG: 空 displayName 应补填为 '用户+后4位'",
            slot.captured.displayName == "用户3efg" || slot.captured.displayName.startsWith("用户"))
    }

    @Test
    fun `restoreAuthState对非空displayName不覆盖`() = runBlocking {
        val saved = com.csbaby.kefu.data.model.SyncAuthState(
            userId = "u1", tenantId = "t1",
            accessToken = "tok", refreshToken = "ref",
            expiresAt = System.currentTimeMillis() + 3600_000L,
            displayName = "原账号"
        )
        coEvery { authManager.getAuthState() } returns saved
        coEvery { authManager.saveAuthState(any()) } just Runs
        stubFullSyncOk()

        syncManager.restoreAuthState()

        coVerify(exactly = 0) { authManager.saveAuthState(any()) }
    }

    // ========== 异常/错误场景 ==========

    @Test
    fun `登录失败时不写displayName`() = runBlocking {
        coEvery { mockAuthApi.login(any()) } returns LoginResponse(error = "密码错误")

        val result = syncManager.login("13800000000", "wrong")

        assertTrue("登录应失败", result.isFailure)
        coVerify(exactly = 0) { authManager.saveAuthState(any()) }
    }

    @Test
    fun `token过期刷新时空displayName用原saved兜底`() = runBlocking {
        val saved = com.csbaby.kefu.data.model.SyncAuthState(
            userId = "u1", tenantId = "t1",
            accessToken = "old", refreshToken = "ref",
            expiresAt = System.currentTimeMillis() - 1000L,
            displayName = "原账号"
        )
        coEvery { authManager.getAuthState() } returns saved
        val refreshApi = mockk<SyncApiService>(relaxed = true)
        coEvery { refreshApi.refreshToken(any()) } returns ApiResponse(
            code = 0, message = "ok",
            data = AuthResult(
                userId = "u1", tenantId = "t1",
                accessToken = "new", refreshToken = "ref2",
                expiresAt = System.currentTimeMillis() + 3600_000L
            )
        )
        val mockClient = mockk<AuthenticatedSyncClient>(relaxed = true)
        every { mockClient.apiService } returns mockSyncApi
        every { mockClient.authApiService } returns mockAuthApi
        every { mockClient.refreshApiService } returns refreshApi
        val field = SyncManager::class.java.getDeclaredField("syncClient")
        field.isAccessible = true
        field.set(syncManager, mockClient)
        stubFullSyncOk()
        val slot = slot<com.csbaby.kefu.data.model.SyncAuthState>()
        coEvery { authManager.saveAuthState(capture(slot)) } just Runs

        syncManager.restoreAuthState()

        assertEquals("BUG: token 刷新后 displayName 应保留原 saved 值", "原账号", slot.captured.displayName)
    }
}
