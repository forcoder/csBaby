package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察同步状态 UseCase
 */
class ObserveSyncStateUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    operator fun invoke(): Flow<SyncState> = syncManager.syncState
}
