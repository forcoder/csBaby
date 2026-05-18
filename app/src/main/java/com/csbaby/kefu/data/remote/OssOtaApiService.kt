package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.model.OtaUpdate
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.Path

/**
 * 阿里云OSS版本管理API服务
 * 
 * 这个服务可以部署在服务器端，用于：
 * 1. 管理版本信息
 * 2. 生成上传签名
 * 3. 记录上传历史
 * 4. 控制强制更新
 */
interface OssOtaApiService {
    
    /**
     * 检查OSS上的更新（基于阿里云OSS的直接文件访问）
     */
    @GET("api/v1/oss/ota/check")
    suspend fun checkForUpdate(
        @Query("versionCode") versionCode: Int,
        @Query("appName") appName: String = "kefu",
        @Query("channel") channel: String = "default"
    ): ApiResponse<OtaUpdate?>
    
    /**
     * 获取最新版本信息（从OSS元数据获取）
     */
    @GET("api/v1/oss/ota/latest")
    suspend fun getLatestVersion(
        @Query("appName") appName: String = "kefu",
        @Query("channel") channel: String = "default"
    ): ApiResponse<OtaUpdate?>
    
    /**
     * 获取上传签名（用于客户端直接上传到OSS）
     */
    @GET("api/v1/oss/ota/upload-signature")
    suspend fun getUploadSignature(
        @Query("fileName") fileName: String,
        @Query("fileSize") fileSize: Long,
        @Query("versionCode") versionCode: Int,
        @Query("versionName") versionName: String,
        @Query("contentType") contentType: String = "application/vnd.android.package-archive"
    ): ApiResponse<UploadSignature>
    
    /**
     * 注册已上传的版本（记录版本信息到数据库）
     */
    @POST("api/v1/oss/ota/register")
    suspend fun registerVersion(
        @Body request: RegisterVersionRequest
    ): ApiResponse<Unit>
    
    /**
     * 获取版本列表
     */
    @GET("api/v1/oss/ota/versions")
    suspend fun getVersionList(
        @Query("appName") appName: String = "kefu",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): ApiResponse<List<VersionListItem>>
    
    /**
     * 获取特定版本详情
     */
    @GET("api/v1/oss/ota/versions/{versionCode}")
    suspend fun getVersionDetail(
        @Path("versionCode") versionCode: Int
    ): ApiResponse<VersionDetail>
    
    /**
     * 设置强制更新
     */
    @PUT("api/v1/oss/ota/versions/{versionCode}/force-update")
    suspend fun setForceUpdate(
        @Path("versionCode") versionCode: Int,
        @Query("forceUpdate") forceUpdate: Boolean
    ): ApiResponse<Unit>
    
    /**
     * 删除版本
     */
    @retrofit2.http.DELETE("api/v1/oss/ota/versions/{versionCode}")
    suspend fun deleteVersion(
        @Path("versionCode") versionCode: Int
    ): ApiResponse<Unit>
}

/**
 * 上传签名响应
 */
data class UploadSignature(
    val signature: String,
    val policy: String,
    val ossAccessKeyId: String,
    val key: String,
    val callback: String? = null,
    val expire: Long,
    val host: String,
    val uploadUrl: String
)

/**
 * 注册版本请求
 */
data class RegisterVersionRequest(
    val versionCode: Int,
    val versionName: String,
    val objectKey: String,
    val fileSize: Long,
    val md5: String,
    val releaseNotes: String = "",
    val isForceUpdate: Boolean = false,
    val minRequiredVersion: Int = 0,
    val channel: String = "default",
    val uploader: String = "manual"
)

/**
 * 版本列表项
 */
data class VersionListItem(
    val versionCode: Int,
    val versionName: String,
    val uploadTime: String,
    val fileSize: Long,
    val downloadCount: Long = 0,
    val isForceUpdate: Boolean = false,
    val uploader: String
)

/**
 * 版本详情
 */
data class VersionDetail(
    val versionCode: Int,
    val versionName: String,
    val objectKey: String,
    val downloadUrl: String,
    val fileSize: Long,
    val md5: String,
    val releaseNotes: String,
    val uploadTime: String,
    val uploader: String,
    val isForceUpdate: Boolean,
    val minRequiredVersion: Int,
    val channel: String,
    val downloadCount: Long = 0,
    val lastDownloadTime: String? = null
)

