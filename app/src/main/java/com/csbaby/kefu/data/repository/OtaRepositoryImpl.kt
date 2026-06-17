package com.csbaby.kefu.data.repository

import android.content.Context
import android.util.Log
import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.data.remote.OtaApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
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
        // 步骤 1: 优先 master URL (~csBabyLog) — 不带版本号, 长期稳定
        val masterResult = tryFetchUpdate("https://shz.al/~csBabyLog", currentVersionCode)
        if (masterResult != null) return masterResult

        // 步骤 2: master 不存在 (404) → fallback 到版本专用 URL
        val versionUrl = "https://shz.al/~csBabyLog_v$currentVersionCode"
        val versionResult = tryFetchUpdate(versionUrl, currentVersionCode)
        if (versionResult != null) return versionResult

        // 两个 URL 都没拿到 — 视为无更新 (而非错误)
        Log.i("OtaRepository", "OTA: 无可用版本信息,视为无更新")
        return Result.success(null)
    }

    /**
     * 尝试从指定 URL 拉取 OTA 信息。
     * 返回: Result<OtaUpdate?>(success 表示有数据或无更新, failure 表示网络错误)
     * 返回 null 表示该 URL 不可用 (404), 调用方应尝试 fallback。
     */
    private suspend fun tryFetchUpdate(url: String, currentVersionCode: Int): Result<OtaUpdate?>? {
        return try {
            val response = apiService.checkForUpdate(url)
            if (response.versionCode > currentVersionCode) {
                Result.success(response)
            } else {
                Result.success(null) // 已是最新版本
            }
        } catch (e: HttpException) {
            if (e.code() == 404) {
                // shz.al 上该版本文件不存在 — 不是网络错误, 让调用方 fallback
                Log.w("OtaRepository", "OTA URL $url 返回 404, 尝试 fallback")
                null
            } else {
                // 其他 HTTP 错误 (500/403 等) 是真错误, 不再 fallback
                Result.failure(Exception("HTTP ${e.code()}: ${e.message}"))
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
