package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察上次同步时间 UseCase
 */
class ObserveLastSyncTimeUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    operator fun invoke(): Flow<Long> = syncManager.lastSyncTime
}
