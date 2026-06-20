package com.csbaby.kefu.domain.usecase.auth

import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.sync.AuthManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveAuthStateUseCaseTest {

    private val authManager: AuthManager = mockk()
    private val useCase = ObserveAuthStateUseCase(authManager)

    private val fakeAuth = SyncAuthState(
        userId = "u1", tenantId = "t1", accessToken = "tok",
        refreshToken = "ref", expiresAt = 9999999999999L, displayName = "张三"
    )

    @Test
    fun `未登录时发射 null`() = runTest {
        every { authManager.authStateFlow } returns MutableStateFlow(null)

        assertNull(useCase().first())
    }

    @Test
    fun `已登录时发射 SyncAuthState`() = runTest {
        every { authManager.authStateFlow } returns MutableStateFlow(fakeAuth)

        val result = useCase().first()

        assertEquals("t1", result?.tenantId)
        assertEquals("张三", result?.displayName)
    }

    @Test
    fun `登录后 first 拿到登录态`() = runTest {
        val flow = MutableStateFlow<SyncAuthState?>(null)
        every { authManager.authStateFlow } returns flow

        flow.value = fakeAuth

        val result = useCase().first()
        assertEquals("t1", result?.tenantId)
    }

    @Test
    fun `登出后 first 拿回 null`() = runTest {
        val flow = MutableStateFlow<SyncAuthState?>(fakeAuth)
        every { authManager.authStateFlow } returns flow

        flow.value = null

        assertNull(useCase().first())
    }
}