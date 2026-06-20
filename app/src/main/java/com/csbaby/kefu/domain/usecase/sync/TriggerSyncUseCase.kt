package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import javax.inject.Inject

/**
 * 触发后台同步 UseCase
 *
 * 封装 SyncManager.triggerSync() 的"debounce + 自动判断 tenantId"逻辑。
 * ViewModel 直接调用，无需关心是否已登录。
 */
class TriggerSyncUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    operator fun invoke() {
        syncManager.triggerSync()
    }
}
