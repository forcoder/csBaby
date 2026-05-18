package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.model.OtaUpdate
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Streaming

/**
 * shz.al 文件分享服务客户端
 * 
 * API 文档: https://shz.al/api
 * 
 * 使用方式:
 * - 版本文件: ${ShzlConfig.VERSION_FILE_URL} (JSON 格式)
 * - APK 文件: ${ShzlConfig.APK_FILE_URL} (APK 二进制文件)
 */
interface ShzlApiService {
    
    /**
     * 获取版本信息 JSON
     * 直接访问 ${ShzlConfig.VERSION_FILE_URL}
     */
    @GET("~${ShzlConfig.VERSION_FILE_NAME}")
    suspend fun getVersionInfo(): ShzlVersionInfo
    
    /**
     * 下载 APK 文件（流式下载）
     * 直接访问 ${ShzlConfig.APK_FILE_URL}
     */
    @Streaming
    @GET("~${ShzlConfig.APK_FILE_NAME}")
    suspend fun downloadApk(): okhttp3.ResponseBody
}

/**
 * shz.al 版本信息 JSON 格式
 * 
 * 示例:
 * {
 *   "versionCode": 8,
 *   "versionName": "1.1.2",
 *   "releaseDate": "2026-04-11",
 *   "fileSize": 16777216,
 *   "md5": "abc123...",
 *   "downloadUrl": "${ShzlConfig.APK_FILE_URL}",
 *   "releaseNotes": "版本 1.1.2 更新内容...",
 *   "forceUpdate": false
 * }
 */
data class ShzlVersionInfo(
    @SerializedName("versionCode")
    val versionCode: Int,
    
    @SerializedName("versionName")
    val versionName: String,
    
    @SerializedName("releaseDate")
    val releaseDate: String,
    
    @SerializedName("fileSize")
    val fileSize: Long,
    
    @SerializedName("md5")
    val md5: String,
    
    @SerializedName("downloadUrl")
    val downloadUrl: String,
    
    @SerializedName("releaseNotes")
    val releaseNotes: String,
    
    @SerializedName("forceUpdate")
    val forceUpdate: Boolean = false
) {
    /**
     * 转换为 OtaUpdate 对象
     */
    fun toOtaUpdate(): OtaUpdate {
        return OtaUpdate(
            versionCode = versionCode,
            versionName = versionName,
            downloadUrl = downloadUrl,
            fileSize = fileSize,
            md5 = md5,
            releaseNotes = releaseNotes,
            releaseDate = releaseDate,
            isForceUpdate = forceUpdate,
            minRequiredVersion = 0,
            uploader = "shz.al",
            objectKey = "",
            uploadTime = releaseDate
        )
    }
}
