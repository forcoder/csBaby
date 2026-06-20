package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察同步队列待同步数量 UseCase
 */
class ObserveSyncQueueUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    operator fun invoke(): Flow<Int> = syncManager.queue.pendingCount
}
