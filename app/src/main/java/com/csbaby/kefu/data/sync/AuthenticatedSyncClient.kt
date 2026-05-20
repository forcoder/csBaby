package com.csbaby.kefu.data.sync

import android.util.Log
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

class AuthenticatedSyncClient(
    private val authManager: AuthManager
) {
    val apiService: SyncApiService

    @Volatile
    private var isRefreshing = false

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = authManager.currentAuthState?.accessToken
            Log.d("AuthInterceptor", "url=${original.url}, hasToken=${token != null}")
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

    private fun refreshTokenBlocking(refreshToken: String): SyncAuthState? {
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
            val response = runBlocking { tempApi.refreshToken(RefreshTokenRequest(refreshToken)) }
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
