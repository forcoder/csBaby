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

class FetchBackupListUseCaseTest {

    private val backupManager: BackupManager = mockk()
    private val useCase = FetchBackupListUseCase(backupManager)

    private val fakeList = listOf(
        BackupRecord(id = 1, deviceName = "dev1"),
        BackupRecord(id = 2, deviceName = "dev2")
    )

    @Test
    fun `成功返回列表`() = runTest {
        coEvery { backupManager.fetchBackupList() } returns Result.success(fakeList)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `空列表返回 (边界)`() = runTest {
        coEvery { backupManager.fetchBackupList() } returns Result.success(emptyList())

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
    }

    @Test
    fun `调用一次`() = runTest {
        coEvery { backupManager.fetchBackupList() } returns Result.success(fakeList)

        useCase()

        coVerify(exactly = 1) { backupManager.fetchBackupList() }
    }

    @Test
    fun `网络异常 failure`() = runTest {
        coEvery { backupManager.fetchBackupList() } returns Result.failure(RuntimeException("timeout"))

        val result = useCase()

        assertTrue(result.isFailure)
    }

    @Test
    fun `服务端失败 failure`() = runTest {
        coEvery { backupManager.fetchBackupList() } returns Result.failure(RuntimeException("获取备份列表失败"))

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals("获取备份列表失败", result.exceptionOrNull()?.message)
    }
}