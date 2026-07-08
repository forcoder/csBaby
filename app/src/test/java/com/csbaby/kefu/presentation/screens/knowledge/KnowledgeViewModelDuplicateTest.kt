package com.csbaby.kefu.presentation.screens.knowledge

import android.content.ClipData
import android.content.ClipboardManager
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
    private lateinit var mockContext: Context
    private lateinit var mockClipboardManager: ClipboardManager
    private lateinit var mockClipData: ClipData
    private val testDispatcher = StandardTestDispatcher()

    // ========= 测试数据工厂 =========

    companion object {
        private const val EXISTING_RULE_ID = 7L
        private const val NON_EXISTENT_RULE_ID = 404L
        private val BASE_RULE = KeywordRule(
            id = EXISTING_RULE_ID,
            keyword = "你好",
            matchType = MatchType.EXACT,
            replyTemplate = "您好,请问有什么可以帮您",
            category = "问候",
            targetType = RuleTargetType.PROPERTY,
            targetNames = listOf("房源A", "房源B"),
            priority = 42,
            enabled = false
        )
    }

    // ========= Setup / Teardown =========

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        knowledgeBaseManager = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        mockClipboardManager = mockk(relaxed = true)
        mockClipData = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns mockClipboardManager

        // mock 静态方法 ClipData.newPlainText，避免 Android 单元测试 "not mocked" 异常
        mockkStatic(ClipData::class)
        every { ClipData.newPlainText(any(), any()) } returns mockClipData
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): KnowledgeViewModel {
        every { knowledgeBaseManager.getAllRules() } returns flowOf(emptyList())
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        return KnowledgeViewModel(
            appContext = mockContext,
            knowledgeBaseManager = knowledgeBaseManager,
            syncManager = syncManager
        )
    }

    // ================================================================
    //  正常场景 (6个)
    // ================================================================

    @Test
    fun `copyReplyToClipboard copies replyTemplate to clipboard`() = runTest {
        val rule = BASE_RULE
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns rule

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        // 验证 ClipData.newPlainText 被用正确的回复内容调用
        verify { ClipData.newPlainText("suggested_reply", BASE_RULE.replyTemplate) }
        // 验证剪贴板 setPrimaryClip 被调用
        verify { mockClipboardManager.setPrimaryClip(mockClipData) }
    }

    @Test
    fun `copyReplyToClipboard shows success noticeMessage`() = runTest {
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns BASE_RULE

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        val notice = viewModel.uiState.value.noticeMessage
        assertEquals("已复制回复内容", notice)
    }

    @Test
    fun `copyReplyToClipboard does not create rule or trigger sync`() = runTest {
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns BASE_RULE

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        coVerify(exactly = 0) { knowledgeBaseManager.createRule(any()) }
        coVerify(exactly = 0) { syncManager.triggerSync() }
    }

    @Test
    fun `copyReplyToClipboard with emoji and special characters`() = runTest {
        val emojiReply = "您好！😊 很高兴为您服务~ 【价格】¥100/晚 ★好评\n祝您入住愉快！🎉"
        val rule = BASE_RULE.copy(replyTemplate = emojiReply)
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns rule

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        verify { ClipData.newPlainText("suggested_reply", emojiReply) }
        verify { mockClipboardManager.setPrimaryClip(mockClipData) }
        assertEquals("已复制回复内容", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `copyReplyToClipboard with multi-line replyTemplate`() = runTest {
        val multiLineReply = "您好，感谢您的咨询！\n\n" +
            "关于您的问题，以下是为您提供的方案：\n" +
            "1. 方案一：XXXXX\n" +
            "2. 方案二：YYYYY\n\n" +
            "请确认是否需要进一步协助。"
        val rule = BASE_RULE.copy(replyTemplate = multiLineReply)
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns rule

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        verify { ClipData.newPlainText("suggested_reply", multiLineReply) }
        verify { mockClipboardManager.setPrimaryClip(mockClipData) }
        assertEquals("已复制回复内容", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `copyReplyToClipboard with very long replyTemplate`() = runTest {
        val longReply = "您好，".repeat(200) // 600+ 字的长文本
        val rule = BASE_RULE.copy(replyTemplate = longReply)
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns rule

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        verify { ClipData.newPlainText("suggested_reply", longReply) }
        verify { mockClipboardManager.setPrimaryClip(mockClipData) }
        assertEquals("已复制回复内容", viewModel.uiState.value.noticeMessage)
    }

    // ================================================================
    //  边界值场景 (5个)
    // ================================================================

    @Test
    fun `copyReplyToClipboard with empty replyTemplate shows error`() = runTest {
        val rule = BASE_RULE.copy(replyTemplate = "")
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns rule

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        assertEquals("回复内容为空，无法复制", viewModel.uiState.value.noticeMessage)
        verify(exactly = 0) { mockClipboardManager.setPrimaryClip(any()) }
    }

    @Test
    fun `copyReplyToClipboard with blank replyTemplate of spaces shows error`() = runTest {
        val rule = BASE_RULE.copy(replyTemplate = "   ")
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns rule

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        assertEquals("回复内容为空，无法复制", viewModel.uiState.value.noticeMessage)
        verify(exactly = 0) { mockClipboardManager.setPrimaryClip(any()) }
    }

    @Test
    fun `copyReplyToClipboard with blank replyTemplate of newlines and tabs shows error`() = runTest {
        val rule = BASE_RULE.copy(replyTemplate = "\n\n\t\n  \n")
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns rule

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        assertEquals("回复内容为空，无法复制", viewModel.uiState.value.noticeMessage)
        verify(exactly = 0) { mockClipboardManager.setPrimaryClip(any()) }
    }

    @Test
    fun `copyReplyToClipboard with single character replyTemplate`() = runTest {
        val rule = BASE_RULE.copy(replyTemplate = "好")
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns rule

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        verify { ClipData.newPlainText("suggested_reply", "好") }
        verify { mockClipboardManager.setPrimaryClip(mockClipData) }
        assertEquals("已复制回复内容", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `copyReplyToClipboard with mixed blank and valid content succeeds`() = runTest {
        // replyTemplate 虽然是空白开头，但不是 isBlank（有实际内容）
        val rule = BASE_RULE.copy(replyTemplate = "  您好")
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns rule

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        verify { ClipData.newPlainText("suggested_reply", "  您好") }
        verify { mockClipboardManager.setPrimaryClip(mockClipData) }
        assertEquals("已复制回复内容", viewModel.uiState.value.noticeMessage)
    }

    // ================================================================
    //  异常/错误场景 (5个)
    // ================================================================

    @Test
    fun `copyReplyToClipboard with non-existent rule shows error`() = runTest {
        coEvery { knowledgeBaseManager.getRuleById(NON_EXISTENT_RULE_ID) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(NON_EXISTENT_RULE_ID)
        advanceUntilIdle()

        assertEquals("规则不存在", viewModel.uiState.value.noticeMessage)
        verify(exactly = 0) { mockClipboardManager.setPrimaryClip(any()) }
    }

    @Test
    fun `copyReplyToClipboard handles SecurityException from clipboard`() = runTest {
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns BASE_RULE
        every { mockClipboardManager.setPrimaryClip(any()) } throws SecurityException("no permission")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        assertEquals("复制失败：没有剪贴板访问权限", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `copyReplyToClipboard uses correct clip label`() = runTest {
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns BASE_RULE

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        verify { ClipData.newPlainText("suggested_reply", BASE_RULE.replyTemplate) }
    }

    @Test
    fun `copyReplyToClipboard with rule id 0 works`() = runTest {
        val rule = BASE_RULE.copy(id = 0L)
        coEvery { knowledgeBaseManager.getRuleById(0L) } returns rule

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(0L)
        advanceUntilIdle()

        verify { ClipData.newPlainText("suggested_reply", rule.replyTemplate) }
        verify { mockClipboardManager.setPrimaryClip(any()) }
        assertEquals("已复制回复内容", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `copyReplyToClipboard preserves other UI state fields`() = runTest {
        // 验证复制操作不会影响其他 UI 状态（rules, isLoading, isImporting 等）
        val rules = listOf(
            KeywordRule(id = 1, keyword = "测试", matchType = MatchType.CONTAINS,
                replyTemplate = "测试回复", category = "", targetType = RuleTargetType.ALL,
                targetNames = emptyList(), priority = 0, enabled = true)
        )
        // 在 createViewModel 前设置好 mock
        every { knowledgeBaseManager.getAllRules() } returns flowOf(rules)
        every { knowledgeBaseManager.getAllCategories() } returns flowOf(emptyList())
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns BASE_RULE

        val viewModel = KnowledgeViewModel(
            appContext = mockContext,
            knowledgeBaseManager = knowledgeBaseManager,
            syncManager = syncManager
        )
        advanceUntilIdle()

        val stateBefore = viewModel.uiState.value
        assertFalse(stateBefore.isLoading)
        assertEquals(1, stateBefore.rules.size)

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        val stateAfter = viewModel.uiState.value
        assertEquals("已复制回复内容", stateAfter.noticeMessage)
        assertEquals(stateBefore.rules.size, stateAfter.rules.size)
        assertEquals(stateBefore.isLoading, stateAfter.isLoading)
        assertEquals(stateBefore.isImporting, stateAfter.isImporting)
        assertEquals(stateBefore.isClearing, stateAfter.isClearing)
    }

    // ================================================================
    //  回归防护：防止回到旧"生成副本"行为 (2个)
    // ================================================================

    @Test
    fun `ViewModel does NOT have duplicateRule method anymore`() {
        // 编译期防护：如果回归到旧名 duplicateRule，此测试会编译失败
        val methods = KnowledgeViewModel::class.java.methods.map { it.name }
        assertFalse("duplicateRule should not exist, use copyReplyToClipboard instead",
            methods.contains("duplicateRule"))
    }

    @Test
    fun `copyReplyToClipboard does NOT append 副本 suffix to anything`() = runTest {
        // 即使关键字包含"副本"文本，也不会触发旧逻辑
        val ruleWithCopyText = BASE_RULE.copy(keyword = "副本测试")
        coEvery { knowledgeBaseManager.getRuleById(EXISTING_RULE_ID) } returns ruleWithCopyText

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.copyReplyToClipboard(EXISTING_RULE_ID)
        advanceUntilIdle()

        // 验证没有 createRule 调用（旧行为会调用 createRule）
        coVerify(exactly = 0) { knowledgeBaseManager.createRule(any()) }
        verify { mockClipboardManager.setPrimaryClip(any()) }
        assertEquals("已复制回复内容", viewModel.uiState.value.noticeMessage)
    }
}
