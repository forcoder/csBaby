package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.infrastructure.ota.OtaManager
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class CancelDownloadUseCaseTest {

    private val otaManager: OtaManager = mockk()
    private val useCase = CancelDownloadUseCase(otaManager)

    @Test
    fun `调用 manager cancelDownload`() {
        justRun { otaManager.cancelDownload() }

        useCase()

        verify(exactly = 1) { otaManager.cancelDownload() }
    }

    @Test
    fun `未下载状态调用不抛`() {
        justRun { otaManager.cancelDownload() }

        useCase()

        verify { otaManager.cancelDownload() }
    }

    @Test
    fun `重复调用安全`() {
        justRun { otaManager.cancelDownload() }

        useCase()
        useCase()
        useCase()

        verify(exactly = 3) { otaManager.cancelDownload() }
    }
}