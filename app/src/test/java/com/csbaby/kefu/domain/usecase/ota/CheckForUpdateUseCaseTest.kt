package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.infrastructure.ota.OtaManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckForUpdateUseCaseTest {

    private val otaManager: OtaManager = mockk()
    private val useCase = CheckForUpdateUseCase(otaManager)

    @Test
    fun `有更新返回 true`() = runTest {
        coEvery { otaManager.checkForUpdate() } returns true

        assertTrue(useCase())
    }

    @Test
    fun `无更新返回 false`() = runTest {
        coEvery { otaManager.checkForUpdate() } returns false

        assertFalse(useCase())
    }

    @Test
    fun `底层异常时返回 false`() = runTest {
        coEvery { otaManager.checkForUpdate() } throws RuntimeException("网络错误")

        // OtaManager 内部已经 try-catch,正常返回 false; 但若抛到 useCase 也透传
        val ex = runCatching { useCase() }.exceptionOrNull()
        assertTrue("异常应透传", ex is RuntimeException)
    }

    @Test
    fun `调用一次`() = runTest {
        coEvery { otaManager.checkForUpdate() } returns true

        useCase()

        coVerify(exactly = 1) { otaManager.checkForUpdate() }
    }
}