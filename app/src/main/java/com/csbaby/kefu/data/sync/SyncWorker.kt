package com.csbaby.kefu.data.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 兜底同步 Worker。
 * 每 15 分钟执行一次（有网络时），确保即使写入触发器失败，数据最终也会同步。
 * 增量同步失败时自动降级为全量同步，确保数据最终一致。
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
            val incrementalResult = syncManager.incrementalSync(tenantId)
            if (incrementalResult.isSuccess) {
                Timber.d("SyncWorker: 兜底同步成功")
                Result.success()
            } else {
                Timber.w("SyncWorker: 增量同步失败，尝试全量同步兜底: ${incrementalResult.exceptionOrNull()?.message}")
                // 增量同步失败时降级为全量同步
                val fullResult = syncManager.fullSync(tenantId)
                if (fullResult.isSuccess) {
                    Timber.d("SyncWorker: 全量同步兜底成功")
                    Result.success()
                } else {
                    Timber.w("SyncWorker: 全量同步也失败: ${fullResult.exceptionOrNull()?.message}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker: 同步异常，尝试全量同步")
            try {
                val fullResult = syncManager.fullSync(tenantId)
                if (fullResult.isSuccess) Result.success() else Result.retry()
            } catch (e2: Exception) {
                Timber.e(e2, "SyncWorker: 全量同步也异常")
                Result.retry()
            }
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
