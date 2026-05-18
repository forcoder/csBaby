package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.local.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动附加 JWT Token 到请求头的拦截器
 * 使用内存缓存的 token 避免在 OkHot interceptor 中阻塞
 * 收到 401 时清除本地 token 触发重新注册
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val preferencesManager: PreferencesManager
) : Interceptor {

    @Volatile
    private var cachedToken: String? = null

    init {
        runBlocking {
            cachedToken = preferencesManager.authTokenFlow.first()
        }
    }

    fun updateToken(token: String) {
        cachedToken = token
    }

    fun clearToken() {
        cachedToken = null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = cachedToken

        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)

        if (response.code == 401) {
            clearToken()
            runBlocking { preferencesManager.clearAuthToken() }
        }

        return response
    }
}
