package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.data.sync.SyncQueue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveSyncQueueUseCaseTest {

    private val syncManager: SyncManager = mockk()
    private val useCase = ObserveSyncQueueUseCase(syncManager)

    private fun mockQueue(initial: Int): SyncQueue {
        val flow = MutableStateFlow(initial)
        val q = mockk<SyncQueue>()
        every { q.pendingCount } returns flow
        return q
    }

    @Test
    fun `初始为 0`() = runTest {
        every { syncManager.queue } returns mockQueue(0)

        assertEquals(0, useCase().first())
    }

    @Test
    fun `初始有 5 条待同步`() = runTest {
        every { syncManager.queue } returns mockQueue(5)

        assertEquals(5, useCase().first())
    }

    @Test
    fun `count 增加到 10 后 first 拿到 10`() = runTest {
        val flow = MutableStateFlow(0)
        val q = mockk<SyncQueue>()
        every { q.pendingCount } returns flow
        every { syncManager.queue } returns q

        flow.value = 10

        // first() 拿到 StateFlow 当前值
        assertEquals(10, useCase().first())
    }

    @Test
    fun `count 多次变化 first 拿最新`() = runTest {
        val flow = MutableStateFlow(0)
        val q = mockk<SyncQueue>()
        every { q.pendingCount } returns flow
        every { syncManager.queue } returns q

        flow.value = 3
        flow.value = 7
        flow.value = 100

        assertEquals(100, useCase().first())
    }
}