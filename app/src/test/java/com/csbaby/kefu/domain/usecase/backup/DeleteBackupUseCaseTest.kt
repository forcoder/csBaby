package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.infrastructure.backup.BackupManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteBackupUseCaseTest {

    private val backupManager: BackupManager = mockk()
    private val useCase = DeleteBackupUseCase(backupManager)

    @Test
    fun `成功删除`() = runTest {
        coEvery { backupManager.deleteBackup(5) } returns Result.success(Unit)

        val result = useCase(5)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `传正确 id`() = runTest {
        coEvery { backupManager.deleteBackup(any()) } returns Result.success(Unit)

        useCase(999)

        coVerify(exactly = 1) { backupManager.deleteBackup(999) }
    }

    @Test
    fun `id=0 (边界) 透传`() = runTest {
        coEvery { backupManager.deleteBackup(0) } returns Result.success(Unit)

        val result = useCase(0)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `不存在 id 返回 failure`() = runTest {
        coEvery { backupManager.deleteBackup(any()) } returns Result.failure(RuntimeException("记录不存在"))

        val result = useCase(404)

        assertTrue(result.isFailure)
    }

    @Test
    fun `网络异常 failure`() = runTest {
        coEvery { backupManager.deleteBackup(any()) } returns Result.failure(RuntimeException("network"))

        val result = useCase(1)

        assertTrue(result.isFailure)
    }
}