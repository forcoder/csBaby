package com.csbaby.kefu

import android.app.Application
import android.provider.Settings
import android.util.Log
import com.csbaby.kefu.BuildConfig
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.data.sync.SyncWorker
import com.csbaby.kefu.infrastructure.ota.OtaScheduler
import com.csbaby.kefu.infrastructure.reply.ReplyOrchestrator
import com.csbaby.kefu.infrastructure.window.FloatingWindowService
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Application class — uses standard @HiltAndroidApp for full Hilt initialization.
 * This enables @AndroidEntryPoint on Activity / Service / BroadcastReceiver components.
 */
@HiltAndroidApp
class KefuApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Log.d(TAG, "KefuApplication.onCreate() starting")

        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                this,
                AppEntryPoint::class.java
            )

            try {
                val replyOrchestrator = entryPoint.replyOrchestrator()
                replyOrchestrator.start()
                Timber.d("ReplyOrchestrator started")
                Log.d(TAG, "ReplyOrchestrator started OK")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start ReplyOrchestrator")
                Log.e(TAG, "Failed to start ReplyOrchestrator", e)
            }

            try {
                val otaScheduler = entryPoint.otaScheduler()
                otaScheduler.schedulePeriodicUpdateCheck()
                Timber.d("OTA update check scheduled")
                Log.d(TAG, "OTA update check scheduled OK")
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule OTA updates")
                Log.e(TAG, "Failed to schedule OTA updates", e)
            }

            // 调度兜底同步 Worker（每 15 分钟，有网时）
            try {
                SyncWorker.schedule(this)
                Timber.d("SyncWorker scheduled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule SyncWorker")
            }

            // 恢复同步登录状态（内部会自动触发全量同步）
            try {
                val syncManager = entryPoint.syncManager()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    syncManager.restoreAuthState()
                }
                Timber.d("Sync auth restore triggered")
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore sync auth state")
            }

            // 如果用户已开启悬浮窗图标且有悬浮窗权限，启动时自动显示
            try {
                val prefs = entryPoint.preferencesManager()
                val preferences = prefs.userPreferencesFlow.first()
                if (preferences.floatingIconEnabled && Settings.canDrawOverlays(this@KefuApplication)) {
                    Timber.d("悬浮窗图标已开启且有权限，应用启动时自动显示")
                    FloatingWindowService.showIconOnly(this@KefuApplication)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to auto-show floating icon on startup")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hilt EntryPoint bootstrap failed — app will run without auto-reply", e)
        }
    }

    companion object {
        private const val TAG = "KefuApp"
    }
}
