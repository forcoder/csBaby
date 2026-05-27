package com.csbaby.kefu.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.infrastructure.notification.NotificationListenerServiceImpl
import com.csbaby.kefu.infrastructure.window.FloatingWindowService
import com.csbaby.kefu.presentation.navigation.AppNavigation
import com.csbaby.kefu.presentation.theme.KefuTheme
import com.csbaby.kefu.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private var pendingOverlayPermission = false
    private var pendingNotificationPermission = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Android 13+ POST_NOTIFICATIONS granted → 请求悬浮窗权限
        if (granted) {
            requestOverlayPermission()
        } else {
            // 即使没授权，也尝试请求悬浮窗权限
            requestOverlayPermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val preferences by preferencesManager.userPreferencesFlow.collectAsState(
                initial = PreferencesManager.UserPreferences()
            )
            val currentThemeMode = remember(preferences.themeMode) {
                ThemeMode.fromValue(preferences.themeMode)
            }

            KefuTheme(themeMode = currentThemeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 检查权限是否已到位，如果之前在等待用户授权，则继续引导
        if (pendingOverlayPermission && Settings.canDrawOverlays(this)) {
            pendingOverlayPermission = false
            // 悬浮窗权限已授权 → 检查用户是否配置了悬浮图标，有则自动显示
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                try {
                    val prefs = preferencesManager.userPreferencesFlow.first()
                    if (prefs.floatingIconEnabled) {
                        FloatingWindowService.showIconOnly(this@MainActivity)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
            // 继续请求通知监听权限
            requestNotificationListenerPermissionIfNeeded()
        } else if (pendingNotificationPermission &&
                   NotificationListenerServiceImpl.isNotificationAccessEnabled(this)) {
            pendingNotificationPermission = false
            // 通知监听权限已授权
        }
    }

    private fun checkPermissions() {
        // 步骤1：Android 13+ 请求 POST_NOTIFICATIONS 权限
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        // 步骤2：请求悬浮窗权限（用户从设置返回后，onResume 会继续步骤3）
        requestOverlayPermission()

        // 步骤3：如果悬浮窗已授权，立即请求通知监听权限
        if (Settings.canDrawOverlays(this)) {
            requestNotificationListenerPermissionIfNeeded()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayPermission = true
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            // 已授权，立即继续下一步
            requestNotificationListenerPermissionIfNeeded()
        }
    }

    private fun requestNotificationListenerPermissionIfNeeded() {
        if (!NotificationListenerServiceImpl.isNotificationAccessEnabled(this)) {
            pendingNotificationPermission = true
            requestNotificationListenerPermission()
        }
    }

    private fun requestNotificationListenerPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
}

