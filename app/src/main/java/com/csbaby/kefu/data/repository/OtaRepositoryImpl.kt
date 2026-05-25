package com.csbaby.kefu.data.repository

import android.content.Context
import android.util.Log
import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.data.remote.OtaApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtaRepositoryImpl @Inject constructor(
    private val context: Context,
    private val apiService: OtaApiService
) : OtaRepository {

    private val _downloadProgress = MutableStateFlow(0f)
    override val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadStatus = MutableStateFlow("")
    override val downloadStatus: StateFlow<String> = _downloadStatus.asStateFlow()

    override suspend fun checkForUpdate(currentVersionCode: Int): Result<OtaUpdate?> {
        return try {
            val response = apiService.checkForUpdate(currentVersionCode)
            // shz.al 直接返回 OtaUpdate，比较版本号判断是否有更新
            if (response.versionCode > currentVersionCode) {
                Result.success(response)
            } else {
                Result.success(null) // 没有更新
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络连接失败: ${e.message}"))
        }
    }

    override suspend fun downloadApk(update: OtaUpdate): Result<String> {
        return Result.failure(Exception("请使用 OtaManager.startDownload() 下载"))
    }

    override suspend fun getDownloadedApkPath(versionCode: Int): String? {
        val fileName = "app-update-v$versionCode.apk"
        val file = context.getExternalFilesDir("ota_updates")?.resolve(fileName)
        return file?.takeIf { it.exists() }?.absolutePath
    }

    override suspend fun cleanupOldVersions() {
        try {
            val otaDir = context.getExternalFilesDir("ota_updates")
            otaDir?.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".apk")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("OtaRepository", "清理旧版本失败", e)
        }
    }
}
