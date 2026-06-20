package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.infrastructure.backup.BackupManager
import javax.inject.Inject

/**
 * 下载并恢复备份 UseCase
 */
class RestoreBackupUseCase @Inject constructor(
    private val backupManager: BackupManager
) {
    suspend operator fun invoke(backupId: Int): Result<Unit> = backupManager.downloadAndRestore(backupId)
}