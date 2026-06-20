package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.sync.SyncManager
import javax.inject.Inject

/**
 * 注册 UseCase
 */
class RegisterUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(
        identifier: String,
        password: String,
        displayName: String
    ): Result<SyncAuthState> {
        return syncManager.register(identifier, password, displayName)
    }
}
