package com.csbaby.kefu.data.sync

import android.util.Log
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.model.SyncAuthState
import com.csbaby.kefu.data.remote.ApiResponse
import com.csbaby.kefu.data.remote.AuthApiService
import com.csbaby.kefu.data.remote.AuthResult
import com.csbaby.kefu.data.remote.RefreshTokenRequest
import com.csbaby.kefu.data.remote.SyncApiService
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AuthenticatedSyncClient(
    private val authManager: AuthManager
) {
    val apiService: SyncApiService
    val authApiService: AuthApiService

    /** 用于 Token 刷新的 API Service（不经过认证拦截器） */
    val refreshApiService: SyncApiService

    @Volatile
    private var isRefreshing = false

    // 用于 Token 刷新的独立 Retrofit 实例（不经过认证拦截器）
    private val refreshRetrofit: Retrofit

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        }

        // 用于刷新 Token 的 OkHttpClient（无认证拦截器）
        val refreshClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        refreshRetrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(refreshClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        refreshApiService = refreshRetrofit.create(SyncApiService::class.java)

        // 主 API 认证 Retrofit (api.agentai0.com) - 用于登录/注册
        val authRetrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(refreshClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        authApiService = authRetrofit.create(AuthApiService::class.java)

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val path = original.url.encodedPath

            // 跳过不需要认证的接口（登录/注册/刷新token）
            if (path.contains("auth/login") || path.contains("auth/register") || path.contains("auth/refresh")) {
                Log.d("AuthInterceptor", "Skip auth for: $path")
                return@Interceptor chain.proceed(original)
            }

            // 使用 getAuthStateSync 确保获取最新状态
            val authState = authManager.getAuthStateSync()
            val token = authState?.accessToken
            Log.d("AuthInterceptor", "url=$path, hasToken=${token != null}, authState=${authState != null}")

            if (token != null) {
                val request = original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                Log.d("AuthInterceptor", "Added Bearer token for: $path")
                chain.proceed(request)
            } else {
                Log.w("AuthInterceptor", "No token available for: $path - proceeding without auth")
                chain.proceed(original)
            }
        }

        val unauthorizedInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            Log.d("AuthInterceptor", "responseCode=${response.code}, url=${response.request.url}")
            if (response.code == 401) {
                val currentAuth = authManager.currentAuthState
                Log.d("AuthInterceptor", "401 received, hasAuth=${currentAuth != null}, isRefreshing=$isRefreshing")
                if (currentAuth != null && !isRefreshing) {
                    synchronized(this) {
                        if (!isRefreshing) {
                            isRefreshing = true
                            try {
                                val newAuth = refreshTokenBlocking(currentAuth.refreshToken)
                                if (newAuth != null) {
                                    runBlocking { authManager.saveAuthState(newAuth) }
                                    response.close()
                                    val retryRequest = chain.request().newBuilder()
                                        .header("Authorization", "Bearer ${newAuth.accessToken}")
                                        .build()
                                    return@Interceptor chain.proceed(retryRequest)
                                }
                            } catch (e: Exception) {
                                Log.w("AuthSync", "Token 刷新失败，尝试使用原 Token", e)
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
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(SyncApiService::class.java)
    }

    /**
     * 使用 refreshToken 调用后端 /auth/refresh 接口获取新的 accessToken。
     * 该方法在 401 响应时由 OkHttp 拦截器同步调用。
     */
    private fun refreshTokenBlocking(refreshToken: String): SyncAuthState? {
        Log.d("AuthenticatedSyncClient", "refreshTokenBlocking() 开始刷新")
        return runBlocking {
            try {
                val response = refreshApiService.refreshToken(RefreshTokenRequest(refreshToken))
                Log.d("AuthenticatedSyncClient", "refreshToken 响应: isSuccess=${response.isSuccess}, msg=${response.message}")
                if (response.isSuccess && response.data != null) {
                    val data = response.data
                    SyncAuthState.fromLoginResponse(
                        userId = data.userId,
                        tenantId = data.tenantId.ifEmpty { data.userId },
                        token = data.effectiveAccessToken(),
                        refreshToken = data.refreshToken,
                        expiresAt = data.expiresAt
                    ).also {
                        Log.d("AuthenticatedSyncClient", "刷新成功: token=${it.accessToken.take(20)}...")
                    }
                } else {
                    Log.w("AuthenticatedSyncClient", "刷新失败: ${response.message}")
                    null
                }
            } catch (e: Exception) {
                Log.e("AuthenticatedSyncClient", "刷新异常", e)
                null
            }
        }
    }
}
