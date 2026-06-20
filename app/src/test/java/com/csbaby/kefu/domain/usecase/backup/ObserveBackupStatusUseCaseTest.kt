package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.data.model.BackupStatus
import com.csbaby.kefu.infrastructure.backup.BackupManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveBackupStatusUseCaseTest {

    private val backupManager: BackupManager = mockk()
    private val useCase = ObserveBackupStatusUseCase(backupManager)

    @Test
    fun `初始 IDLE`() = runTest {
        every { backupManager.backupStatus } returns MutableStateFlow(BackupStatus.IDLE)

        assertEquals(BackupStatus.IDLE, useCase().first())
    }

    @Test
    fun `多步推进后 first 拿到 SUCCESS`() = runTest {
        val flow = MutableStateFlow(BackupStatus.IDLE)
        every { backupManager.backupStatus } returns flow

        flow.value = BackupStatus.EXPORTING
        flow.value = BackupStatus.UPLOADING
        flow.value = BackupStatus.SUCCESS

        assertEquals(BackupStatus.SUCCESS, useCase().first())
    }

    @Test
    fun `FAILED 后 first 拿到 FAILED`() = runTest {
        val flow = MutableStateFlow(BackupStatus.IDLE)
        every { backupManager.backupStatus } returns flow

        flow.value = BackupStatus.FAILED

        assertEquals(BackupStatus.FAILED, useCase().first())
    }
}