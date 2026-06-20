package com.csbaby.kefu.domain.usecase.backup

import com.csbaby.kefu.infrastructure.backup.BackupManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBackupMessageUseCase @Inject constructor(
    private val backupManager: BackupManager
) {
    operator fun invoke(): Flow<String> = backupManager.backupMessage
}