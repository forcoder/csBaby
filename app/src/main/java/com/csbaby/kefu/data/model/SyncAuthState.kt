package com.csbaby.kefu.data.model

/**
 * 同步认证状态。
 * 登录/注册后获得，保存在DataStore中持久化，进程重启后可恢复。
 *
 * 后端登录返回: {user_id, token, expires_in}
 */
data class SyncAuthState(
    val userId: String,
    val tenantId: String,  // 后端user_id作为tenantId用于数据隔离
    val accessToken: String,
    val refreshToken: String = "",  // 简化版：无需refreshToken，重新登录即可
    val expiresAt: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt

    companion object {
        fun fromLoginResponse(userId: String, token: String, expiresIn: Long): SyncAuthState {
            return SyncAuthState(
                userId = userId,
                tenantId = userId,  // userId即tenantId
                accessToken = token,
                refreshToken = "",  // 无refreshToken机制
                expiresAt = if (expiresIn > 0) {
                    System.currentTimeMillis() + expiresIn * 1000
                } else {
                    System.currentTimeMillis() + 30 * 24 * 3600 * 1000L  // 默认30天
                }
            )
        }
    }
}
