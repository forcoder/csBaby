package com.csbaby.kefu.data.sync

import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.remote.RefreshTokenRequest
import com.csbaby.kefu.data.remote.SyncApiService
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 创建带 JWT 认证的 SyncApiService 客户端。
 *
 * 认证策略：
 * - 从 AuthManager 读取运行时 accessToken（内存缓存，非 suspend 调用）
 * - Token 通过 Authorization: Bearer <token> 头发送
 * - 未登录时不带认证头，服务端应返回 401
 * - 收到 401 后尝试用 refreshToken 刷新，刷新成功则重试请求
 * - 刷新失败则清除本地认证状态
 *
 * 不使用任何硬编码的 API Key。
 */
class AuthenticatedSyncClient(
    private val authManager: AuthManager
) {
    val apiService: SyncApiService

    @Volatile
    private var isRefreshing = false

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = authManager.currentAuthState?.accessToken
            val request = if (token != null) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }

        val unauthorizedInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 401) {
                val currentAuth = authManager.currentAuthState
                if (currentAuth != null && !isRefreshing) {
                    synchronized(this) {
                        if (!isRefreshing) {
                            isRefreshing = true
                            try {
                                val newAuth = refreshTokenSync(currentAuth.refreshToken)
                                if (newAuth != null) {
                                    authManager.saveAuthState(newAuth)
                                    // 用新 token 重试请求
                                    response.close()
                                    val retryRequest = chain.request().newBuilder()
                                        .header("Authorization", "Bearer ${newAuth.accessToken}")
                                        .build()
                                    return@Interceptor chain.proceed(retryRequest)
                                }
                            } catch (_: Exception) {
                            } finally {
                                isRefreshing = false
                            }
                        }
                    }
                }
                authManager.onUnauthorized()
            }
            response
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(unauthorizedInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.SYNC_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(SyncApiService::class.java)
    }

    private fun refreshTokenSync(refreshToken: String): SyncAuthState? {
        return try {
            val tempClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val tempRetrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.SYNC_BASE_URL)
                .client(tempClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val tempApi = tempRetrofit.create(SyncApiService::class.java)
            val response = tempApi.refreshToken(RefreshTokenRequest(refreshToken))
            if (response.isSuccess && response.data != null) {
                SyncAuthState(
                    userId = response.data.userId,
                    tenantId = response.data.tenantId,
                    accessToken = response.data.accessToken,
                    refreshToken = response.data.refreshToken,
                    expiresAt = response.data.expiresAt
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
