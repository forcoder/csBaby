package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.infrastructure.backup.BackupManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreBackupUseCaseTest {

    private val backupManager: BackupManager = mockk()
    private val useCase = RestoreBackupUseCase(backupManager)

    @Test
    fun `成功恢复`() = runTest {
        coEvery { backupManager.downloadAndRestore(42) } returns Result.success(Unit)

        val result = useCase(42)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `传正确 backupId 给 manager`() = runTest {
        coEvery { backupManager.downloadAndRestore(any()) } returns Result.success(Unit)

        useCase(123)

        coVerify(exactly = 1) { backupManager.downloadAndRestore(123) }
    }

    @Test
    fun `id=0 (边界) 透传`() = runTest {
        coEvery { backupManager.downloadAndRestore(0) } returns Result.success(Unit)

        val result = useCase(0)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `负数 id (边界) 透传`() = runTest {
        coEvery { backupManager.downloadAndRestore(-1) } returns Result.failure(IllegalArgumentException("invalid id"))

        val result = useCase(-1)

        assertTrue(result.isFailure)
    }

    @Test
    fun `未登录返回 failure`() = runTest {
        coEvery { backupManager.downloadAndRestore(any()) } returns Result.failure(IllegalStateException("未登录"))

        val result = useCase(1)

        assertTrue(result.isFailure)
    }

    @Test
    fun `下载失败 failure`() = runTest {
        coEvery { backupManager.downloadAndRestore(any()) } returns Result.failure(RuntimeException("下载失败"))

        val result = useCase(1)

        assertTrue(result.isFailure)
        assertEquals("下载失败", result.exceptionOrNull()?.message)
    }
}