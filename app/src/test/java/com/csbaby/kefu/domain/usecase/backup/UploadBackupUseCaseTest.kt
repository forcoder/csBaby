package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.data.model.BackupRecord
import com.csbaby.kefu.infrastructure.backup.BackupManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadBackupUseCaseTest {

    private val backupManager: BackupManager = mockk()
    private val useCase = UploadBackupUseCase(backupManager)

    private val fakeRecord = BackupRecord(id = 100, deviceName = "Pixel 7", appVersion = "1.0.0", dataSize = 1234)

    @Test
    fun `成功上传返回 record`() = runTest {
        coEvery { backupManager.uploadBackup() } returns Result.success(fakeRecord)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(100, result.getOrNull()?.id)
        assertEquals("Pixel 7", result.getOrNull()?.deviceName)
    }

    @Test
    fun `调用一次 uploadBackup`() = runTest {
        coEvery { backupManager.uploadBackup() } returns Result.success(fakeRecord)

        useCase()

        coVerify(exactly = 1) { backupManager.uploadBackup() }
    }

    @Test
    fun `未登录返回 failure`() = runTest {
        coEvery { backupManager.uploadBackup() } returns Result.failure(IllegalStateException("未登录"))

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals("未登录", result.exceptionOrNull()?.message)
    }

    @Test
    fun `网络异常返回 failure`() = runTest {
        val ex = RuntimeException("network")
        coEvery { backupManager.uploadBackup() } returns Result.failure(ex)

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals(ex, result.exceptionOrNull())
    }

    @Test
    fun `服务端错误返回 failure`() = runTest {
        coEvery { backupManager.uploadBackup() } returns Result.failure(RuntimeException("服务器返回 500"))

        val result = useCase()

        assertTrue(result.isFailure)
    }

    @Test
    fun `本地无数据时上传返回 0 字节的 record (边界)`() = runTest {
        val empty = fakeRecord.copy(dataSize = 0)
        coEvery { backupManager.uploadBackup() } returns Result.success(empty)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0L, result.getOrNull()?.dataSize)
    }

    @Test
    fun `极小 id (边界) 返回`() = runTest {
        val rec = fakeRecord.copy(id = 1)
        coEvery { backupManager.uploadBackup() } returns Result.success(rec)

        val result = useCase()

        assertEquals(1, result.getOrNull()?.id)
    }
}