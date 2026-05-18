package com.csbaby.kefu.presentation.screens.knowledge

import android.content.Context
import com.csbaby.kefu.data.local.KefuDatabase
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.DeviceManager
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.model.MatchType
import com.csbaby.kefu.domain.model.RuleTargetType
import com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager
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
class KnowledgeViewModelTest {

    private lateinit var knowledgeBaseManager: KnowledgeBaseManager
    private lateinit var apiService: CsbabyApiService
    private lateinit var deviceManager: DeviceManager
    private lateinit var kefuDatabase: KefuDatabase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        knowledgeBaseManager = mockk()
        apiService = mockk()
        deviceManager = mockk()
        kefuDatabase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createTestRule(id: Long, keyword: String, category: String = "默认") = KeywordRule(
        id = id,
        keyword = keyword,
        matchType = MatchType.CONTAINS,
        replyTemplate = "回复$keyword",
        category = category,
        targetType = RuleTargetType.ALL,
        targetNames = emptyList(),
        priority = 0,
        enabled = true
    )

    @Test
    fun `initial state has empty rules list`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.rules.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `search filters rules by keyword`() = runTest {
        val rules = listOf(
            createTestRule(1, "你好", "问候"),
            createTestRule(2, "价格", "售后")
        )
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(listOf("问候", "售后"))

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        advanceUntilIdle()
        viewModel.search("你好")

        val state = viewModel.uiState.value
        assertEquals(1, state.rules.size)
        assertEquals("你好", state.rules[0].keyword)
    }

    @Test
    fun `search with empty query returns all rules`() = runTest {
        val rules = listOf(
            createTestRule(1, "你好", "问候"),
            createTestRule(2, "价格", "售后")
        )
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(listOf("问候", "售后"))

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        advanceUntilIdle()
        viewModel.search("")

        val state = viewModel.uiState.value
        assertEquals(2, state.rules.size)
    }

    @Test
    fun `search is case insensitive`() = runTest {
        val rules = listOf(createTestRule(1, "Hello", "问候"))
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(listOf("问候"))

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        advanceUntilIdle()
        viewModel.search("hello")

        val state = viewModel.uiState.value
        assertEquals(1, state.rules.size)
    }

    @Test
    fun `deleteRule calls repository and rebuilds trie`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.deleteRule(any()) } returns Unit
        coEvery { knowledgeBaseManager.initializeMatcher() } returns Unit

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        viewModel.deleteRule(1L)
        advanceUntilIdle()

        coVerify { knowledgeBaseManager.deleteRule(1L) }
        coVerify { knowledgeBaseManager.initializeMatcher() }
    }

    @Test
    fun `toggleRule calls repository and rebuilds trie`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.toggleRule(any(), any()) } returns Unit
        coEvery { knowledgeBaseManager.initializeMatcher() } returns Unit

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        viewModel.toggleRule(1L, false)
        advanceUntilIdle()

        coVerify { knowledgeBaseManager.toggleRule(1L, false) }
        coVerify { knowledgeBaseManager.initializeMatcher() }
    }

    @Test
    fun `saveRule creates new rule when id is 0`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.createRule(any()) } returns 1L
        coEvery { knowledgeBaseManager.initializeMatcher() } returns Unit

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        val newRule = createTestRule(0, "测试")
        viewModel.saveRule(newRule)
        advanceUntilIdle()

        coVerify { knowledgeBaseManager.createRule(any()) }
    }

    @Test
    fun `saveRule updates existing rule when id is not 0`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.updateRule(any()) } returns Unit
        coEvery { knowledgeBaseManager.initializeMatcher() } returns Unit

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        val existingRule = createTestRule(5, "测试")
        viewModel.saveRule(existingRule)
        advanceUntilIdle()

        coVerify { knowledgeBaseManager.updateRule(any()) }
    }

    @Test
    fun `clearAllRules shows message when already empty`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.clearAllRules() } returns 0
        coEvery { knowledgeBaseManager.initializeMatcher() } returns Unit

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        advanceUntilIdle()
        viewModel.clearAllRules()
        advanceUntilIdle()

        assertEquals("知识库已经是空的", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `consumeNoticeMessage clears notice`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.clearAllRules() } returns 0
        coEvery { knowledgeBaseManager.initializeMatcher() } returns Unit

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        advanceUntilIdle()
        viewModel.clearAllRules()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.noticeMessage)
        viewModel.consumeNoticeMessage()
        assertNull(viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `search matches reply template content`() = runTest {
        val rules = listOf(
            createTestRule(1, "你好", "问候"),
            createTestRule(2, "再见", "问候")
        )
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(listOf("问候"))

        val viewModel = KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            apiService = apiService,
            deviceManager = deviceManager,
            kefuDatabase = kefuDatabase
        )

        advanceUntilIdle()
        viewModel.search("回复你好")

        val state = viewModel.uiState.value
        assertEquals(1, state.rules.size)
        assertEquals("你好", state.rules[0].keyword)
    }
}
