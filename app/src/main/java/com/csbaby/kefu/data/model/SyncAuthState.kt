package com.csbaby.kefu.data.model

/**
 * 同步认证状态。
 * 登录/注册后获得，保存在DataStore中持久化，进程重启后可恢复。
 *
 * 后端登录返回: {userId, tenantId, accessToken, refreshToken, expiresAt}
 */
data class SyncAuthState(
    val userId: String,
    val tenantId: String,  // 后端tenantId用于数据隔离
    val accessToken: String,
    val refreshToken: String = "",
    val expiresAt: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt

    companion object {
        fun fromLoginResponse(
            userId: String,
            tenantId: String,
            token: String,
            refreshToken: String = "",
            expiresAt: Long = 0L
        ): SyncAuthState {
            return SyncAuthState(
                userId = userId,
                tenantId = tenantId.ifEmpty { userId },  // fallback to userId if tenantId empty
                accessToken = token,
                refreshToken = refreshToken,
                expiresAt = if (expiresAt > 0) {
                    expiresAt
                } else {
                    System.currentTimeMillis() + 30 * 24 * 3600 * 1000L  // 默认30天
                }
            )
        }
    }
}
