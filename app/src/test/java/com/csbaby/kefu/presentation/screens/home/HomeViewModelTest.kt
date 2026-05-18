package com.csbaby.kefu.presentation.screens.home

import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.repository.KeywordRuleRepository
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var replyHistoryRepository: ReplyHistoryRepository
    private lateinit var keywordRuleRepository: KeywordRuleRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        preferencesManager = mockk(relaxed = true)
        replyHistoryRepository = mockk(relaxed = true)
        keywordRuleRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createUserPreferences(
        monitoringEnabled: Boolean = true,
        floatingIconEnabled: Boolean = false,
        selectedApps: Set<String> = emptySet()
    ) = PreferencesManager.UserPreferences(
        monitoringEnabled = monitoringEnabled,
        floatingWindowEnabled = true,
        floatingIconEnabled = floatingIconEnabled,
        selectedApps = selectedApps,
        defaultModelId = -1L,
        styleLearningEnabled = true,
        autoSendEnabled = false,
        currentUserId = "default_user",
        isFirstLaunch = false,
        notificationPermissionAsked = false,
        overlayPermissionAsked = false,
        semanticSearchEnabled = true,
        searchMode = "HYBRID",
        themeMode = "system"
    )

    private fun createTestReply(id: Long, sourceApp: String, sendTime: Long = System.currentTimeMillis()) = ReplyHistory(
        id = id,
        sourceApp = sourceApp,
        originalMessage = "消息$id",
        generatedReply = "回复$id",
        finalReply = "回复$id",
        ruleMatchedId = null,
        modelUsedId = null,
        sendTime = sendTime
    )

    @Test
    fun `initial state loads preferences and data`() = runTest {
        val prefs = createUserPreferences(monitoringEnabled = true, floatingIconEnabled = true)
        every { preferencesManager.userPreferencesFlow } returns flowOf(prefs)
        coEvery { replyHistoryRepository.getTotalCount() } returns 42
        every { replyHistoryRepository.getRecentReplies(10) } returns flowOf(emptyList())
        every { keywordRuleRepository.getRuleCountFlow() } returns flowOf(5)

        val viewModel = HomeViewModel(
            preferencesManager = preferencesManager,
            replyHistoryRepository = replyHistoryRepository,
            keywordRuleRepository = keywordRuleRepository
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.isMonitoringEnabled)
        assertTrue(state.isFloatingIconEnabled)
        assertEquals(42, state.totalReplies)
        assertEquals(5, state.knowledgeBaseCount)
    }

    @Test
    fun `initial state with monitoring disabled`() = runTest {
        val prefs = createUserPreferences(monitoringEnabled = false)
        every { preferencesManager.userPreferencesFlow } returns flowOf(prefs)
        coEvery { replyHistoryRepository.getTotalCount() } returns 0
        every { replyHistoryRepository.getRecentReplies(10) } returns flowOf(emptyList())
        every { keywordRuleRepository.getRuleCountFlow() } returns flowOf(0)

        val viewModel = HomeViewModel(
            preferencesManager = preferencesManager,
            replyHistoryRepository = replyHistoryRepository,
            keywordRuleRepository = keywordRuleRepository
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isMonitoringEnabled)
    }

    @Test
    fun `toggleMonitoring calls preferencesManager`() = runTest {
        val prefs = createUserPreferences(monitoringEnabled = true)
        every { preferencesManager.userPreferencesFlow } returns flowOf(prefs)
        coEvery { replyHistoryRepository.getTotalCount() } returns 0
        every { replyHistoryRepository.getRecentReplies(10) } returns flowOf(emptyList())
        every { keywordRuleRepository.getRuleCountFlow() } returns flowOf(0)
        coEvery { preferencesManager.updateMonitoringEnabled(any()) } returns Unit

        val viewModel = HomeViewModel(
            preferencesManager = preferencesManager,
            replyHistoryRepository = replyHistoryRepository,
            keywordRuleRepository = keywordRuleRepository
        )

        advanceUntilIdle()
        viewModel.toggleMonitoring()
        advanceUntilIdle()

        coVerify { preferencesManager.updateMonitoringEnabled(false) }
    }

    @Test
    fun `updateFloatingIconEnabled calls preferencesManager`() = runTest {
        val prefs = createUserPreferences()
        every { preferencesManager.userPreferencesFlow } returns flowOf(prefs)
        coEvery { replyHistoryRepository.getTotalCount() } returns 0
        every { replyHistoryRepository.getRecentReplies(10) } returns flowOf(emptyList())
        every { keywordRuleRepository.getRuleCountFlow() } returns flowOf(0)
        coEvery { preferencesManager.updateFloatingIconEnabled(any()) } returns Unit

        val viewModel = HomeViewModel(
            preferencesManager = preferencesManager,
            replyHistoryRepository = replyHistoryRepository,
            keywordRuleRepository = keywordRuleRepository
        )

        advanceUntilIdle()
        viewModel.updateFloatingIconEnabled(true)
        advanceUntilIdle()

        coVerify { preferencesManager.updateFloatingIconEnabled(true) }
    }

    @Test
    fun `updateSelectedApps calls preferencesManager`() = runTest {
        val prefs = createUserPreferences()
        every { preferencesManager.userPreferencesFlow } returns flowOf(prefs)
        coEvery { replyHistoryRepository.getTotalCount() } returns 0
        every { replyHistoryRepository.getRecentReplies(10) } returns flowOf(emptyList())
        every { keywordRuleRepository.getRuleCountFlow() } returns flowOf(0)
        coEvery { preferencesManager.updateSelectedApps(any()) } returns Unit

        val viewModel = HomeViewModel(
            preferencesManager = preferencesManager,
            replyHistoryRepository = replyHistoryRepository,
            keywordRuleRepository = keywordRuleRepository
        )

        advanceUntilIdle()
        val apps = setOf(PreferencesManager.WECHAT_PACKAGE, PreferencesManager.BAIJUYI_PACKAGE)
        viewModel.updateSelectedApps(apps)
        advanceUntilIdle()

        coVerify { preferencesManager.updateSelectedApps(apps) }
    }

    @Test
    fun `monitored apps are built from preferences`() = runTest {
        val selectedApps = setOf(PreferencesManager.WECHAT_PACKAGE)
        val prefs = createUserPreferences(selectedApps = selectedApps)
        every { preferencesManager.userPreferencesFlow } returns flowOf(prefs)
        coEvery { replyHistoryRepository.getTotalCount() } returns 0
        every { replyHistoryRepository.getRecentReplies(10) } returns flowOf(emptyList())
        every { keywordRuleRepository.getRuleCountFlow() } returns flowOf(0)

        val viewModel = HomeViewModel(
            preferencesManager = preferencesManager,
            replyHistoryRepository = replyHistoryRepository,
            keywordRuleRepository = keywordRuleRepository
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        val wechatApp = state.monitoredApps.find { it.packageName == PreferencesManager.WECHAT_PACKAGE }
        assertNotNull(wechatApp)
        assertTrue(wechatApp!!.isSelected)

        val tuJiaApp = state.monitoredApps.find { it.packageName == PreferencesManager.TUJIA_MINSU_PACKAGE }
        assertNotNull(tuJiaApp)
        assertFalse(tuJiaApp!!.isSelected)
    }

    @Test
    fun `recent replies are loaded`() = runTest {
        val prefs = createUserPreferences()
        val replies = listOf(
            createTestReply(1, "com.tencent.mm"),
            createTestReply(2, "com.meituan.minsu")
        )
        every { preferencesManager.userPreferencesFlow } returns flowOf(prefs)
        coEvery { replyHistoryRepository.getTotalCount() } returns 2
        every { replyHistoryRepository.getRecentReplies(10) } returns flowOf(replies)
        every { keywordRuleRepository.getRuleCountFlow() } returns flowOf(0)

        val viewModel = HomeViewModel(
            preferencesManager = preferencesManager,
            replyHistoryRepository = replyHistoryRepository,
            keywordRuleRepository = keywordRuleRepository
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.recentReplies.size)
    }

    @Test
    fun `today replies count is calculated`() = runTest {
        val prefs = createUserPreferences()
        val now = System.currentTimeMillis()
        val replies = listOf(
            createTestReply(1, "com.tencent.mm", now - 1000),
            createTestReply(2, "com.meituan.minsu", now - 2000),
            createTestReply(3, "com.tujia.minsu", now - 3 * 24 * 60 * 60 * 1000) // 3 days ago
        )
        every { preferencesManager.userPreferencesFlow } returns flowOf(prefs)
        coEvery { replyHistoryRepository.getTotalCount() } returns 3
        every { replyHistoryRepository.getRecentReplies(10) } returns flowOf(replies)
        every { keywordRuleRepository.getRuleCountFlow() } returns flowOf(0)

        val viewModel = HomeViewModel(
            preferencesManager = preferencesManager,
            replyHistoryRepository = replyHistoryRepository,
            keywordRuleRepository = keywordRuleRepository
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.todayReplies)
    }
}
