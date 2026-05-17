package com.csbaby.kefu.data.remote

import android.provider.Settings
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.remote.dto.RegisterRequest
import com.csbaby.kefu.data.remote.dto.RegisterResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户认证与 Token 管理
 * 支持用户登录/注册，多设备共享数据
 */
@Singleton
class UserAuthManager @Inject constructor(
    private val apiService: CsbabyApiService,
    private val preferencesManager: PreferencesManager,
    private val authInterceptor: AuthInterceptor
) {
    private val mutex = Mutex()

    /**
     * 检查用户是否已登录，返回有效的 token 或 userId
     * 如果本地已有 token 则直接返回，否则需要用户登录
     */
    suspend fun ensureAuthenticated(): Pair<String?, String?> {
        val existingToken = preferencesManager.authTokenFlow.first()
        if (existingToken.isNotBlank()) {
            val userId = preferencesManager.userPreferencesFlow.first().userId
            return Pair(existingToken, userId)
        }

        val currentUserId = preferencesManager.userPreferencesFlow.first().currentUserId
        if (currentUserId.isNotBlank() && currentUserId != "default_user") {
            val token = preferencesManager.authTokenFlow.first()
            return Pair(token, currentUserId)
        }

        return Pair(null, null) // Need to login
    }

    /**
     * 用户登录
     */
    suspend fun login(phone: String, password: String): AuthResponse {
        val response = apiService.userLogin(UserLoginRequest(phone, password))
        preferencesManager.saveAuthToken(response.token)
        preferencesManager.updateCurrentUserId(response.userId)
        preferencesManager.saveUserId(response.userId)
        authInterceptor.updateToken(response.token)
        return response
    }

    /**
     * 用户注册
     */
    suspend fun register(phone: String, password: String, name: String = ""): AuthResponse {
        val response = apiService.userRegister(UserRegisterRequest(phone, password, name))
        preferencesManager.saveAuthToken(response.token)
        preferencesManager.updateCurrentUserId(response.userId)
        preferencesManager.saveUserId(response.userId)
        authInterceptor.updateToken(response.token)
        return response
    }

    /**
     * 修改密码
     */
    suspend fun changePassword(oldPassword: String, newPassword: String): ChangePasswordResponse {
        return apiService.changePassword(ChangePasswordRequest(oldPassword, newPassword))
    }

    /**
     * 登出
     */
    suspend fun logout() {
        preferencesManager.clearAllAuthData()
        authInterceptor.clearToken()
    }

    /**
     * 发送心跳
     */
    suspend fun heartbeat() {
        try {
            apiService.heartbeat()
        } catch (e: Exception) {
            Timber.w(e, "Heartbeat failed")
        }
    }

    private suspend fun registerDevice(): String {
        val request = RegisterRequest(
            platform = "android",
            appVersion = getAppVersion(),
            name = getDeviceName()
        )
        try {
            val response: RegisterResponse = apiService.register(request)
            preferencesManager.saveAuthToken(response.token)
            preferencesManager.saveDeviceId(response.deviceId)
            authInterceptor.updateToken(response.token)
            Timber.i("Device registered: ${response.deviceId}")
            return response.token
        } catch (e: Exception) {
            Timber.e(e, "Device registration failed")
            throw e
        }
    }

    /**
     * 注册设备并绑定到用户（用于新用户首次使用）
     */
    suspend fun registerDeviceForUser(userId: String): String {
        val request = RegisterRequest(
            platform = "android",
            appVersion = getAppVersion(),
            name = getDeviceName()
        )
        try {
            val response: RegisterResponse = apiService.register(request)
            // 保存设备信息到 user_devices 表，这里需要后端支持
            preferencesManager.saveAuthToken(response.token)
            preferencesManager.saveDeviceId(response.deviceId)
            authInterceptor.updateToken(response.token)
            Timber.i("Device registered for user $userId: ${response.deviceId}")
            return response.token
        } catch (e: Exception) {
            Timber.e(e, "Device registration for user failed")
            throw e
        }
    }

    private fun getAppVersion(): String {
        return try {
            com.csbaby.kefu.BuildConfig.VERSION_NAME ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun getDeviceName(): String {
        return try {
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        } catch (e: Exception) {
            "Android Device"
        }
    }
}
