package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.infrastructure.backup.BackupManager
import javax.inject.Inject

/**
 * 清除备份状态 UseCase
 */
class ClearBackupStatusUseCase @Inject constructor(
    private val backupManager: BackupManager
) {
    operator fun invoke() {
        backupManager.clearStatus()
    }
}