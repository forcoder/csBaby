package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.infrastructure.ota.OtaManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveOtaErrorUseCaseTest {

    private val otaManager: OtaManager = mockk()
    private val useCase = ObserveOtaErrorUseCase(otaManager)

    @Test
    fun `初始 null`() = runTest {
        every { otaManager.errorMessage } returns MutableStateFlow(null)

        assertNull(useCase().first())
    }

    @Test
    fun `错误消息透传`() = runTest {
        every { otaManager.errorMessage } returns MutableStateFlow("检查更新失败: timeout")

        assertEquals("检查更新失败: timeout", useCase().first())
    }

    @Test
    fun `错误清空后 first 拿 null`() = runTest {
        val flow = MutableStateFlow<String?>("old error")
        every { otaManager.errorMessage } returns flow

        flow.value = null

        assertNull(useCase().first())
    }
}