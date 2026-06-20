package com.csbaby.kefu.domain.usecase.sync

import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.sync.SyncManager
import javax.inject.Inject

/**
 * 登录 UseCase
 *
 * 封装 SyncManager.login()，切断 UI 层对 data.sync 的直接依赖。
 * 失败时返回 Result.failure，调用方应通过 .fold 处理。
 *
 * @param syncManager 同步管理器（来自 data.sync 内部实现）
 */
class LoginUseCase @Inject constructor(
    private val syncManager: SyncManager
) {
    /**
     * @param identifier 手机号或邮箱（取决于后端 auto-detect）
     * @param password 密码
     * @return Result.success(SyncAuthState) 登录成功；Result.failure 登录失败（网络/凭据错误）
     */
    suspend operator fun invoke(identifier: String, password: String): Result<SyncAuthState> {
        return syncManager.login(identifier, password)
    }
}
