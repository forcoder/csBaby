package com.csbaby.kefu.data.repository

import android.content.Context
import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.data.remote.MockOtaApiService
import com.csbaby.kefu.data.remote.OtaApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OTA更新仓库
 */
interface OtaRepository {
    /**
     * 检查更新
     */
    suspend fun checkForUpdate(currentVersionCode: Int): Result<OtaUpdate?>
    
    /**
     * 获取下载进度
     */
    val downloadProgress: Flow<Float>
    
    /**
     * 获取下载状态
     */
    val downloadStatus: Flow<String>
    
    /**
     * 下载APK
     */
    suspend fun downloadApk(update: OtaUpdate): Result<String>
    
    /**
     * 获取本地已下载的APK路径
     */
    suspend fun getDownloadedApkPath(versionCode: Int): String?
    
    /**
     * 清理旧版本APK文件
     */
    suspend fun cleanupOldVersions()
}

/**
 * OTA更新仓库实现
 */
@Singleton
class OtaRepositoryImpl @Inject constructor(
    private val context: Context,
    private val apiService: OtaApiService
) : OtaRepository {
    
    private val _downloadProgress = MutableStateFlow(0f)
    override val downloadProgress: StateFlow<Float> = _downloadProgress
    
    private val _downloadStatus = MutableStateFlow("")
    override val downloadStatus: StateFlow<String> = _downloadStatus
    
    override suspend fun checkForUpdate(currentVersionCode: Int): Result<OtaUpdate?> {
        return try {
            // 在实际应用中，使用真实的API服务
            // val response = apiService.checkForUpdate(currentVersionCode)
            
            // 测试阶段使用模拟服务
            val mockService = MockOtaApiService()
            val response = mockService.checkForUpdate(currentVersionCode)
            
            if (response.isSuccess && response.data != null) {
                Result.success(response.data)
            } else if (response.isNoUpdate) {
                Result.success(null) // 没有更新
            } else {
                Result.failure(Exception("检查更新失败: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络连接失败: ${e.message}"))
        }
    }
    
    override suspend fun downloadApk(update: OtaUpdate): Result<String> {
        // 这里实现下载逻辑
        // 实际实现需要使用DownloadManager或OkHttp下载文件
        return Result.failure(Exception("下载功能暂未实现"))
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
            // 忽略清理错误
        }
    }
    
    /**
     * 模拟下载进度更新（用于测试）
     */
    private suspend fun simulateDownloadProgress() {
        for (progress in 0..100 step 5) {
            _downloadProgress.value = progress / 100f
            _downloadStatus.value = "下载中... $progress%"
            kotlinx.coroutines.delay(100)
        }
        _downloadStatus.value = "下载完成"
    }
}