package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import javax.inject.Inject

/**
 * 立即同步 UseCase（增量同步）
 *
 * 内部依赖 GetCurrentTenantIdUseCase 获取当前租户，未登录时返回 failure("未登录")。
 */
class SyncNowUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(): Result<Unit> {
        val tenantId = syncManager.currentTenantId()
            ?: return Result.failure(IllegalStateException("未登录，无法同步"))
        return syncManager.incrementalSync(tenantId)
    }
}
