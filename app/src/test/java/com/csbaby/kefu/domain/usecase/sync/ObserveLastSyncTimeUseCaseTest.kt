package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveLastSyncTimeUseCaseTest {

    private val syncManager: SyncManager = mockk()
    private val useCase = ObserveLastSyncTimeUseCase(syncManager)

    @Test
    fun `未登录时返回 0`() = runTest {
        every { syncManager.lastSyncTime } returns MutableStateFlow(0L)

        assertEquals(0L, useCase().first())
    }

    @Test
    fun `已登录有时间戳`() = runTest {
        every { syncManager.lastSyncTime } returns MutableStateFlow(1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, useCase().first())
    }

    @Test
    fun `时间戳变化后 first 拿到最新值`() = runTest {
        val flow = MutableStateFlow(0L)
        every { syncManager.lastSyncTime } returns flow

        flow.value = 100L
        flow.value = 200L

        assertEquals(200L, useCase().first())
    }
}