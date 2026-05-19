package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.model.OtaUpdate
import retrofit2.http.*

/**
 * OTA更新API服务接口
 */
interface OtaApiService {
    
    /**
     * 检查更新
     * @param versionCode 当前版本号
     * @param channel 渠道（可选）
     */
    @GET("api/v1/ota/check")
    suspend fun checkForUpdate(
        @Query("versionCode") versionCode: Int,
        @Query("channel") channel: String = "default"
    ): ApiResponse<OtaUpdate?>

    /**
     * 获取最新版本信息（无缓存）
     */
    @GET("api/v1/ota/latest")
    suspend fun getLatestVersion(
        @Query("channel") channel: String = "default"
    ): ApiResponse<OtaUpdate?>

    /**
     * 获取版本列表（管理员）
     */
    @GET("api/v1/ota/versions")
    suspend fun getVersionList(): ApiResponse<List<OtaVersionItem>>

    /**
     * 发布新版本（管理员）
     */
    @POST("api/v1/ota/versions")
    suspend fun publishVersion(@Body request: PublishVersionRequest): ApiResponse<PublishVersionResult>
}

data class OtaVersionItem(
    val id: Int = 0,
    @field:SerializedName("version_code") val versionCode: Int = 0,
    @field:SerializedName("version_name") val versionName: String = "",
    val channel: String = "default",
    @field:SerializedName("is_published") val isPublished: Boolean = true,
    @field:SerializedName("is_force_update") val isForceUpdate: Boolean = false,
    @field:SerializedName("release_date") val releaseDate: Long = 0,
    @field:SerializedName("file_size") val fileSize: Long = 0,
    @field:SerializedName("created_at") val createdAt: Long = 0
)

data class PublishVersionRequest(
    @field:SerializedName("version_code") val versionCode: Int,
    @field:SerializedName("version_name") val versionName: String,
    @field:SerializedName("download_url") val downloadUrl: String,
    @field:SerializedName("file_size") val fileSize: Long = 0,
    val md5: String = "",
    @field:SerializedName("release_notes") val releaseNotes: String = "",
    val channel: String = "default",
    @field:SerializedName("is_force_update") val isForceUpdate: Boolean = false,
    @field:SerializedName("min_required_version") val minRequiredVersion: Int = 1
)

data class PublishVersionResult(
    val versionCode: Int = 0,
    val versionName: String = ""
)

/**
 * API响应包装类
 */
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
) {
    val isSuccess: Boolean
        get() = code == 0
    
    val isNoUpdate: Boolean
        get() = code == 204 // 204表示没有更新
}