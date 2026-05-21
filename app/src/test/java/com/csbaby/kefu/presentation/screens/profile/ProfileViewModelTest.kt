package com.csbaby.kefu.presentation.screens.profile

import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.model.BackupStatus
import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.data.sync.AuthManager
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.data.sync.SyncState
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.backup.BackupManager
import com.csbaby.kefu.infrastructure.ota.OtaManager
import com.csbaby.kefu.infrastructure.style.StyleLearningEngine
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
    private lateinit var styleLearningEngine: StyleLearningEngine
    private lateinit var otaManager: OtaManager
    private lateinit var syncManager: SyncManager
    private lateinit var authManager: AuthManager
    private lateinit var backupManager: BackupManager

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

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
                overlayPermissionAsked = false
            )
        )
        every { userStyleRepository.getProfile(any()) } returns flowOf(null)
        every { otaManager.updateStatus } returns MutableStateFlow(UpdateStatus.IDLE)
        every { otaManager.availableUpdate } returns MutableStateFlow(null)
        every { otaManager.errorMessage } returns MutableStateFlow(null)
        every { syncManager.syncState } returns MutableStateFlow(SyncState.Idle)
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
            backupManager = backupManager
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
    fun `updateFormality calls style learning engine`() = runTest {
        coEvery { styleLearningEngine.updateStyleParameters(any(), formality = any()) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateFormality(0.8f)
        advanceUntilIdle()

        coVerify { styleLearningEngine.updateStyleParameters(userId = "test_user", formality = 0.8f) }
        assertEquals(0.8f, viewModel.uiState.value.formalityLevel)
    }

    @Test
    fun `updateEnthusiasm calls style learning engine`() = runTest {
        coEvery { styleLearningEngine.updateStyleParameters(any(), enthusiasm = any()) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateEnthusiasm(0.6f)
        advanceUntilIdle()

        coVerify { styleLearningEngine.updateStyleParameters(userId = "test_user", enthusiasm = 0.6f) }
        assertEquals(0.6f, viewModel.uiState.value.enthusiasmLevel)
    }

    @Test
    fun `updateProfessionalism calls style learning engine`() = runTest {
        coEvery { styleLearningEngine.updateStyleParameters(any(), professionalism = any()) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateProfessionalism(0.9f)
        advanceUntilIdle()

        coVerify { styleLearningEngine.updateStyleParameters(userId = "test_user", professionalism = 0.9f) }
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
    fun `clearBackupStatus calls backupManager`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearBackupStatus()
        advanceUntilIdle()

        verify { backupManager.clearStatus() }
    }

    @Test
    fun `uploadBackup calls backupManager`() = runTest {
        coEvery { backupManager.uploadBackup() } returns mockk()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.uploadBackup()
        advanceUntilIdle()

        coVerify { backupManager.uploadBackup() }
    }

    @Test
    fun `fetchBackupList calls backupManager`() = runTest {
        coEvery { backupManager.fetchBackupList() } returns mockk()

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.fetchBackupList()
        advanceUntilIdle()

        coVerify { backupManager.fetchBackupList() }
    }

    @Test
    fun `logout calls syncManager`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        verify { syncManager.logout() }
    }

    @Test
    fun `syncState reflects syncManager state`() = runTest {
        every { syncManager.syncState } returns MutableStateFlow(SyncState.Syncing("同步中"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.syncState is SyncState.Syncing)
    }

    @Test
    fun `isLoggedIn reflects auth state`() = runTest {
        coEvery { authManager.authStateFlow } returns MutableStateFlow(
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
    fun `backupStatus reflects backupManager state`() = runTest {
        every { backupManager.backupStatus } returns MutableStateFlow(BackupStatus.UPLOADING)
        every { backupManager.backupMessage } returns MutableStateFlow("正在上传...")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(BackupStatus.UPLOADING, viewModel.uiState.value.backupStatus)
        assertEquals("正在上传...", viewModel.uiState.value.backupMessage)
    }
}
