package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.data.model.BackupRecord
import com.csbaby.kefu.infrastructure.backup.BackupManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveBackupRecordsUseCaseTest {

    private val backupManager: BackupManager = mockk()
    private val useCase = ObserveBackupRecordsUseCase(backupManager)

    @Test
    fun `初始空列表`() = runTest {
        every { backupManager.backupRecords } returns MutableStateFlow(emptyList())

        assertEquals(emptyList<BackupRecord>(), useCase().first())
    }

    @Test
    fun `有 2 条记录`() = runTest {
        val records = listOf(BackupRecord(id = 1), BackupRecord(id = 2))
        every { backupManager.backupRecords } returns MutableStateFlow(records)

        val result = useCase().first()

        assertEquals(2, result.size)
        assertEquals(1, result[0].id)
    }

    @Test
    fun `列表更新后 first 拿到最新`() = runTest {
        val flow = MutableStateFlow<List<BackupRecord>>(emptyList())
        every { backupManager.backupRecords } returns flow

        flow.value = listOf(BackupRecord(id = 1))
        flow.value = listOf(BackupRecord(id = 1), BackupRecord(id = 2))

        val result = useCase().first()
        assertEquals(2, result.size)
    }
}