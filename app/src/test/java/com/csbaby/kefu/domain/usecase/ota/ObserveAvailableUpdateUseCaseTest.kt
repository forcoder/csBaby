package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.infrastructure.ota.OtaManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveAvailableUpdateUseCaseTest {

    private val otaManager: OtaManager = mockk()
    private val useCase = ObserveAvailableUpdateUseCase(otaManager)

    private val fakeUpdate = OtaUpdate(
        versionCode = 2, versionName = "1.1.0",
        downloadUrl = "https://example.com/update.apk",
        fileSize = 1024L, md5 = "abc", releaseNotes = "fix", releaseDate = "2026-06-20"
    )

    @Test
    fun `初始 null`() = runTest {
        every { otaManager.availableUpdate } returns MutableStateFlow(null)

        assertNull(useCase().first())
    }

    @Test
    fun `有可用更新`() = runTest {
        every { otaManager.availableUpdate } returns MutableStateFlow(fakeUpdate)

        val result = useCase().first()
        assertNotNull(result)
        assertEquals(2, result?.versionCode)
    }

    @Test
    fun `更新值变化 first 拿最新`() = runTest {
        val flow = MutableStateFlow<OtaUpdate?>(null)
        every { otaManager.availableUpdate } returns flow

        flow.value = fakeUpdate

        val result = useCase().first()
        assertEquals(2, result?.versionCode)
    }
}