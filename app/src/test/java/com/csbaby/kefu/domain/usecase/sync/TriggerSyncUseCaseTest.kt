package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class TriggerSyncUseCaseTest {

    private val syncManager: SyncManager = mockk()
    private val useCase = TriggerSyncUseCase(syncManager)

    @Test
    fun `调用 syncManager triggerSync`() {
        justRun { syncManager.triggerSync() }

        useCase()

        verify(exactly = 1) { syncManager.triggerSync() }
    }

    @Test
    fun `多次调用依次触发`() {
        justRun { syncManager.triggerSync() }

        useCase()
        useCase()

        verify(exactly = 2) { syncManager.triggerSync() }
    }

    @Test
    fun `未登录不抛异常 (由 SyncManager 内部处理)`() {
        // SyncManager.triggerSync() 内部会判 tenantId, 未登录直接 return
        justRun { syncManager.triggerSync() }

        useCase()

        verify { syncManager.triggerSync() }
    }
}