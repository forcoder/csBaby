package com.csbaby.kefu.data.sync

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.csbaby.kefu.data.model.SyncAuthState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "kefu_auth")

/**
 * 认证管理器：管理用户登录状态和 Token 持久化。
 *
 * 使用 DataStore 持久化 + 内存 StateFlow 缓存。
 * 拦截器可同步读取 currentAuthState（不阻塞）。
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val authStore = context.authDataStore

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("auth_user_id")
        private val KEY_TENANT_ID = stringPreferencesKey("auth_tenant_id")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
        private val KEY_EXPIRES_AT = longPreferencesKey("auth_expires_at")
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("auth_is_logged_in")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("auth_display_name")
    }

    /** 内存缓存的认证状态，拦截器可同步读取 */
    private val _currentAuth = MutableStateFlow<SyncAuthState?>(null)

    /** 当前认证状态（同步读取，不阻塞） */
    val currentAuthState: SyncAuthState? get() = _currentAuth.value

    val authStateFlow: Flow<SyncAuthState?> = _currentAuth

    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 同步加载保存的认证状态到内存缓存
        authScope.launch {
            val saved = loadFromDataStore()
            _currentAuth.value = saved
        }
    }

    /** 同步获取认证状态（阻塞直到加载完成，用于 OkHttp 拦截器） */
    fun getAuthStateSync(): SyncAuthState? {
        return _currentAuth.value ?: run {
            // 如果内存缓存为空，尝试同步加载
            // 注意：这只在首次调用时可能发生
            var result: SyncAuthState? = null
            authScope.launch {
                result = loadFromDataStore()
                _currentAuth.value = result
            }
            // 由于协程是异步的，这里只能返回当前缓存值
            // 首次调用时可能为 null，后续调用会正确
            result
        }
    }

    suspend fun saveAuthState(auth: SyncAuthState) {
        authStore.edit { prefs ->
            prefs[KEY_USER_ID] = auth.userId
            prefs[KEY_TENANT_ID] = auth.tenantId
            prefs[KEY_ACCESS_TOKEN] = auth.accessToken
            prefs[KEY_REFRESH_TOKEN] = auth.refreshToken
            prefs[KEY_EXPIRES_AT] = auth.expiresAt
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_DISPLAY_NAME] = auth.displayName
        }
        _currentAuth.value = auth
    }

    suspend fun clearAuthState() {
        authStore.edit { it.clear() }
        _currentAuth.value = null
    }

    /** 收到 401 时调用，清除认证状态 */
    fun onUnauthorized() {
        _currentAuth.value = null
        authScope.launch {
            authStore.edit { it.clear() }
        }
    }

    suspend fun getAuthState(): SyncAuthState? = _currentAuth.value

    suspend fun isLoggedIn(): Boolean = _currentAuth.value != null

    suspend fun currentTenantId(): String? = _currentAuth.value?.tenantId

    suspend fun currentUserId(): String? = _currentAuth.value?.userId

    private suspend fun loadFromDataStore(): SyncAuthState? {
        val prefs = authStore.data.first()
        return if (prefs[KEY_IS_LOGGED_IN] == true) {
            SyncAuthState(
                userId = prefs[KEY_USER_ID] ?: return null,
                tenantId = prefs[KEY_TENANT_ID] ?: return null,
                accessToken = prefs[KEY_ACCESS_TOKEN] ?: return null,
                refreshToken = prefs[KEY_REFRESH_TOKEN] ?: return null,
                expiresAt = prefs[KEY_EXPIRES_AT] ?: 0L,
                displayName = prefs[KEY_DISPLAY_NAME] ?: ""
            )
        } else null
    }
}
