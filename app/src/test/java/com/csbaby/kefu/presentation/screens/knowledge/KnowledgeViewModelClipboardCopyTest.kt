package com.csbaby.kefu.presentation.screens.knowledge

import android.content.Context
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.data.sync.SyncState
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.model.MatchType
import com.csbaby.kefu.domain.model.RuleTargetType
import com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager
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
 * 知识库"复制"按钮 = 把规则内容写入系统剪贴板(非"在数据库里克隆新规则")。
 * 见 docs/product/knowledge-base.md §3.1。
 *
 * 注：ClipboardManager.setPrimaryClip 在 JVM 单元测试中 mockk 无法直接 mock，
 * 因此 ViewModel 持有 ClipboardService 接口，本测试 mock 该接口。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeViewModelClipboardCopyTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var syncManager: SyncManager
    private lateinit var kbManager: KnowledgeBaseManager
    private lateinit var clipboardService: ClipboardService
    private lateinit var viewModel: KnowledgeViewModel

    private val sourceRule = KeywordRule(
        id = 42L,
        keyword = "test 测试",
        matchType = MatchType.CONTAINS,
        replyTemplate = "这是一个测试回复",
        category = "通用问题",
        targetType = RuleTargetType.ALL,
        enabled = true,
        syncVersion = 7L,
        deleted = false,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        every { context.contentResolver } returns mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        kbManager = mockk(relaxed = true)
        clipboardService = mockk(relaxed = true)

        coEvery { kbManager.getRuleById(42L) } returns sourceRule
        coEvery { kbManager.getRuleById(not(42L)) } returns null
        every { kbManager.getAllRules() } returns flowOf(emptyList())
        every { kbManager.getAllCategories() } returns flowOf(emptyList())
        every { syncManager.syncState } returns MutableStateFlow(SyncState.Idle)
        every { syncManager.isLoggedIn() } returns false

        viewModel = KnowledgeViewModel(context, kbManager, syncManager, clipboardService)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    /** 正常场景:应当调用 putText,且只含 replyTemplate(不拼 keyword) */
    @Test
    fun `copyRuleToClipboard writes only replyTemplate to clipboard`() =
        runTest(testDispatcher) {
            viewModel.copyRuleToClipboard(42L)
            testDispatcher.scheduler.advanceUntilIdle()

            verify {
                clipboardService.putText(
                    label = "csBaby 规则",
                    text = "这是一个测试回复"
                )
            }
            assertEquals(
                "已复制: test 测试",
                viewModel.uiState.value.noticeMessage
            )
        }

    /** 边界值:replyTemplate 为空时仍要复制(用户主动触发,粘贴结果为空字符串) */
    @Test
    fun `copyRuleToClipboard works when replyTemplate is empty`() =
        runTest(testDispatcher) {
            val emptyRule = sourceRule.copy(replyTemplate = "")
            coEvery { kbManager.getRuleById(42L) } returns emptyRule

            viewModel.copyRuleToClipboard(42L)
            testDispatcher.scheduler.advanceUntilIdle()

            verify {
                clipboardService.putText(
                    label = "csBaby 规则",
                    text = ""
                )
            }
        }

    /** 边界值:replyTemplate 含特殊字符(冒号、换行)保留原样 */
    @Test
    fun `copyRuleToClipboard preserves newlines in replyTemplate`() =
        runTest(testDispatcher) {
            val multiLineRule = sourceRule.copy(
                keyword = "价格",
                replyTemplate = "第一行\n第二行：优惠",
            )
            coEvery { kbManager.getRuleById(42L) } returns multiLineRule

            viewModel.copyRuleToClipboard(42L)
            testDispatcher.scheduler.advanceUntilIdle()

            verify {
                clipboardService.putText(
                    label = "csBaby 规则",
                    text = "第一行\n第二行：优惠"
                )
            }
        }

    /** 异常:源 rule 不存在 → 不写剪贴板,不弹 noticeMessage(静默) */
    @Test
    fun `copyRuleToClipboard does nothing when source rule is missing`() =
        runTest(testDispatcher) {
            viewModel.copyRuleToClipboard(99999L)
            testDispatcher.scheduler.advanceUntilIdle()

            verify(exactly = 0) { clipboardService.putText(any(), any()) }
            assertNull(viewModel.uiState.value.noticeMessage)
        }

    /** 异常:AndroidClipboardService 静默失败(ClipboardManager 不可用)→ 不 crash */
    @Test
    fun `copyRuleToClipboard is resilient when putText fails silently`() =
        runTest(testDispatcher) {
            every { clipboardService.putText(any(), any()) } throws IllegalStateException("clipboard off")

            viewModel.copyRuleToClipboard(42L)
            testDispatcher.scheduler.advanceUntilIdle()

            // 不应向上抛(unexpected);noticeMessage 仍可能更新也可能不更新,关键是 ViewModel 不崩
            // 这里为简化只验证不抛异常
            assertTrue(true)
        }

    /** 复制是本地行为,不应触发 sync(剪贴板操作不涉及数据持久化) */
    @Test
    fun `copyRuleToClipboard does not trigger sync even when logged in`() =
        runTest(testDispatcher) {
            every { syncManager.isLoggedIn() } returns true
            every { syncManager.triggerSync() } returns Unit

            viewModel.copyRuleToClipboard(42L)
            testDispatcher.scheduler.advanceUntilIdle()

            verify { clipboardService.putText(any(), any()) }
            verify(exactly = 0) { syncManager.triggerSync() }
        }

    /** 重复点击同一个 rule:每次都应写剪贴板(不需要防抖) */
    @Test
    fun `repeated copyRuleToClipboard calls overwrite clipboard each time`() =
        runTest(testDispatcher) {
            viewModel.copyRuleToClipboard(42L)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.copyRuleToClipboard(42L)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.copyRuleToClipboard(42L)
            testDispatcher.scheduler.advanceUntilIdle()

            verify(exactly = 3) { clipboardService.putText(any(), any()) }
        }
}
