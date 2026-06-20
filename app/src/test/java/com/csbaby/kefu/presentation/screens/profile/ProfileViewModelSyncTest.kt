package com.csbaby.kefu.presentation.screens.profile

import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.model.BackupStatus
import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.domain.model.SyncState
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.domain.usecase.auth.GetCurrentTenantIdUseCase
import com.csbaby.kefu.domain.usecase.auth.ObserveAuthStateUseCase
import com.csbaby.kefu.domain.usecase.backup.ClearBackupStatusUseCase
import com.csbaby.kefu.domain.usecase.backup.ObserveBackupMessageUseCase
import com.csbaby.kefu.domain.usecase.backup.ObserveBackupRecordsUseCase
import com.csbaby.kefu.domain.usecase.backup.ObserveBackupStatusUseCase
import com.csbaby.kefu.domain.usecase.ota.CancelDownloadUseCase
import com.csbaby.kefu.domain.usecase.ota.CheckForUpdateUseCase
import com.csbaby.kefu.domain.usecase.ota.ObserveAvailableUpdateUseCase
import com.csbaby.kefu.domain.usecase.ota.ObserveOtaErrorUseCase
import com.csbaby.kefu.domain.usecase.ota.ObserveOtaStatusUseCase
import com.csbaby.kefu.domain.usecase.ota.StartDownloadUpdateUseCase
import com.csbaby.kefu.domain.usecase.stats.GetDataStatsUseCase
import com.csbaby.kefu.domain.usecase.style.UpdateStyleParametersUseCase
import com.csbaby.kefu.domain.usecase.sync.IsLoggedInUseCase
import com.csbaby.kefu.domain.usecase.sync.LoginUseCase
import com.csbaby.kefu.domain.usecase.sync.LogoutUseCase
import com.csbaby.kefu.domain.usecase.sync.ObserveLastSyncTimeUseCase
import com.csbaby.kefu.domain.usecase.sync.ObserveSyncQueueUseCase
import com.csbaby.kefu.domain.usecase.sync.ObserveSyncStateUseCase
import com.csbaby.kefu.domain.usecase.sync.RegisterUseCase
import com.csbaby.kefu.domain.usecase.sync.SyncNowUseCase
import com.csbaby.kefu.domain.usecase.sync.TriggerSyncUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var loginUseCase: LoginUseCase
    private lateinit var registerUseCase: RegisterUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var syncNowUseCase: SyncNowUseCase
    private lateinit var isLoggedInUseCase: IsLoggedInUseCase
    private lateinit var observeSyncStateUseCase: ObserveSyncStateUseCase
    private lateinit var observeSyncQueueUseCase: ObserveSyncQueueUseCase
    private lateinit var observeLastSyncTimeUseCase: ObserveLastSyncTimeUseCase
    private lateinit var triggerSyncUseCase: TriggerSyncUseCase
    private lateinit var observeAuthStateUseCase: ObserveAuthStateUseCase
    private lateinit var getCurrentTenantIdUseCase: GetCurrentTenantIdUseCase
    private lateinit var clearBackupStatusUseCase: ClearBackupStatusUseCase
    private lateinit var observeBackupStatusUseCase: ObserveBackupStatusUseCase
    private lateinit var observeBackupMessageUseCase: ObserveBackupMessageUseCase
    private lateinit var observeBackupRecordsUseCase: ObserveBackupRecordsUseCase
    private lateinit var checkForUpdateUseCase: CheckForUpdateUseCase
    private lateinit var startDownloadUpdateUseCase: StartDownloadUpdateUseCase
    private lateinit var cancelDownloadUseCase: CancelDownloadUseCase
    private lateinit var observeOtaStatusUseCase: ObserveOtaStatusUseCase
    private lateinit var observeAvailableUpdateUseCase: ObserveAvailableUpdateUseCase
    private lateinit var observeOtaErrorUseCase: ObserveOtaErrorUseCase
    private lateinit var updateStyleParametersUseCase: UpdateStyleParametersUseCase
    private lateinit var getDataStatsUseCase: GetDataStatsUseCase

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
        loginUseCase = mockk(relaxed = true)
        registerUseCase = mockk(relaxed = true)
        logoutUseCase = mockk(relaxed = true)
        syncNowUseCase = mockk(relaxed = true)
        isLoggedInUseCase = mockk(relaxed = true)
        observeSyncStateUseCase = mockk(relaxed = true)
        observeSyncQueueUseCase = mockk(relaxed = true)
        observeLastSyncTimeUseCase = mockk(relaxed = true)
        triggerSyncUseCase = mockk(relaxed = true)
        observeAuthStateUseCase = mockk(relaxed = true)
        getCurrentTenantIdUseCase = mockk(relaxed = true)
        clearBackupStatusUseCase = mockk(relaxed = true)
        observeBackupStatusUseCase = mockk(relaxed = true)
        observeBackupMessageUseCase = mockk(relaxed = true)
        observeBackupRecordsUseCase = mockk(relaxed = true)
        checkForUpdateUseCase = mockk(relaxed = true)
        startDownloadUpdateUseCase = mockk(relaxed = true)
        cancelDownloadUseCase = mockk(relaxed = true)
        observeOtaStatusUseCase = mockk(relaxed = true)
        observeAvailableUpdateUseCase = mockk(relaxed = true)
        observeOtaErrorUseCase = mockk(relaxed = true)
        updateStyleParametersUseCase = mockk(relaxed = true)
        getDataStatsUseCase = mockk(relaxed = true)

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
        every { observeOtaStatusUseCase() } returns MutableStateFlow(UpdateStatus.IDLE)
        every { observeAvailableUpdateUseCase() } returns MutableStateFlow(null)
        every { observeOtaErrorUseCase() } returns MutableStateFlow(null)
        every { observeSyncStateUseCase() } returns syncStateFlow
        every { observeLastSyncTimeUseCase() } returns flowOf(0L)
        every { observeSyncQueueUseCase() } returns flowOf(0)
        every { isLoggedInUseCase() } returns false
        every { observeAuthStateUseCase() } returns MutableStateFlow(null)
        coEvery { getCurrentTenantIdUseCase() } returns null
        coEvery { getDataStatsUseCase(any()) } returns GetDataStatsUseCase.Stats()
        every { observeBackupStatusUseCase() } returns MutableStateFlow(BackupStatus.IDLE)
        every { observeBackupMessageUseCase() } returns MutableStateFlow("")
        every { observeBackupRecordsUseCase() } returns MutableStateFlow(emptyList())
        every { clearBackupStatusUseCase() } returns Unit
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
            loginUseCase = loginUseCase,
            registerUseCase = registerUseCase,
            logoutUseCase = logoutUseCase,
            syncNowUseCase = syncNowUseCase,
            isLoggedInUseCase = isLoggedInUseCase,
            observeSyncStateUseCase = observeSyncStateUseCase,
            observeSyncQueueUseCase = observeSyncQueueUseCase,
            observeLastSyncTimeUseCase = observeLastSyncTimeUseCase,
            triggerSyncUseCase = triggerSyncUseCase,
            observeAuthStateUseCase = observeAuthStateUseCase,
            getCurrentTenantIdUseCase = getCurrentTenantIdUseCase,
            uploadBackupUseCase = mockk(relaxed = true),
            fetchBackupListUseCase = mockk(relaxed = true),
            restoreBackupUseCase = mockk(relaxed = true),
            deleteBackupUseCase = mockk(relaxed = true),
            clearBackupStatusUseCase = clearBackupStatusUseCase,
            observeBackupStatusUseCase = observeBackupStatusUseCase,
            observeBackupMessageUseCase = observeBackupMessageUseCase,
            observeBackupRecordsUseCase = observeBackupRecordsUseCase,
            checkForUpdateUseCase = checkForUpdateUseCase,
            startDownloadUpdateUseCase = startDownloadUpdateUseCase,
            cancelDownloadUseCase = cancelDownloadUseCase,
            observeOtaStatusUseCase = observeOtaStatusUseCase,
            observeAvailableUpdateUseCase = observeAvailableUpdateUseCase,
            observeOtaErrorUseCase = observeOtaErrorUseCase,
            updateStyleParametersUseCase = updateStyleParametersUseCase,
            getDataStatsUseCase = getDataStatsUseCase
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
        coEvery { loginUseCase(any(), any()) } returns Result.success(auth)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.login("user@example.com", "pass")
        advanceUntilIdle()

        coVerify(exactly = 1) { loginUseCase("user@example.com", "pass") }
        // 关键断言: 不应该再额外调一次 SyncManager.fullSync（通过 SyncNowUseCase 也未调用）
        coVerify(exactly = 0) { syncNowUseCase() }
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