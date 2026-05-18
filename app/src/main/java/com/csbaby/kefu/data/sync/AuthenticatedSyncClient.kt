package com.csbaby.kefu.data.sync

import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.remote.SyncApiService
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
 * - 收到 401 后清除本地认证状态
 *
 * 不使用任何硬编码的 API Key。
 */
class AuthenticatedSyncClient(
    private val authManager: AuthManager
) {
    val apiService: SyncApiService

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        // JWT 认证拦截器：从 AuthManager 读取当前 Token
        // 注意：拦截器同步执行，读取 authState 的当前值（不阻塞）
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            // 读取当前 authState（StateFlow 的 value 属性，同步非阻塞）
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

        // 401 处理拦截器：Token 过期时标记需要重新登录
        val unauthorizedInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 401) {
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
}
