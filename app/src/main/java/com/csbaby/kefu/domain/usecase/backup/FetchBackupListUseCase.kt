package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.data.model.BackupRecord
import com.csbaby.kefu.infrastructure.backup.BackupManager
import javax.inject.Inject

/**
 * 拉取备份列表 UseCase
 */
class FetchBackupListUseCase @Inject constructor(
    private val backupManager: BackupManager
) {
    suspend operator fun invoke(): Result<List<BackupRecord>> = backupManager.fetchBackupList()
}