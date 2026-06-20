package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LoginUseCaseTest {

    private val syncManager: SyncManager = mockk()
    private val useCase = LoginUseCase(syncManager)

    private val fakeAuth = SyncAuthState(
        userId = "u1",
        tenantId = "t1",
        accessToken = "tok",
        refreshToken = "ref",
        expiresAt = System.currentTimeMillis() + 60_000,
        displayName = "user@example.com"
    )

    // ========== 正常场景 (≥3) ==========

    @Test
    fun `正常邮箱登录返回 success`() = runTest {
        coEvery { syncManager.login("user@example.com", "pwd123") } returns Result.success(fakeAuth)

        val result = useCase("user@example.com", "pwd123")

        assertTrue(result.isSuccess)
        assertEquals("t1", result.getOrNull()?.tenantId)
    }

    @Test
    fun `正常手机号登录返回 success`() = runTest {
        coEvery { syncManager.login("13800000000", "pwd") } returns Result.success(
            fakeAuth.copy(displayName = "13800000000")
        )

        val result = useCase("13800000000", "pwd")

        assertTrue(result.isSuccess)
        assertEquals("13800000000", result.getOrNull()?.displayName)
    }

    @Test
    fun `调用一次 syncManager_login`() = runTest {
        coEvery { syncManager.login(any(), any()) } returns Result.success(fakeAuth)

        useCase("a@b.com", "x")

        coVerify(exactly = 1) { syncManager.login("a@b.com", "x") }
    }

    // ========== 边界值 (≥2) ==========

    @Test
    fun `空字符串 identifier 透传`() = runTest {
        coEvery { syncManager.login("", "x") } returns Result.success(fakeAuth)

        val result = useCase("", "x")

        // UseCase 不做校验，由 SyncManager 决定
        assertTrue(result.isSuccess)
    }

    @Test
    fun `极长 identifier (边界) 透传`() = runTest {
        val longId = "a".repeat(3000)
        coEvery { syncManager.login(longId, "x") } returns Result.success(fakeAuth)

        val result = useCase(longId, "x")

        assertTrue(result.isSuccess)
        coVerify { syncManager.login(longId, "x") }
    }

    // ========== 异常场景 (≥2) ==========

    @Test
    fun `网络异常返回 failure`() = runTest {
        val ex = IOException("network down")
        coEvery { syncManager.login(any(), any()) } returns Result.failure(ex)

        val result = useCase("a@b.com", "x")

        assertTrue(result.isFailure)
        assertEquals(ex, result.exceptionOrNull())
    }

    @Test
    fun `凭据错误 (后端返回 401) 返回 failure`() = runTest {
        val ex = RuntimeException("凭据无效")
        coEvery { syncManager.login("a@b.com", "wrong") } returns Result.failure(ex)

        val result = useCase("a@b.com", "wrong")

        assertTrue(result.isFailure)
        assertEquals("凭据无效", result.exceptionOrNull()?.message)
    }
}
