package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.model.OtaUpdate
import kotlinx.coroutines.flow.Flow

/**
 * OTA 更新仓库接口
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
     * 下载 APK
     */
    suspend fun downloadApk(update: OtaUpdate): Result<String>

    /**
     * 获取本地已下载的 APK 路径
     */
    suspend fun getDownloadedApkPath(versionCode: Int): String?

    /**
     * 清理旧版本 APK 文件
     */
    suspend fun cleanupOldVersions()
}
