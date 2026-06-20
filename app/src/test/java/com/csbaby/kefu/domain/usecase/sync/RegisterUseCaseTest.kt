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

class RegisterUseCaseTest {

    private val syncManager: SyncManager = mockk()
    private val useCase = RegisterUseCase(syncManager)

    private val fakeAuth = SyncAuthState(
        userId = "u2",
        tenantId = "t2",
        accessToken = "tok2",
        refreshToken = "ref2",
        expiresAt = System.currentTimeMillis() + 60_000,
        displayName = "新用户"
    )

    @Test
    fun `正常注册返回 success`() = runTest {
        coEvery { syncManager.register("new@x.com", "pwd", "昵称") } returns Result.success(fakeAuth)

        val result = useCase("new@x.com", "pwd", "昵称")

        assertTrue(result.isSuccess)
        assertEquals("t2", result.getOrNull()?.tenantId)
    }

    @Test
    fun `displayName 含特殊字符 透传`() = runTest {
        coEvery { syncManager.register(any(), any(), any()) } returns Result.success(fakeAuth)

        useCase("a@b.com", "x", "昵称-with_特殊.char")

        coVerify { syncManager.register("a@b.com", "x", "昵称-with_特殊.char") }
    }

    @Test
    fun `调用一次`() = runTest {
        coEvery { syncManager.register(any(), any(), any()) } returns Result.success(fakeAuth)

        useCase("a", "b", "c")

        coVerify(exactly = 1) { syncManager.register("a", "b", "c") }
    }

    @Test
    fun `空 displayName 透传`() = runTest {
        coEvery { syncManager.register("a", "b", "") } returns Result.success(fakeAuth)

        val result = useCase("a", "b", "")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `极长 displayName (边界) 透传`() = runTest {
        val longName = "x".repeat(2000)
        coEvery { syncManager.register("a", "b", longName) } returns Result.success(fakeAuth)

        val result = useCase("a", "b", longName)

        assertTrue(result.isSuccess)
        coVerify { syncManager.register("a", "b", longName) }
    }

    @Test
    fun `服务端返回邮箱已存在 failure`() = runTest {
        coEvery { syncManager.register("dup@x.com", any(), any()) } returns
            Result.failure(IllegalStateException("邮箱已被注册"))

        val result = useCase("dup@x.com", "x", "昵称")

        assertTrue(result.isFailure)
        assertEquals("邮箱已被注册", result.exceptionOrNull()?.message)
    }

    @Test
    fun `网络异常 failure`() = runTest {
        val ex = RuntimeException("timeout")
        coEvery { syncManager.register(any(), any(), any()) } returns Result.failure(ex)

        val result = useCase("a", "b", "c")

        assertTrue(result.isFailure)
        assertEquals(ex, result.exceptionOrNull())
    }
}
