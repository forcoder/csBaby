package com.csbaby.kefu.domain.usecase.auth

import com.csbaby.kefu.data.sync.AuthManager
import javax.inject.Inject

/**
 * 获取当前租户 ID UseCase
 *
 * 内部是 suspend（AuthManager.currentTenantId() 需 IO 加载 DataStore）。
 */
class GetCurrentTenantIdUseCase @Inject constructor(
    private val authManager: AuthManager
) {
    suspend operator fun invoke(): String? = authManager.currentTenantId()
}