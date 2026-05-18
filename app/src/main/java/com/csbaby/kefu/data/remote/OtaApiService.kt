package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.model.OtaUpdate
import retrofit2.http.GET
import retrofit2.http.Query

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
}

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