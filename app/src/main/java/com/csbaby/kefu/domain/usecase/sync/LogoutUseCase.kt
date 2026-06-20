package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import javax.inject.Inject

/**
 * 登出 UseCase
 */
class LogoutUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    operator fun invoke() {
        syncManager.logout()
    }
}
