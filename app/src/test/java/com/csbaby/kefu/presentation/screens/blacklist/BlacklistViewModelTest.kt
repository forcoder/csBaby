package com.csbaby.kefu.presentation.screens.blacklist

import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import com.csbaby.kefu.domain.repository.MessageBlacklistRepository
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
class BlacklistViewModelTest {

    private lateinit var blacklistRepository: MessageBlacklistRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        blacklistRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads blacklists from repository`() = runTest {
        val items = listOf(
            MessageBlacklistEntity(id = 1, type = "KEYWORD", value = "垃圾", description = ""),
            MessageBlacklistEntity(id = 2, type = "SENDER", value = "spam_user", description = "垃圾用户")
        )
        every { blacklistRepository.getAll() } returns flowOf(items)

        val viewModel = BlacklistViewModel(
            appContext = mockk(relaxed = true),
            blacklistRepository = blacklistRepository
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.blacklists.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `addBlacklist with blank value shows error`() = runTest {
        every { blacklistRepository.getAll() } returns flowOf(emptyList())

        val viewModel = BlacklistViewModel(
            appContext = mockk(relaxed = true),
            blacklistRepository = blacklistRepository
        )

        viewModel.addBlacklist(type = "KEYWORD", value = "  ")
        assertEquals("黑名单值不能为空", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `addBlacklist with valid value calls repository`() = runTest {
        every { blacklistRepository.getAll() } returns flowOf(emptyList())
        coEvery { blacklistRepository.addToBlacklist(any(), any(), any(), any()) } returns 1L

        val viewModel = BlacklistViewModel(
            appContext = mockk(relaxed = true),
            blacklistRepository = blacklistRepository
        )

        viewModel.addBlacklist(type = "KEYWORD", value = "测试关键词", description = "备注")
        advanceUntilIdle()

        coVerify { blacklistRepository.addToBlacklist("KEYWORD", "测试关键词", "备注", null) }
        assertEquals("添加成功", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `removeBlacklist calls repository`() = runTest {
        every { blacklistRepository.getAll() } returns flowOf(emptyList())
        coEvery { blacklistRepository.removeFromBlacklist(any()) } returns Unit

        val viewModel = BlacklistViewModel(
            appContext = mockk(relaxed = true),
            blacklistRepository = blacklistRepository
        )

        viewModel.removeBlacklist(1L)
        advanceUntilIdle()

        coVerify { blacklistRepository.removeFromBlacklist(1L) }
        assertEquals("已删除", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `toggleBlacklist enables item`() = runTest {
        every { blacklistRepository.getAll() } returns flowOf(emptyList())
        coEvery { blacklistRepository.toggleBlacklist(any(), any()) } returns Unit

        val viewModel = BlacklistViewModel(
            appContext = mockk(relaxed = true),
            blacklistRepository = blacklistRepository
        )

        viewModel.toggleBlacklist(1L, true)
        advanceUntilIdle()

        coVerify { blacklistRepository.toggleBlacklist(1L, true) }
        assertEquals("已启用", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `toggleBlacklist disables item`() = runTest {
        every { blacklistRepository.getAll() } returns flowOf(emptyList())
        coEvery { blacklistRepository.toggleBlacklist(any(), any()) } returns Unit

        val viewModel = BlacklistViewModel(
            appContext = mockk(relaxed = true),
            blacklistRepository = blacklistRepository
        )

        viewModel.toggleBlacklist(1L, false)
        advanceUntilIdle()

        coVerify { blacklistRepository.toggleBlacklist(1L, false) }
        assertEquals("已禁用", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `clearAll calls repository`() = runTest {
        every { blacklistRepository.getAll() } returns flowOf(emptyList())
        coEvery { blacklistRepository.clearAll() } returns Unit

        val viewModel = BlacklistViewModel(
            appContext = mockk(relaxed = true),
            blacklistRepository = blacklistRepository
        )

        viewModel.clearAll()
        advanceUntilIdle()

        coVerify { blacklistRepository.clearAll() }
        assertEquals("已清空所有黑名单", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `consumeNoticeMessage clears notice`() = runTest {
        every { blacklistRepository.getAll() } returns flowOf(emptyList())

        val viewModel = BlacklistViewModel(
            appContext = mockk(relaxed = true),
            blacklistRepository = blacklistRepository
        )

        // Trigger a notice
        viewModel.addBlacklist(type = "KEYWORD", value = "  ")
        assertNotNull(viewModel.uiState.value.noticeMessage)

        viewModel.consumeNoticeMessage()
        assertNull(viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun `empty state shows correct message`() = runTest {
        every { blacklistRepository.getAll() } returns flowOf(emptyList())

        val viewModel = BlacklistViewModel(
            appContext = mockk(relaxed = true),
            blacklistRepository = blacklistRepository
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.blacklists.isEmpty())
        assertFalse(state.isLoading)
    }
}
