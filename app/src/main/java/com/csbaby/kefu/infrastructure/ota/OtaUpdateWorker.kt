package com.csbaby.kefu.infrastructure.ota

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.repository.OtaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * OTA更新检查Worker
 * 用于后台定期检查更新
 */
@HiltWorker
class OtaUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: OtaRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "OtaUpdateWorker"
        const val WORK_NAME = "ota_update_check"
    }

    override suspend fun doWork(): Result {
        return try {
            withContext(Dispatchers.IO) {
                Timber.d("开始后台检查更新...")
                
                val result = repository.checkForUpdate(BuildConfig.VERSION_CODE)
                
                if (result.isSuccess) {
                    val update = result.getOrNull()
                    
                    if (update != null && update.needsUpdate(BuildConfig.VERSION_CODE)) {
                        Timber.d("发现新版本: ${update.versionName} (${update.versionCode})")
                        // 这里可以发送通知或存储更新信息
                        // 在实际应用中，可以发送本地通知提醒用户
                    } else {
                        Timber.d("当前已是最新版本")
                    }
                    
                    Result.success()
                } else {
                    Timber.e("检查更新失败: ${result.exceptionOrNull()?.message}")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "OTA更新检查Worker执行失败")
            Result.failure()
        }
    }
    
    /**
     * Worker工厂接口
     */
    interface Factory {
        fun create(context: Context, params: WorkerParameters): OtaUpdateWorker
    }
}