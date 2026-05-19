package com.csbaby.kefu.data.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 兜底同步 Worker。
 * 每 15 分钟执行一次（有网络时），确保即使写入触发器失败，数据最终也会同步。
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            com.csbaby.kefu.AppEntryPoint::class.java
        )
        val syncManager = entryPoint.syncManager()

        val tenantId = syncManager.currentTenantId()
        if (tenantId == null) {
            Timber.d("SyncWorker: 未登录，跳过")
            return Result.success()
        }

        Timber.d("SyncWorker: 开始兜底同步, tenant=$tenantId")
        return try {
            val result = syncManager.incrementalSync(tenantId)
            if (result.isSuccess) {
                Timber.d("SyncWorker: 兜底同步成功")
                Result.success()
            } else {
                Timber.w("SyncWorker: 兜底同步失败: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker: 同步异常")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "sync_worker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("SyncWorker: 已调度每 15 分钟兜底同步")
        }
    }
}
