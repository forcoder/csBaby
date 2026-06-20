package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.infrastructure.backup.BackupManager
import javax.inject.Inject

/**
 * 删除服务端备份 UseCase
 */
class DeleteBackupUseCase @Inject constructor(
    private val backupManager: BackupManager
) {
    suspend operator fun invoke(backupId: Int): Result<Unit> = backupManager.deleteBackup(backupId)
}