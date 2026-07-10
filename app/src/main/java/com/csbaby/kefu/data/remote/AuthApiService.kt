package com.csbaby.kefu.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 主 API 鉴权服务 (api.agentai0.com).
 *
 * 与数据同步 SyncApiService 拆分：
 * - 鉴权: BASE_URL = API_BASE_URL (主 API)
 *   端点: /api/auth/user/{login,register}
 *   支持 phone 或 email 双字段登录
 * - 数据同步: BASE_URL = API_BASE_URL (统一主 API 域名)
 *   端点: /sync/all, /sync/changes, /sync/push
 *
 * 响应格式: 成功时直接返回 {user_id, token, expires_in} (无包装),
 * 失败时返回 {error: "..."}。LoginResponse 兼容两种形态。
 */
interface AuthApiService {

    @POST("api/auth/user/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/user/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse
}

// 登录请求 — 支持 phone 或 email (auto-detect by '@' in identifier)
data class LoginRequest(
    @SerializedName("identifier") val identifier: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("password") val password: String
)

// 注册请求 — 支持 phone 或 email, name 可选
data class RegisterRequest(
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("password") val password: String,
    @SerializedName("name") val name: String? = null
)

// 响应 — 兼容主 API (user_id/token) 与旧 sync API (userId/accessToken)
data class LoginResponse(
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("userId") val userIdAlt: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("tenantId") val tenantId: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("refreshToken") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long? = null,
    @SerializedName("expiresAt") val expiresAt: Long? = null,
    @SerializedName("error") val error: String? = null
) {
    fun effectiveUserId(): String = userId ?: userIdAlt ?: ""
    fun effectiveToken(): String = token ?: accessToken ?: ""
    fun effectiveTenantId(): String = tenantId ?: effectiveUserId()
    fun isSuccess(): Boolean = effectiveToken().isNotEmpty() && error == null
    fun errorMessage(): String? = error
}
