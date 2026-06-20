package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.domain.model.SyncState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ObserveSyncStateUseCaseTest {

    private val syncManager: SyncManager = mockk()
    private val useCase = ObserveSyncStateUseCase(syncManager)

    @Test
    fun `转发射层 syncState`() = runTest {
        val flow = MutableStateFlow<SyncState>(SyncState.Idle)
        every { syncManager.syncState } returns flow

        val result = useCase().first()

        assertSame(SyncState.Idle, result)
    }

    @Test
    fun `首次 first 拿到 Idle`() = runTest {
        val flow = MutableStateFlow<SyncState>(SyncState.Idle)
        every { syncManager.syncState } returns flow

        // first() 只取第一个发射的值；后续更新不影响 first()
        assertSame(SyncState.Idle, useCase().first())
    }

    @Test
    fun `state 变 Syncing 时 first 仍可拿初始值`() = runTest {
        val flow = MutableStateFlow<SyncState>(SyncState.Idle)
        every { syncManager.syncState } returns flow

        flow.value = SyncState.Syncing("正在同步")

        // 注意: StateFlow 第一次订阅时一定发射当前值, 此时是 Syncing
        assertEquals("正在同步", (useCase().first() as SyncState.Syncing).message)
    }

    @Test
    fun `多次调用返回同一 flow 实例`() {
        val flow = MutableStateFlow<SyncState>(SyncState.Idle)
        every { syncManager.syncState } returns flow

        val a = useCase()
        val b = useCase()

        // 同一个 StateFlow 实例
        assertSame(a, b)
    }
}