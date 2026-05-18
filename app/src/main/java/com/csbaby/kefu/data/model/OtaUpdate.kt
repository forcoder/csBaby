package com.csbaby.kefu.data.model

import com.google.gson.annotations.SerializedName

/**
 * OTA更新信息模型
 */
data class OtaUpdate(
    @SerializedName("versionCode")
    val versionCode: Int,
    
    @SerializedName("versionName")
    val versionName: String,
    
    @SerializedName("downloadUrl")
    val downloadUrl: String,
    
    @SerializedName("fileSize")
    val fileSize: Long,
    
    @SerializedName("md5")
    val md5: String,
    
    @SerializedName("releaseNotes")
    val releaseNotes: String,
    
    @SerializedName("releaseDate")
    val releaseDate: String,
    
    @SerializedName("isForceUpdate")
    val isForceUpdate: Boolean = false,
    
    @SerializedName("minRequiredVersion")
    val minRequiredVersion: Int = 1,
    
    // 阿里云OSS专用字段（用于手动版本管理）
    @SerializedName("objectKey")
    val objectKey: String? = null,          // OSS对象键，如: apks/kefu/v1.1.0_2/...
    
    @SerializedName("uploader")
    val uploader: String? = null,           // 上传者（manual, auto, admin等）
    
    @SerializedName("uploadTime")
    val uploadTime: String? = null,         // 上传时间
    
    @SerializedName("channel")
    val channel: String? = "default",       // 分发渠道
    
    @SerializedName("downloadCount")
    val downloadCount: Long = 0,            // 下载次数统计
    
    @SerializedName("signature")
    val signature: String? = null           // 签名验证（可选）
) {
    /**
     * 检查是否需要更新
     */
    fun needsUpdate(currentVersionCode: Int): Boolean {
        return versionCode > currentVersionCode
    }
    
    /**
     * 是否为阿里云OSS版本（通过objectKey判断）
     */
    val isOssVersion: Boolean
        get() = objectKey != null && downloadUrl.contains("oss-cn-shenzhen.aliyuncs.com")
    
    /**
     * 获取OSS对象键（如果不存在则从URL解析）
     */
    fun getOssObjectKey(): String? {
        return objectKey ?: run {
            // 尝试从URL解析OSS对象键
            if (downloadUrl.contains("oss-cn-shenzhen.aliyuncs.com")) {
                val prefix = "https://apk-ota.oss-cn-shenzhen.aliyuncs.com/"
                if (downloadUrl.startsWith(prefix)) {
                    downloadUrl.substring(prefix.length)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }
    
    /**
     * 是否为手动上传版本
     */
    val isManualUpload: Boolean
        get() = uploader == "manual"
}

/**
 * OTA更新状态
 */
enum class UpdateStatus {
    IDLE,           // 空闲状态
    CHECKING,       // 检查中
    UPDATE_AVAILABLE, // 有可用更新
    DOWNLOADING,    // 下载中
    DOWNLOADED,     // 已下载
    INSTALLING,     // 安装中
    SUCCESS,        // 成功
    FAILED          // 失败
}

/**
 * OTA下载进度
 */
data class DownloadProgress(
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val progress: Float = 0f,
    val speedBytesPerSecond: Long = 0
) {
    val percentage: Int
        get() = (progress * 100).toInt()
    
    val isComplete: Boolean
        get() = progress >= 1f
}