package com.csbaby.kefu.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kefu_settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
        const val BAIJUYI_PACKAGE = "com.myhostex.hostexapp"
        const val MEITUAN_MINSU_PACKAGE = "com.meituan.phoenix"
        const val TUJIA_MINSU_PACKAGE = "com.tujia.hotel"
        val DEFAULT_MONITORED_APPS = setOf(
            WECHAT_PACKAGE,
            BAIJUYI_PACKAGE,
            MEITUAN_MINSU_PACKAGE,
            TUJIA_MINSU_PACKAGE
        )
    }



    private val dataStore = context.dataStore


    // Keys
    private object PreferencesKeys {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val FLOATING_WINDOW_ENABLED = booleanPreferencesKey("floating_window_enabled")
        val FLOATING_ICON_ENABLED = booleanPreferencesKey("floating_icon_enabled")
        val SELECTED_APPS = stringSetPreferencesKey("selected_apps")
        val DEFAULT_MODEL_ID = longPreferencesKey("default_model_id")
        val STYLE_LEARNING_ENABLED = booleanPreferencesKey("style_learning_enabled")
        val AUTO_SEND_ENABLED = booleanPreferencesKey("auto_send_enabled")
        val CURRENT_USER_ID = stringPreferencesKey("current_user_id")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
        val OVERLAY_PERMISSION_ASKED = booleanPreferencesKey("overlay_permission_asked")
        // Hybrid search settings
        val SEMANTIC_SEARCH_ENABLED = booleanPreferencesKey("semantic_search_enabled")
        val SEARCH_MODE = stringPreferencesKey("search_mode") // KEYWORD, SEMANTIC, HYBRID
        // Theme settings
        val THEME_MODE = stringPreferencesKey("theme_mode") // light, dark, system
        // Auth / Device
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_ID = stringPreferencesKey("user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
    }

    // Data class for user preferences
    data class UserPreferences(
        val monitoringEnabled: Boolean = true,
        val floatingWindowEnabled: Boolean = true,
        val floatingIconEnabled: Boolean = false,
        val selectedApps: Set<String> = DEFAULT_MONITORED_APPS,
        val defaultModelId: Long = -1L,

        val styleLearningEnabled: Boolean = true,
        val autoSendEnabled: Boolean = false,
        val currentUserId: String = "",
        val userId: String = "",
        val isFirstLaunch: Boolean = true,
        val notificationPermissionAsked: Boolean = false,
        val overlayPermissionAsked: Boolean = false,
        // Hybrid search settings
        val semanticSearchEnabled: Boolean = true,
        val searchMode: String = "HYBRID", // KEYWORD, SEMANTIC, HYBRID
        // Theme settings
        val themeMode: String = "system" // light, dark, system
    )

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                monitoringEnabled = preferences[PreferencesKeys.MONITORING_ENABLED] ?: true,
                floatingWindowEnabled = preferences[PreferencesKeys.FLOATING_WINDOW_ENABLED] ?: true,
                floatingIconEnabled = preferences[PreferencesKeys.FLOATING_ICON_ENABLED] ?: false,
                selectedApps = resolveSelectedApps(preferences),
                defaultModelId = preferences[PreferencesKeys.DEFAULT_MODEL_ID] ?: -1L,

                styleLearningEnabled = preferences[PreferencesKeys.STYLE_LEARNING_ENABLED] ?: true,
                autoSendEnabled = preferences[PreferencesKeys.AUTO_SEND_ENABLED] ?: false,
                currentUserId = preferences[PreferencesKeys.CURRENT_USER_ID] ?: "",
                userId = preferences[PreferencesKeys.USER_ID] ?: "",
                isFirstLaunch = preferences[PreferencesKeys.FIRST_LAUNCH] ?: true,
                notificationPermissionAsked = preferences[PreferencesKeys.NOTIFICATION_PERMISSION_ASKED] ?: false,
                overlayPermissionAsked = preferences[PreferencesKeys.OVERLAY_PERMISSION_ASKED] ?: false,
                // Hybrid search settings
                semanticSearchEnabled = preferences[PreferencesKeys.SEMANTIC_SEARCH_ENABLED] ?: true,
                searchMode = preferences[PreferencesKeys.SEARCH_MODE] ?: "HYBRID",
                // Theme settings
                themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "system"
            )
        }


    suspend fun updateMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MONITORING_ENABLED] = enabled
        }
    }

    suspend fun updateFloatingWindowEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FLOATING_WINDOW_ENABLED] = enabled
        }
    }

    suspend fun updateFloatingIconEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FLOATING_ICON_ENABLED] = enabled
        }
    }

    suspend fun updateSelectedApps(apps: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_APPS] = apps
        }
    }

    suspend fun addSelectedApp(packageName: String) {
        dataStore.edit { preferences ->
            val current = resolveSelectedApps(preferences)
            preferences[PreferencesKeys.SELECTED_APPS] = current + packageName
        }
    }

    suspend fun removeSelectedApp(packageName: String) {
        dataStore.edit { preferences ->
            val current = resolveSelectedApps(preferences)
            preferences[PreferencesKeys.SELECTED_APPS] = current - packageName
        }
    }

    private fun resolveSelectedApps(preferences: Preferences): Set<String> {
        return if (preferences.contains(PreferencesKeys.SELECTED_APPS)) {
            preferences[PreferencesKeys.SELECTED_APPS] ?: emptySet()
        } else {
            DEFAULT_MONITORED_APPS
        }
    }


    suspend fun updateDefaultModelId(modelId: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_MODEL_ID] = modelId
        }
    }

    suspend fun updateStyleLearningEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STYLE_LEARNING_ENABLED] = enabled
        }
    }

    suspend fun updateAutoSendEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SEND_ENABLED] = enabled
        }
    }

    suspend fun updateCurrentUserId(userId: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_USER_ID] = userId
        }
    }

    suspend fun saveUserId(userId: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ID] = userId
        }
    }

    suspend fun clearUserId() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.USER_ID)
        }
    }

    suspend fun setFirstLaunchComplete() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIRST_LAUNCH] = false
        }
    }

    suspend fun setNotificationPermissionAsked() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATION_PERMISSION_ASKED] = true
        }
    }

    suspend fun setOverlayPermissionAsked() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.OVERLAY_PERMISSION_ASKED] = true
        }
    }

    // Hybrid search settings
    suspend fun updateSemanticSearchEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEMANTIC_SEARCH_ENABLED] = enabled
        }
    }

    suspend fun updateSearchMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEARCH_MODE] = mode
        }
    }

    // Theme settings
    suspend fun updateThemeMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    // Auth / Device
    val authTokenFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTH_TOKEN] ?: ""
        }

    suspend fun saveAuthToken(token: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTH_TOKEN] = token
        }
    }

    suspend fun saveDeviceId(deviceId: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVICE_ID] = deviceId
        }
    }

    suspend fun clearAuthToken() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.AUTH_TOKEN)
        }
    }

    suspend fun clearAllAuthData() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.AUTH_TOKEN)
            preferences.remove(PreferencesKeys.USER_ID)
            preferences.remove(PreferencesKeys.CURRENT_USER_ID)
        }
    }

    // Sync timestamp
    val lastSyncTimestampFlow: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L
        }

    suspend fun getLastSyncTimestamp(): Long {
        return lastSyncTimestampFlow.first()
    }

    suspend fun updateLastSyncTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp
        }
    }
}
