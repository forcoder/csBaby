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
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
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
            }

            // DEBUG: 自动登录测试账号，验证 API 连通性
            if (BuildConfig.DEBUG) {
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        val syncManager = entryPoint.syncManager()
                        // 先注册新账号
                        Log.d("KefuApp", "DEBUG: 尝试注册 test@test.com")
                        val regResult = syncManager.register("test@test.com", "123456", "TestUser")
                        regResult.fold(
                            onSuccess = { Log.d("KefuApp", "DEBUG: 注册成功") },
                            onFailure = { Log.d("KefuApp", "DEBUG: 注册失败(可能已存在): ${it.message}") }
                        )
                        // 然后登录
                        Log.d("KefuApp", "DEBUG: 尝试登录 test@test.com")
                        val result = syncManager.login("test@test.com", "123456")
                        result.fold(
                            onSuccess = { auth ->
                                Log.d("KefuApp", "DEBUG: 自动登录成功! tenant=${auth.tenantId}")
                                syncManager.fullSync(auth.tenantId)
                            },
                            onFailure = { e ->
                                Log.e("KefuApp", "DEBUG: 自动登录失败: ${e.message}")
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("KefuApp", "DEBUG: 自动登录异常", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hilt EntryPoint bootstrap failed — app will run without auto-reply", e)
        }
    }

    companion object {
        private const val TAG = "KefuApp"
    }
}
