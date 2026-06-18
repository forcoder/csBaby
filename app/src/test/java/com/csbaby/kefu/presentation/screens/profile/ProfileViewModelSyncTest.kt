package com.csbaby.kefu.presentation.screens.profile

import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.model.BackupStatus
import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.data.sync.AuthManager
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.data.sync.SyncState
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.backup.BackupManager
import com.csbaby.kefu.infrastructure.ota.OtaManager
import com.csbaby.kefu.infrastructure.style.StyleLearningEngine
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * BUG-R8 续修复回归测试:
 * - login() onSuccess 不应再触发二次 fullSync (SyncManager.login() 内部已调 fullSync)
 * - observeSyncState 收到空 stats 的 Success 时不应覆盖已有 syncStats
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelSyncTest {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var userStyleRepository: UserStyleRepository
    private lateinit var styleLearningEngine: StyleLearningEngine
    private lateinit var otaManager: OtaManager
    private lateinit var syncManager: SyncManager
    private lateinit var authManager: AuthManager
    private lateinit var backupManager: BackupManager

    private val testDispatcher = StandardTestDispatcher()

    private val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Idle)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // 单元测试环境 mock android.util.Log 调用
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        preferencesManager = mockk(relaxed = true)
        userStyleRepository = mockk(relaxed = true)
        styleLearningEngine = mockk(relaxed = true)
        otaManager = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        authManager = mockk(relaxed = true)
        backupManager = mockk(relaxed = true)

        // Default stubs
        every { preferencesManager.userPreferencesFlow } returns flowOf(
            PreferencesManager.UserPreferences(
                monitoringEnabled = true,
                floatingWindowEnabled = true,
                floatingIconEnabled = false,
                selectedApps = emptySet(),
                defaultModelId = -1L,
                styleLearningEnabled = true,
                autoSendEnabled = false,
                currentUserId = "test_user",
                isFirstLaunch = false,
                notificationPermissionAsked = false,
                overlayPermissionAsked = false,
                themeMode = "system"
            )
        )
        every { userStyleRepository.getProfile(any()) } returns flowOf(null)
        every { otaManager.updateStatus } returns MutableStateFlow(UpdateStatus.IDLE)
        every { otaManager.availableUpdate } returns MutableStateFlow(null)
        every { otaManager.errorMessage } returns MutableStateFlow(null)
        every { syncManager.syncState } returns syncStateFlow
        every { syncManager.lastSyncTime } returns flowOf(0L)
        every { syncManager.isLoggedIn() } returns false
        coEvery { authManager.authStateFlow } returns MutableStateFlow(null)
        coEvery { authManager.currentTenantId() } returns null
        every { backupManager.backupStatus } returns MutableStateFlow(BackupStatus.IDLE)
        every { backupManager.backupMessage } returns MutableStateFlow("")
        every { backupManager.backupRecords } returns MutableStateFlow(emptyList())
        every { backupManager.clearStatus() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): ProfileViewModel {
        return ProfileViewModel(
            preferencesManager = preferencesManager,
            userStyleRepository = userStyleRepository,
            styleLearningEngine = styleLearningEngine,
            otaManager = otaManager,
            syncManager = syncManager,
            authManager = authManager,
            backupManager = backupManager,
            keywordRuleDao = mockk(relaxed = true),
            messageBlacklistDao = mockk(relaxed = true),
            aiModelConfigDao = mockk(relaxed = true),
            appConfigDao = mockk(relaxed = true),
            scenarioDao = mockk(relaxed = true)
        )
    }

    /**
     * 1. login() 成功后,onSuccess 中不应该再调 syncManager.fullSync()
     *    因为 SyncManager.login() 内部已经触发 fullSync。
     *    二次 fullSync 会让 server 返回空 stats 覆盖第一次的非空 stats。
     */
    @Test
    fun `BUG-R8 续 login onSuccess 不应触发二次 fullSync`() = runTest {
        val auth = com.csbaby.kefu.data.model.SyncAuthState(
            userId = "u1",
            tenantId = "t1",
            accessToken = "tok",
            refreshToken = "ref",
            expiresAt = System.currentTimeMillis() + 86400000
        )
        coEvery { syncManager.login(any(), any()) } returns Result.success(auth)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.login("user@example.com", "pass")
        advanceUntilIdle()

        coVerify(exactly = 1) { syncManager.login("user@example.com", "pass") }
        // 关键断言: 不应该再额外调一次 fullSync
        coVerify(exactly = 0) { syncManager.fullSync(any()) }
    }

    /**
     * 2. observeSyncState 收到 SyncState.Success(stats="知识库 5 条") 时,
     *    uiState.syncStats 应包含该 stats
     */
    @Test
    fun `observeSyncState 收到非空 stats 时更新 syncStats`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val stats = "知识库 5 条，黑名单 2 条"
        syncStateFlow.value = SyncState.Success("同步完成", stats)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(stats, state.syncStats)
    }

    /**
     * 3. observeSyncState 收到空 stats 的 Success 时, 不应覆盖已有的 syncStats。
     *    这是 BUG-R8 的核心场景: 第二次 fullSync 返回空 stats 时,
     *    如果覆盖了第一次的非空 stats, UI 会看到 "同步完成: " 而丢失具体内容。
     */
    @Test
    fun `observeSyncState 空 stats 不应覆盖已有 syncStats`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 第一次: 非空 stats
        val firstStats = "知识库 5 条，模型 2 条"
        syncStateFlow.value = SyncState.Success("全量同步完成", firstStats)
        advanceUntilIdle()
        assertEquals(firstStats, viewModel.uiState.value.syncStats)

        // 第二次: 空 stats (模拟 BUG-R8: 二次 fullSync 后 server 返回空 data)
        syncStateFlow.value = SyncState.Success("同步完成", "")
        advanceUntilIdle()

        // 关键断言: 空 stats 不应覆盖已有 syncStats
        assertEquals(
            "BUG-R8: 空 stats 应保留上一次非空 stats",
            firstStats,
            viewModel.uiState.value.syncStats
        )
    }

    /**
     * 边界: 首次 observeSyncState 收到空 stats 时, syncStats 应为空 (无上一次值)
     */
    @Test
    fun `observeSyncState 首次空 stats 时 syncStats 为空`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        syncStateFlow.value = SyncState.Success("同步完成", "")
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.syncStats)
    }

    /**
     * 边界: Non-Success 状态不影响 syncStats
     */
    @Test
    fun `observeSyncState Syncing 状态不影响 syncStats`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 先设置非空 stats
        syncStateFlow.value = SyncState.Success("ok", "知识库 3 条")
        advanceUntilIdle()

        // Syncing 状态应保留 syncStats
        syncStateFlow.value = SyncState.Syncing("正在同步...")
        advanceUntilIdle()

        assertEquals("知识库 3 条", viewModel.uiState.value.syncStats)
    }
}