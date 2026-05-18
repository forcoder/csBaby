package com.csbaby.kefu.data.model

/**
 * 同步认证状态。
 * 登录/注册后获得，保存在内存中（进程重启后需重新登录，或配合 DataStore 持久化）。
 */
data class SyncAuthState(
    val userId: String,
    val tenantId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt
}
