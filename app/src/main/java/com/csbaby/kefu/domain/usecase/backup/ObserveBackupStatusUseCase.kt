package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.data.model.BackupStatus
import com.csbaby.kefu.infrastructure.backup.BackupManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBackupStatusUseCase @Inject constructor(
    private val backupManager: BackupManager
) {
    operator fun invoke(): Flow<BackupStatus> = backupManager.backupStatus
}