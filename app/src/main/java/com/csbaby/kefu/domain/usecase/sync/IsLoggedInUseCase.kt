package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.sync.SyncManager
import javax.inject.Inject

/**
 * 是否已登录 UseCase
 */
class IsLoggedInUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    operator fun invoke(): Boolean = syncManager.isLoggedIn()
}
