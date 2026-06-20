package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.infrastructure.ota.OtaManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartDownloadUpdateUseCaseTest {

    private val otaManager: OtaManager = mockk()
    private val useCase = StartDownloadUpdateUseCase(otaManager)

    private val fakeUpdate = OtaUpdate(
        versionCode = 2, versionName = "1.1.0",
        downloadUrl = "https://example.com/update.apk",
        fileSize = 1024L, md5 = "abc", releaseNotes = "fix", releaseDate = "2026-06-20"
    )

    @Test
    fun `成功开始下载返回 true`() {
        every { otaManager.startDownload(fakeUpdate) } returns true

        assertTrue(useCase(fakeUpdate))
    }

    @Test
    fun `启动下载失败返回 false`() {
        every { otaManager.startDownload(any()) } returns false

        assertFalse(useCase(fakeUpdate))
    }

    @Test
    fun `传正确 update 给 manager`() {
        every { otaManager.startDownload(any()) } returns true

        useCase(fakeUpdate)

        verify(exactly = 1) { otaManager.startDownload(fakeUpdate) }
    }

    @Test
    fun `force update (边界) 也透传`() {
        val force = fakeUpdate.copy(isForceUpdate = true)
        every { otaManager.startDownload(force) } returns true

        assertTrue(useCase(force))
    }
}