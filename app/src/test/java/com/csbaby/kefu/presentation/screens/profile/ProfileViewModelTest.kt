package com.csbaby.kefu.presentation.screens.profile

import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.model.BackupRecord
import com.csbaby.kefu.data.model.BackupStatus
import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.domain.model.SyncState
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.domain.usecase.auth.GetCurrentTenantIdUseCase
import com.csbaby.kefu.domain.usecase.auth.ObserveAuthStateUseCase
import com.csbaby.kefu.domain.usecase.backup.ClearBackupStatusUseCase
import com.csbaby.kefu.domain.usecase.backup.DeleteBackupUseCase
import com.csbaby.kefu.domain.usecase.backup.FetchBackupListUseCase
import com.csbaby.kefu.domain.usecase.backup.ObserveBackupMessageUseCase
import com.csbaby.kefu.domain.usecase.backup.ObserveBackupRecordsUseCase
import com.csbaby.kefu.domain.usecase.backup.ObserveBackupStatusUseCase
import com.csbaby.kefu.domain.usecase.backup.RestoreBackupUseCase
import com.csbaby.kefu.domain.usecase.backup.UploadBackupUseCase
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

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
    private lateinit var uploadBackupUseCase: UploadBackupUseCase
    private lateinit var fetchBackupListUseCase: FetchBackupListUseCase
    private lateinit var restoreBackupUseCase: RestoreBackupUseCase
    private lateinit var deleteBackupUseCase: DeleteBackupUseCase
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

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
        uploadBackupUseCase = mockk(relaxed = true)
        fetchBackupListUseCase = mockk(relaxed = true)
        restoreBackupUseCase = mockk(relaxed = true)
        deleteBackupUseCase = mockk(relaxed = true)
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
                overlayPermissionAsked = false
            )
        )
        every { userStyleRepository.getProfile(any()) } returns flowOf(null)
        every { observeOtaStatusUseCase() } returns MutableStateFlow(UpdateStatus.IDLE)
        every { observeAvailableUpdateUseCase() } returns MutableStateFlow(null)
        every { observeOtaErrorUseCase() } returns MutableStateFlow(null)
        every { observeSyncStateUseCase() } returns MutableStateFlow(SyncState.Idle)
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
            uploadBackupUseCase = uploadBackupUseCase,
            fetchBackupListUseCase = fetchBackupListUseCase,
            restoreBackupUseCase = restoreBackupUseCase,
            deleteBackupUseCase = deleteBackupUseCase,
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

    @Test
    fun `initial state loads preferences`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.styleLearningEnabled)
        assertFalse(state.autoSendEnabled)
    }

    @Test
    fun `updateFormality calls updateStyleParameters use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateFormality(0.8f)
        advanceUntilIdle()

        coVerify { updateStyleParametersUseCase(userId = "test_user", formality = 0.8f) }
        assertEquals(0.8f, viewModel.uiState.value.formalityLevel)
    }

    @Test
    fun `updateEnthusiasm calls use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateEnthusiasm(0.6f)
        advanceUntilIdle()

        coVerify { updateStyleParametersUseCase(userId = "test_user", enthusiasm = 0.6f) }
        assertEquals(0.6f, viewModel.uiState.value.enthusiasmLevel)
    }

    @Test
    fun `updateProfessionalism calls use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateProfessionalism(0.9f)
        advanceUntilIdle()

        coVerify { updateStyleParametersUseCase(userId = "test_user", professionalism = 0.9f) }
        assertEquals(0.9f, viewModel.uiState.value.professionalismLevel)
    }

    @Test
    fun `toggleStyleLearning calls preferencesManager`() = runTest {
        coEvery { preferencesManager.updateStyleLearningEnabled(any()) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleStyleLearning(false)
        advanceUntilIdle()

        coVerify { preferencesManager.updateStyleLearningEnabled(false) }
        assertFalse(viewModel.uiState.value.styleLearningEnabled)
    }

    @Test
    fun `toggleAutoSend calls preferencesManager`() = runTest {
        coEvery { preferencesManager.updateAutoSendEnabled(any()) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleAutoSend(true)
        advanceUntilIdle()

        coVerify { preferencesManager.updateAutoSendEnabled(true) }
        assertTrue(viewModel.uiState.value.autoSendEnabled)
    }

    @Test
    fun `user style profile updates UI state`() = runTest {
        val profile = UserStyleProfile(
            userId = "test_user",
            formalityLevel = 0.7f,
            enthusiasmLevel = 0.3f,
            professionalismLevel = 0.9f,
            wordCountPreference = 80,
            commonPhrases = listOf("您好", "感谢"),
            learningSamples = 100,
            accuracyScore = 0.85f
        )
        coEvery { userStyleRepository.getProfile("test_user") } returns flowOf(profile)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0.7f, state.formalityLevel)
        assertEquals(0.3f, state.enthusiasmLevel)
        assertEquals(0.9f, state.professionalismLevel)
        assertEquals(100, state.learningSamples)
        assertEquals(0.85f, state.accuracyScore)
        assertEquals(listOf("您好", "感谢"), state.commonPhrases)
    }

    @Test
    fun `clearBackupStatus calls use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearBackupStatus()
        advanceUntilIdle()

        verify { clearBackupStatusUseCase() }
    }

    @Test
    fun `uploadBackup calls use case`() = runTest {
        coEvery { uploadBackupUseCase() } returns Result.success(BackupRecord(id = 1))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uploadBackup()
        advanceUntilIdle()

        coVerify { uploadBackupUseCase() }
    }

    @Test
    fun `fetchBackupList calls use case`() = runTest {
        coEvery { fetchBackupListUseCase() } returns Result.success(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fetchBackupList()
        advanceUntilIdle()

        coVerify { fetchBackupListUseCase() }
    }

    @Test
    fun `logout calls use case`() = runTest {
        every { logoutUseCase() } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        verify { logoutUseCase() }
    }

    @Test
    fun `syncState reflects use case flow`() = runTest {
        every { observeSyncStateUseCase() } returns MutableStateFlow(SyncState.Syncing("同步中"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.syncState is SyncState.Syncing)
    }

    @Test
    fun `isLoggedIn reflects auth state`() = runTest {
        every { observeAuthStateUseCase() } returns MutableStateFlow(
            SyncAuthState(
                userId = "test_user",
                tenantId = "test_tenant",
                accessToken = "test_token",
                refreshToken = "",
                expiresAt = System.currentTimeMillis() + 86400000
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertEquals("test_tenant", viewModel.uiState.value.currentTenantId)
    }

    @Test
    fun `backupStatus reflects use case flow`() = runTest {
        every { observeBackupStatusUseCase() } returns MutableStateFlow(BackupStatus.UPLOADING)
        every { observeBackupMessageUseCase() } returns MutableStateFlow("正在上传...")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(BackupStatus.UPLOADING, viewModel.uiState.value.backupStatus)
        assertEquals("正在上传...", viewModel.uiState.value.backupMessage)
    }

    @Test
    fun `syncNow calls use case`() = runTest {
        coEvery { syncNowUseCase() } returns Result.success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow()
        advanceUntilIdle()

        coVerify { syncNowUseCase() }
    }

    @Test
    fun `syncNow handles failure gracefully`() = runTest {
        coEvery { syncNowUseCase() } returns Result.failure(IllegalStateException("未登录，无法同步"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow()
        advanceUntilIdle()

        coVerify { syncNowUseCase() }
        assertTrue(viewModel.uiState.value.syncState is SyncState.Error)
    }

    @Test
    fun `syncNow handles network error gracefully`() = runTest {
        coEvery { syncNowUseCase() } returns Result.failure(java.net.UnknownHostException("网络不可达"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.syncNow()
        advanceUntilIdle()

        coVerify { syncNowUseCase() }
        assertTrue(viewModel.uiState.value.syncState is SyncState.Error)
    }

    @Test
    fun `checkForUpdate calls use case`() = runTest {
        coEvery { checkForUpdateUseCase() } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.checkForUpdate()
        advanceUntilIdle()

        coVerify { checkForUpdateUseCase() }
    }

    @Test
    fun `cancelDownload calls use case`() = runTest {
        every { cancelDownloadUseCase() } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.cancelDownload()

        verify { cancelDownloadUseCase() }
    }

    @Test
    fun `ota error message reflects use case flow`() = runTest {
        every { observeOtaErrorUseCase() } returns MutableStateFlow("检查更新失败: timeout")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("检查更新失败: timeout", viewModel.uiState.value.errorMessage)
    }
}