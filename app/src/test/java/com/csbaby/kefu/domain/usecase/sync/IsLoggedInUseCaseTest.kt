package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsLoggedInUseCaseTest {

    private val syncManager: SyncManager = mockk()
    private val useCase = IsLoggedInUseCase(syncManager)

    @Test
    fun `已登录返回 true`() {
        every { syncManager.isLoggedIn() } returns true

        assertTrue(useCase())
    }

    @Test
    fun `未登录返回 false`() {
        every { syncManager.isLoggedIn() } returns false

        assertFalse(useCase())
    }

    @Test
    fun `状态变化时正确反映`() {
        every { syncManager.isLoggedIn() } returnsMany listOf(false, true, false)

        assertFalse(useCase())
        assertTrue(useCase())
        assertFalse(useCase())
    }
}
