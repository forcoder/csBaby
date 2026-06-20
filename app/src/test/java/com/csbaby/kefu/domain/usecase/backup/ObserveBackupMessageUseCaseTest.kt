package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.infrastructure.backup.BackupManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveBackupMessageUseCaseTest {

    private val backupManager: BackupManager = mockk()
    private val useCase = ObserveBackupMessageUseCase(backupManager)

    @Test
    fun `初始空串`() = runTest {
        every { backupManager.backupMessage } returns MutableStateFlow("")

        assertEquals("", useCase().first())
    }

    @Test
    fun `导出中消息`() = runTest {
        every { backupManager.backupMessage } returns MutableStateFlow("正在导出本地数据...")

        assertEquals("正在导出本地数据...", useCase().first())
    }

    @Test
    fun `消息更新后 first 拿到最新`() = runTest {
        val flow = MutableStateFlow("")
        every { backupManager.backupMessage } returns flow

        flow.value = "step1"
        flow.value = "step2"

        assertEquals("step2", useCase().first())
    }
}