/**
 * 阿里云OSS直接版本检查（用于没有中间服务器的情况）
 * 
 * 这个类可以直接从阿里云OSS获取版本信息
 */
class DirectOssVersionChecker(
    private val ossManager: com.csbaby.kefu.infrastructure.oss.AliyunOssManager
) {
    
    companion object {
        const val VERSION_MANIFEST_FILE = "apks/kefu/versions.json"
    }
    
    /**
     * 版本清单格式
     */
    data class VersionManifest(
        val appName: String,
        val currentVersionCode: Int,
        val currentVersionName: String,
        val versions: List<VersionInfo>,
        val lastUpdateTime: String
    )
    
    data class VersionInfo(
        val versionCode: Int,
        val versionName: String,
        val objectKey: String,
        val fileSize: Long,
        val md5: String,
        val releaseNotes: String,
        val releaseDate: String,
        val isForceUpdate: Boolean,
        val minRequiredVersion: Int,
        val downloadUrl: String
    )
    
    /**
     * 生成版本清单（用于手动上传时创建）
     */
    fun generateVersionManifest(
        appName: String,
        currentVersionCode: Int,
        currentVersionName: String,
        versions: List<VersionInfo>
    ): VersionManifest {
        return VersionManifest(
            appName = appName,
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
            versions = versions,
            lastUpdateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
        )
    }
    
    /**
     * 从版本清单中查找更新
     */
    fun findUpdateFromManifest(
        manifest: VersionManifest,
        currentVersionCode: Int
    ): VersionInfo? {
        return manifest.versions
            .filter { it.versionCode > currentVersionCode }
            .maxByOrNull { it.versionCode }
    }
}

/**
 * 阿里云OSS OTA更新仓库实现
 * 
 * 这个实现可以直接从阿里云OSS获取更新信息，无需中间服务器
 */
class OssOtaRepository(
    private val ossManager: com.csbaby.kefu.infrastructure.oss.AliyunOssManager,
    private val directChecker: DirectOssVersionChecker
) {
    
    /**
     * 检查OSS上的更新
     */
    suspend fun checkOssUpdate(currentVersionCode: Int): Result<OtaUpdate?> {
        return try {
            // 从OSS获取版本清单
            val manifestUrl = ossManager.getOssDomain() + DirectOssVersionChecker.VERSION_MANIFEST_FILE
            
            // 在实际实现中，这里应该使用HTTP客户端获取版本清单
            // 目前我们返回一个模拟结果
            if (currentVersionCode < 2) {
                val update = OtaUpdate(
                    versionCode = 2,
                    versionName = "1.1.0",
                    downloadUrl = "${ossManager.getOssDomain()}apks/kefu/v1.1.0_2/2026-04-08/202345_abc12345.apk",
                    fileSize = 15 * 1024 * 1024,
                    md5 = "a1b2c3d4e5f678901234567890123456",
                    releaseNotes = "版本 1.1.0 (OSS上传)\n" +
                                 "1. 支持阿里云OSS更新\n" +
                                 "2. 支持手动版本管理\n" +
                                 "3. 优化更新体验\n" +
                                 "4. 修复已知问题",
                    releaseDate = "2026-04-08",
                    isForceUpdate = false,
                    minRequiredVersion = 1,
                    uploader = "manual",
                    objectKey = "apks/kefu/v1.1.0_2/2026-04-08/202345_abc12345.apk",
                    uploadTime = "2026-04-08 20:45:30"
                )
                Result.success(update)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(Exception("检查OSS更新失败: ${e.message}"))
        }
    }
    
    /**
     * 获取OSS上的版本列表
     */
    suspend fun getOssVersionList(): Result<List<VersionListItem>> {
        return try {
            // 模拟返回版本列表
            val versions = listOf(
                VersionListItem(
                    versionCode = 2,
                    versionName = "1.1.0",
                    uploadTime = "2026-04-08 20:45:30",
                    fileSize = 15 * 1024 * 1024,
                    downloadCount = 150,
                    isForceUpdate = false,
                    uploader = "manual"
                ),
                VersionListItem(
                    versionCode = 1,
                    versionName = "1.0.0",
                    uploadTime = "2026-04-07 15:30:20",
                    fileSize = 14 * 1024 * 1024,
                    downloadCount = 500,
                    isForceUpdate = false,
                    uploader = "initial"
                )
            )
            Result.success(versions)
        } catch (e: Exception) {
            Result.failure(Exception("获取版本列表失败: ${e.message}"))
        }
    }
}