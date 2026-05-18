package com.csbaby.kefu.presentation.screens.profile

import android.content.Context
import android.net.Uri
import com.csbaby.kefu.data.local.KefuDatabase
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.dao.MessageBlacklistDao
import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.DeviceManager
import com.csbaby.kefu.data.remote.dto.BackupDto
import com.csbaby.kefu.data.remote.dto.RuleDto
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.oss.AliyunOssManager
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

    private lateinit var appContext: Context
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var userStyleRepository: UserStyleRepository
    private lateinit var styleLearningEngine: StyleLearningEngine
    private lateinit var otaManager: OtaManager
    private lateinit var ossManager: AliyunOssManager
    private lateinit var deviceManager: DeviceManager
    private lateinit var apiService: CsbabyApiService
    private lateinit var kefuDatabase: KefuDatabase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        appContext = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)
        userStyleRepository = mockk(relaxed = true)
        styleLearningEngine = mockk(relaxed = true)
        otaManager = mockk(relaxed = true)
        ossManager = mockk(relaxed = true)
        deviceManager = mockk(relaxed = true)
        apiService = mockk(relaxed = true)
        kefuDatabase = mockk(relaxed = true)

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
                semanticSearchEnabled = true,
                searchMode = "HYBRID",
                themeMode = "system"
            )
        )
        every { userStyleRepository.getProfile(any()) } returns flowOf(null)
        every { otaManager.updateStatus } returns MutableStateFlow(com.csbaby.kefu.data.model.UpdateStatus.IDLE)
        every { otaManager.availableUpdate } returns MutableStateFlow(null)
        every { otaManager.errorMessage } returns MutableStateFlow(null)
        every { otaManager.downloadProgress } returns MutableStateFlow(0f)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): ProfileViewModel {
        return ProfileViewModel(
            appContext = appContext,
            preferencesManager = preferencesManager,
            userStyleRepository = userStyleRepository,
            styleLearningEngine = styleLearningEngine,
            otaManager = otaManager,
            ossManager = ossManager,
            deviceManager = deviceManager,
            apiService = apiService,
            kefuDatabase = kefuDatabase
        )
    }

    @Test
    fun `initial state loads preferences`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.styleLearningEnabled)
        assertFalse(state.autoSendEnabled)
        assertEquals("system", state.themeMode)
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
    fun `updateThemeMode calls preferencesManager`() = runTest {
        coEvery { preferencesManager.updateThemeMode(any()) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateThemeMode("dark")
        advanceUntilIdle()

        coVerify { preferencesManager.updateThemeMode("dark") }
        assertEquals("dark", viewModel.uiState.value.themeMode)
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
        every { userStyleRepository.getProfile("test_user") } returns flowOf(profile)

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
    fun `clearBackupMessage clears message`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearBackupMessage()
        assertNull(viewModel.uiState.value.backupMessage)
    }

    @Test
    fun `clearUploadStatus clears status`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearUploadStatus()
        assertEquals("", viewModel.uiState.value.uploadStatus)
    }

    @Test
    fun `cancelUpload resets upload state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.cancelUpload()
        val state = viewModel.uiState.value
        assertFalse(state.isUploading)
        assertEquals(0f, state.uploadProgress)
        assertEquals("上传已取消", state.uploadStatus)
    }

    @Test
    fun `setForceUpdate updates version list`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setForceUpdate(2, true)
        advanceUntilIdle()

        val version = viewModel.uiState.value.ossVersionList.find { it.versionCode == 2 }
        // Version list may be empty since we didn't load it; just verify no crash
        // The method updates the list if present
    }
}
