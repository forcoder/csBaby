package com.csbaby.kefu.presentation.screens.knowledge

import android.content.Context
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
class KnowledgeViewModelDuplicateTest {

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

    private fun createSourceRule() = KeywordRule(
        id = 7L,
        keyword = "你好",
        matchType = MatchType.EXACT,
        replyTemplate = "您好,请问有什么可以帮您",
        category = "问候",
        targetType = RuleTargetType.PROPERTY,
        targetNames = listOf("房源A", "房源B"),
        priority = 42,
        enabled = false
    )

    private fun createViewModel(): KnowledgeViewModel {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        return KnowledgeViewModel(
            appContext = mockk(relaxed = true),
            knowledgeBaseManager = knowledgeBaseManager,
            syncManager = syncManager
        )
    }

    @Test
    fun `duplicateRule creates new rule with different id and 副本 suffix`() = runTest {
        val source = createSourceRule()
        coEvery { knowledgeBaseManager.getRuleById(7L) } returns source
        coEvery { knowledgeBaseManager.createRule(any()) } returns 99L
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.duplicateRule(7L)
        advanceUntilIdle()

        val captor = slot<KeywordRule>()
        coVerify { knowledgeBaseManager.createRule(capture(captor)) }
        val created = captor.captured
        assertEquals(0L, created.id)
        assertEquals("你好 副本", created.keyword)
    }

    @Test
    fun `duplicateRule copies all other fields`() = runTest {
        val source = createSourceRule()
        coEvery { knowledgeBaseManager.getRuleById(7L) } returns source
        coEvery { knowledgeBaseManager.createRule(any()) } returns 99L
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.duplicateRule(7L)
        advanceUntilIdle()

        val captor = slot<KeywordRule>()
        coVerify { knowledgeBaseManager.createRule(capture(captor)) }
        val created = captor.captured
        assertEquals(MatchType.EXACT, created.matchType)
        assertEquals("您好,请问有什么可以帮您", created.replyTemplate)
        assertEquals("问候", created.category)
        assertEquals(RuleTargetType.PROPERTY, created.targetType)
        assertEquals(listOf("房源A", "房源B"), created.targetNames)
        assertEquals(42, created.priority)
        assertEquals(false, created.enabled)
    }

    @Test
    fun `duplicateRule sets success noticeMessage`() = runTest {
        val source = createSourceRule()
        coEvery { knowledgeBaseManager.getRuleById(7L) } returns source
        coEvery { knowledgeBaseManager.createRule(any()) } returns 99L
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.duplicateRule(7L)
        advanceUntilIdle()

        val notice = viewModel.uiState.value.noticeMessage
        assertNotNull(notice)
        assertTrue(notice!!.contains("你好"))
    }

    @Test
    fun `duplicateRule shows 规则不存在 when source not found`() = runTest {
        coEvery { knowledgeBaseManager.getRuleById(404L) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.duplicateRule(404L)
        advanceUntilIdle()

        val notice = viewModel.uiState.value.noticeMessage
        assertNotNull(notice)
        assertTrue(notice!!.contains("规则不存在"))

        coVerify(exactly = 0) { knowledgeBaseManager.createRule(any()) }
    }
}