package com.csbaby.kefu.presentation.screens.knowledge

import android.content.Context
import android.net.Uri
import com.csbaby.kefu.data.sync.SyncManager
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
    private lateinit var syncManager: SyncManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        knowledgeBaseManager = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
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

    private fun createViewModel(context: Context): KnowledgeViewModel {
        return KnowledgeViewModel(
            appContext = context,
            knowledgeBaseManager = knowledgeBaseManager,
            syncManager = syncManager
        )
    }

    @Test
    fun `initial state loads rules and categories`() = runTest {
        val rules = listOf(
            createTestRule(1, "你好", "问候"),
            createTestRule(2, "价格", "售后")
        )
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(listOf("问候", "售后"))

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.rules.size)
        assertEquals(2, state.totalRuleCount)
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

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()
        viewModel.search("你好")

        val state = viewModel.uiState.value
        assertEquals(1, state.rules.size)
        assertEquals("你好", state.rules[0].keyword)
    }

    @Test
    fun `search matches reply template content`() = runTest {
        val rules = listOf(
            createTestRule(1, "你好", "问候"),
            createTestRule(2, "再见", "问候")
        )
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(listOf("问候"))

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()
        viewModel.search("回复你好")

        val state = viewModel.uiState.value
        assertEquals(1, state.rules.size)
        assertEquals("你好", state.rules[0].keyword)
    }

    @Test
    fun `search matches category content`() = runTest {
        val rules = listOf(
            createTestRule(1, "你好", "问候语"),
            createTestRule(2, "价格", "售后问题")
        )
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(listOf("问候语", "售后问题"))

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()
        viewModel.search("问候")

        val state = viewModel.uiState.value
        assertEquals(1, state.rules.size)
        assertEquals("你好", state.rules[0].keyword)
    }

    @Test
    fun `search with empty query returns all rules`() = runTest {
        val rules = listOf(
            createTestRule(1, "你好"),
            createTestRule(2, "价格")
        )
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(listOf("默认"))

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()
        viewModel.search("")

        val state = viewModel.uiState.value
        assertEquals(2, state.rules.size)
    }

    @Test
    fun `search with blank query returns all rules`() = runTest {
        val rules = listOf(
            createTestRule(1, "你好"),
            createTestRule(2, "价格")
        )
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(listOf("默认"))

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()
        viewModel.search("   ")

        val state = viewModel.uiState.value
        assertEquals(2, state.rules.size)
    }

    @Test
    fun `saveRule creates new rule`() = runTest {
        val rule = createTestRule(0, "新规则", "新分类")
        coEvery { knowledgeBaseManager.createRule(rule) } returns 1L
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()

        viewModel.saveRule(rule)
        advanceUntilIdle()

        coVerify { knowledgeBaseManager.createRule(rule) }
    }

    @Test
    fun `saveRule updates existing rule`() = runTest {
        val rule = createTestRule(1, "更新规则", "分类")
        coEvery { knowledgeBaseManager.updateRule(rule) } returns Unit
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()

        viewModel.saveRule(rule)
        advanceUntilIdle()

        coVerify { knowledgeBaseManager.updateRule(rule) }
    }

    @Test
    fun `saveRule triggers sync when logged in`() = runTest {
        val rule = createTestRule(0, "新规则", "新分类")
        coEvery { knowledgeBaseManager.createRule(rule) } returns 1L
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        every { syncManager.isLoggedIn() } returns true
        coEvery { syncManager.triggerSync() } returns Unit

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()

        viewModel.saveRule(rule)
        advanceUntilIdle()

        coVerify { syncManager.triggerSync() }
    }

    @Test
    fun `deleteRule calls knowledge base manager`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.deleteRule(any()) } returns Unit
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()

        viewModel.deleteRule(1L)
        advanceUntilIdle()

        coVerify { knowledgeBaseManager.deleteRule(1L) }
    }

    @Test
    fun `deleteRule triggers sync when logged in`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.deleteRule(any()) } returns Unit
        every { syncManager.isLoggedIn() } returns true
        coEvery { syncManager.triggerSync() } returns Unit

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()

        viewModel.deleteRule(1L)
        advanceUntilIdle()

        coVerify { syncManager.triggerSync() }
    }

    @Test
    fun `toggleRule calls knowledge base manager`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.toggleRule(any(), any()) } returns Unit
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()

        viewModel.toggleRule(1L, false)
        advanceUntilIdle()

        coVerify { knowledgeBaseManager.toggleRule(1L, false) }
    }

    @Test
    fun `toggleRule triggers sync when logged in`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.toggleRule(any(), any()) } returns Unit
        every { syncManager.isLoggedIn() } returns true
        coEvery { syncManager.triggerSync() } returns Unit

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()

        viewModel.toggleRule(1L, false)
        advanceUntilIdle()

        coVerify { syncManager.triggerSync() }
    }

    @Test
    fun `clearAllRules when not empty shows notice`() = runTest {
        val rules = listOf(createTestRule(1, "测试"))
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(listOf("默认"))
        coEvery { knowledgeBaseManager.clearAllRules() } returns 1
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()
        viewModel.clearAllRules()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `clearAllRules when empty shows notice`() = runTest {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.clearAllRules() } returns 0

        val viewModel = createViewModel(mockk(relaxed = true))
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

        val viewModel = createViewModel(mockk(relaxed = true))
        advanceUntilIdle()
        viewModel.clearAllRules()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.noticeMessage)
        viewModel.consumeNoticeMessage()
        assertNull(viewModel.uiState.value.noticeMessage)
    }
}
