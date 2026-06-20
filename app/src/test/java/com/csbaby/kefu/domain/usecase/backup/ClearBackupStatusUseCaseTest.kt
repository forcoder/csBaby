package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.infrastructure.backup.BackupManager
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ClearBackupStatusUseCaseTest {

    private val backupManager: BackupManager = mockk()
    private val useCase = ClearBackupStatusUseCase(backupManager)

    @Test
    fun `调用 manager clearStatus`() {
        justRun { backupManager.clearStatus() }

        useCase()

        verify(exactly = 1) { backupManager.clearStatus() }
    }

    @Test
    fun `重复调用安全`() {
        justRun { backupManager.clearStatus() }

        useCase()
        useCase()

        verify(exactly = 2) { backupManager.clearStatus() }
    }

    @Test
    fun `未初始化状态调用不抛`() {
        justRun { backupManager.clearStatus() }

        useCase() // 不抛

        verify { backupManager.clearStatus() }
    }
}