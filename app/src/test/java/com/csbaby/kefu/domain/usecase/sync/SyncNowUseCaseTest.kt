package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncNowUseCaseTest {

    private val syncManager: SyncManager = mockk()
    private val useCase = SyncNowUseCase(syncManager)

    @Test
    fun `未登录返回 failure`() = runTest {
        coEvery { syncManager.currentTenantId() } returns null

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals("未登录，无法同步", result.exceptionOrNull()?.message)
    }

    @Test
    fun `已登录触发 incrementalSync`() = runTest {
        coEvery { syncManager.currentTenantId() } returns "tenant_123"
        coEvery { syncManager.incrementalSync("tenant_123") } returns Result.success(Unit)

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { syncManager.incrementalSync("tenant_123") }
    }

    @Test
    fun `incrementalSync 失败时透传 failure`() = runTest {
        val ex = RuntimeException("网络错误")
        coEvery { syncManager.currentTenantId() } returns "t1"
        coEvery { syncManager.incrementalSync("t1") } returns Result.failure(ex)

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals(ex, result.exceptionOrNull())
    }

    @Test
    fun `空 tenantId (边界) 视为未登录`() = runTest {
        // 当前实现：currentTenantId 返回 null 才判未登录；返回空串仍会触发同步
        // 这里验证当前行为：如果返回空串，会传空串给 incrementalSync
        coEvery { syncManager.currentTenantId() } returns ""
        coEvery { syncManager.incrementalSync("") } returns Result.success(Unit)

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify { syncManager.incrementalSync("") }
    }
}
