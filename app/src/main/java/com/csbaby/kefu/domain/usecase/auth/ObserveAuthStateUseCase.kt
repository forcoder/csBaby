package com.csbaby.kefu.domain.usecase.auth

import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.sync.AuthManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察认证状态 UseCase
 */
class ObserveAuthStateUseCase @Inject constructor(
    private val authManager: AuthManager
) {
    operator fun invoke(): Flow<SyncAuthState?> = authManager.authStateFlow
}
