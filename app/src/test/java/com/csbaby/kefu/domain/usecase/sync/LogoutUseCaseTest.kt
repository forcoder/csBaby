package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class LogoutUseCaseTest {

    private val syncManager: SyncManager = mockk()
    private val useCase = LogoutUseCase(syncManager)

    @Test
    fun `登出调用 syncManager logout`() {
        justRun { syncManager.logout() }

        useCase()

        verify(exactly = 1) { syncManager.logout() }
    }

    @Test
    fun `登出可重复调用`() {
        justRun { syncManager.logout() }

        useCase()
        useCase()
        useCase()

        verify(exactly = 3) { syncManager.logout() }
    }

    @Test
    fun `未登录状态登出也安全`() {
        // 模拟 syncManager.logout 什么都不做（未登录态登出是幂等的）
        justRun { syncManager.logout() }

        useCase() // 不抛异常

        verify { syncManager.logout() }
    }
}
