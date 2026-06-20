package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.data.model.BackupRecord
import com.csbaby.kefu.infrastructure.backup.BackupManager
import javax.inject.Inject

/**
 * 上传备份 UseCase
 */
class UploadBackupUseCase @Inject constructor(
    private val backupManager: BackupManager
) {
    suspend operator fun invoke(): Result<BackupRecord> = backupManager.uploadBackup()
}