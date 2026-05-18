package com.csbaby.kefu.infrastructure.ota

import android.content.Context
import androidx.work.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OTA更新调度器
 * 负责安排后台更新检查任务
 */
@Singleton
class OtaScheduler @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val WORK_TAG = "ota_update"
        
        // 更新检查间隔（24小时）
        private const val UPDATE_CHECK_INTERVAL_HOURS = 24L
        
        // 初始延迟（应用启动后30分钟）
        private const val INITIAL_DELAY_MINUTES = 30L
        
        // 灵活执行窗口（±1小时）
        private const val FLEX_INTERVAL_HOURS = 1L
    }
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * 安排定期更新检查
     */
    fun schedulePeriodicUpdateCheck() {
        try {
            // 创建周期性工作请求
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val periodicWorkRequest = PeriodicWorkRequestBuilder<OtaUpdateWorker>(
                UPDATE_CHECK_INTERVAL_HOURS, 
                TimeUnit.HOURS,
                FLEX_INTERVAL_HOURS, 
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build()
            
            // 使用唯一的周期性工作，避免重复调度
            workManager.enqueueUniquePeriodicWork(
                OtaUpdateWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // 如果已存在，保持原状
                periodicWorkRequest
            )
            
            Timber.d("已安排OTA更新检查，每${UPDATE_CHECK_INTERVAL_HOURS}小时检查一次")
            
        } catch (e: Exception) {
            Timber.e(e, "安排OTA更新检查失败")
        }
    }
    
    /**
     * 安排立即更新检查（手动触发）
     */
    fun scheduleImmediateUpdateCheck() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val immediateWorkRequest = OneTimeWorkRequestBuilder<OtaUpdateWorker>()
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()
            
            workManager.enqueue(immediateWorkRequest)
            
            Timber.d("已安排立即更新检查")
            
        } catch (e: Exception) {
            Timber.e(e, "安排立即更新检查失败")
        }
    }
    
    /**
     * 取消所有更新检查
     */
    fun cancelAllUpdateChecks() {
        try {
            workManager.cancelAllWorkByTag(WORK_TAG)
            Timber.d("已取消所有OTA更新检查")
        } catch (e: Exception) {
            Timber.e(e, "取消OTA更新检查失败")
        }
    }
    
    /**
     * 检查是否已有更新检查任务在运行
     */
    fun hasScheduledUpdateCheck(): Boolean {
        return try {
            val workInfos = workManager.getWorkInfosByTag(WORK_TAG).get()
            workInfos.any { 
                it.state == WorkInfo.State.RUNNING || 
                it.state == WorkInfo.State.ENQUEUED 
            }
        } catch (e: Exception) {
            Timber.e(e, "检查更新检查任务状态失败")
            false
        }
    }
    
    /**
     * 获取下一次检查时间
     */
    suspend fun getNextCheckTime(): String? {
        return try {
            val workInfos = workManager.getWorkInfosByTag(WORK_TAG).await()
            
            workInfos
                .firstOrNull { it.state == WorkInfo.State.ENQUEUED }
                ?.nextScheduleTimeMillis
                ?.let { nextRunTime ->
                    if (nextRunTime > 0) {
                        val nextRunHours = (nextRunTime - System.currentTimeMillis()) / (1000 * 60 * 60)
                        if (nextRunHours > 0) {
                            "约${nextRunHours.toInt()}小时后"
                        } else {
                            "即将进行"
                        }
                    } else {
                        null
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "获取下次检查时间失败")
            null
        }
    }
}