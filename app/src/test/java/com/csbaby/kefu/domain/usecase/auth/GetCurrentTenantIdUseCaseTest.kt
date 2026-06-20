package com.csbaby.kefu.domain.usecase.auth

import com.csbaby.kefu.data.sync.AuthManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetCurrentTenantIdUseCaseTest {

    private val authManager: AuthManager = mockk()
    private val useCase = GetCurrentTenantIdUseCase(authManager)

    @Test
    fun `已登录返回 tenantId`() = runTest {
        coEvery { authManager.currentTenantId() } returns "tenant_abc"

        assertEquals("tenant_abc", useCase())
    }

    @Test
    fun `未登录返回 null`() = runTest {
        coEvery { authManager.currentTenantId() } returns null

        assertNull(useCase())
    }

    @Test
    fun `空字符串 (边界) 视为已登录但无 tenantId`() = runTest {
        // 与 SyncNowUseCase 不同，这里透传由调用方决定
        coEvery { authManager.currentTenantId() } returns ""

        assertEquals("", useCase())
    }
}