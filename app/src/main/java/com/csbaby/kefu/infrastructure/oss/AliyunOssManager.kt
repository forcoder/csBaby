package com.csbaby.kefu.infrastructure.oss

import android.content.Context
import android.net.Uri
import android.util.Base64
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阿里云OSS管理器
 * 用于上传APK文件到阿里云OSS并进行版本管理
 * 
 * 配置信息从外部配置文件读取（oss-config.properties）
 */
@Singleton
class AliyunOssManager @Inject constructor(
    private val context: Context
) {
    
    // OSS配置管理器
    private val ossConfig = OssConfig(context)
    
    companion object {
        // OSS支持的MIME类型
        const val MIME_TYPE_APK = "application/vnd.android.package-archive"
        const val MIME_TYPE_JSON = "application/json"
        
        // HTTP相关常量
        const val METHOD_PUT = "PUT"
        const val METHOD_GET = "GET"
        const val METHOD_POST = "POST"
        const val METHOD_DELETE = "DELETE"
    }
    
    /**
     * OSS上传结果
     */
    data class OssUploadResult(
        val success: Boolean,
        val objectKey: String? = null,
        val url: String? = null,
        val etag: String? = null,
        val size: Long = 0,
        val error: String? = null,
        val md5: String? = null
    )
    
    /**
     * 生成对象键（Object Key）
     * 格式: {apkFolder}/{appName}/{versionName}/{timestamp}-{md5}.apk
     */
    fun generateObjectKey(
        appName: String = ossConfig.appName,
        versionName: String,
        versionCode: Int,
        timestamp: Long = System.currentTimeMillis(),
        fileMd5: String? = null
    ): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
        val timeStr = SimpleDateFormat("HHmmss", Locale.US).format(Date(timestamp))
        val md5Part = fileMd5?.take(8) ?: "unknown"
        
        return "${ossConfig.apkFolder}${appName}/v${versionName}_${versionCode}/$dateStr/${timeStr}_${md5Part}.apk"
    }
    
    /**
     * 计算文件的MD5值
     */
    fun calculateFileMd5(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            file.inputStream().use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val md5Bytes = digest.digest()
            StringBuilder().apply {
                for (byte in md5Bytes) {
                    append(String.format("%02x", byte))
                }
            }.toString()
        } catch (e: Exception) {
            Timber.e(e, "计算文件MD5失败")
            null
        }
    }
    
    /**
     * 生成OSS签名（用于直接PUT上传）
     */
    fun generatePutSignature(
        objectKey: String,
        contentType: String = MIME_TYPE_APK,
        contentMd5: String? = null
    ): Map<String, String>? {
        // 检查配置是否有效
        if (!ossConfig.isValid()) {
            Timber.e("OSS配置无效，无法生成签名")
            return null
        }
        
        val date = Date(System.currentTimeMillis() + 3600 * 1000) // 1小时有效期
        val dateStr = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).format(date)
        
        val canonicalResource = "/${ossConfig.bucket}/$objectKey"
        val canonicalString = "$METHOD_PUT\n" +
            "$contentMd5\n" +
            "$contentType\n" +
            "$dateStr\n" +
            canonicalResource
        
        val signature = hmacSha1(canonicalString, ossConfig.accessKeySecret)
        val encodedSignature = Base64.encodeToString(signature, Base64.NO_WRAP)
        
        return mapOf(
            "Date" to dateStr,
            "Content-Type" to contentType,
            "Authorization" to "OSS ${ossConfig.accessKeyId}:$encodedSignature",
            "Host" to "${ossConfig.bucket}.${ossConfig.endpoint}"
        )
    }
    
    /**
     * 使用HMAC-SHA1算法生成签名
     */
    private fun hmacSha1(data: String, key: String): ByteArray {
        return try {
            val hmac = Mac.getInstance("HmacSHA1")
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1")
            hmac.init(secretKey)
            hmac.doFinal(data.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Timber.e(e, "HMAC-SHA1签名失败")
            ByteArray(0)
        }
    }
    
    /**
     * 将Uri转换为本地文件（用于选择文件上传）
     */
    suspend fun uriToFile(uri: Uri): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 创建临时文件
                val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}.apk")
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                tempFile
            }
        } catch (e: Exception) {
            Timber.e(e, "转换Uri到文件失败")
            null
        }
    }
    
    /**
     * 获取APK文件基本信息
     */
    data class ApkFileInfo(
        val fileName: String,
        val fileSize: Long,
        val md5: String?,
        val timestamp: Long,
        val mimeType: String
    )
    
    /**
     * 分析APK文件信息
     */
    fun analyzeApkFile(file: File): ApkFileInfo {
        return ApkFileInfo(
            fileName = file.name,
            fileSize = file.length(),
            md5 = calculateFileMd5(file),
            timestamp = file.lastModified(),
            mimeType = MIME_TYPE_APK
        )
    }
    
    /**
     * 构建OSS直接上传URL
     */
    fun buildDirectUploadUrl(objectKey: String): String? {
        if (!ossConfig.isValid()) {
            Timber.e("OSS配置无效，无法构建上传URL")
            return null
        }
        return "https://${ossConfig.bucket}.${ossConfig.endpoint}/$objectKey"
    }
    
    /**
     * 解析OSS URL为对象键
     */
    fun parseObjectKeyFromUrl(url: String): String? {
        if (!ossConfig.isValid()) {
            Timber.e("OSS配置无效，无法解析URL")
            return null
        }
        val prefix = "https://${ossConfig.bucket}.${ossConfig.endpoint}/"
        return if (url.startsWith(prefix)) {
            url.substring(prefix.length)
        } else {
            null
        }
    }
    
    /**
     * 清理上传缓存
     */
    fun cleanupTempFiles() {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("upload_temp_") && file.isFile) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "清理临时文件失败")
        }
    }
    
    /**
     * 验证阿里云OSS配置
     */
    fun validateConfig(): Boolean {
        return ossConfig.isValid()
    }
    
    /**
     * 获取OSS域名（用于构建完整的下载URL）
     */
    fun getOssDomain(): String? {
        return if (ossConfig.isValid()) {
            ossConfig.getOssDomain()
        } else {
            null
        }
    }
}