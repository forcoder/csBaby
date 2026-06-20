package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.infrastructure.ota.OtaManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveOtaStatusUseCaseTest {

    private val otaManager: OtaManager = mockk()
    private val useCase = ObserveOtaStatusUseCase(otaManager)

    @Test
    fun `初始 IDLE`() = runTest {
        every { otaManager.updateStatus } returns MutableStateFlow(UpdateStatus.IDLE)

        assertEquals(UpdateStatus.IDLE, useCase().first())
    }

    @Test
    fun `CHECKING 状态透传`() = runTest {
        every { otaManager.updateStatus } returns MutableStateFlow(UpdateStatus.CHECKING)

        assertEquals(UpdateStatus.CHECKING, useCase().first())
    }

    @Test
    fun `FAILED 状态透传`() = runTest {
        every { otaManager.updateStatus } returns MutableStateFlow(UpdateStatus.FAILED)

        assertEquals(UpdateStatus.FAILED, useCase().first())
    }
}