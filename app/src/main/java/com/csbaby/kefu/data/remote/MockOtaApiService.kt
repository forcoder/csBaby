package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.model.OtaUpdate
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * 模拟OTA API服务（用于测试）
 * 在实际部署时替换为真实的Retrofit实现
 */
class MockOtaApiService : OtaApiService {
    
    companion object {
        // 模拟新版本信息
        private val mockUpdate = OtaUpdate(
            versionCode = 2,
            versionName = "1.1.0",
            downloadUrl = "https://example.com/app/kefu-v1.1.0.apk",
            fileSize = 15 * 1024 * 1024, // 15MB
            md5 = "a1b2c3d4e5f678901234567890123456",
            releaseNotes = "版本 1.1.0 更新内容：\n" +
                          "1. 新增OTA热更新功能\n" +
                          "2. 优化消息监控性能\n" +
                          "3. 修复已知问题\n" +
                          "4. 提升应用稳定性",
            releaseDate = "2026-04-08",
            isForceUpdate = false,
            minRequiredVersion = 1
        )
    }
    
    override suspend fun checkForUpdate(versionCode: Int, channel: String): ApiResponse<OtaUpdate?> {
        Timber.d("模拟检查更新: 当前版本=$versionCode, 渠道=$channel")
        
        // 模拟网络延迟
        delay(1000)
        
        return if (versionCode < 2) {
            // 有新版本
            ApiResponse(
                code = 0,
                message = "有新版本可用",
                data = mockUpdate
            )
        } else {
            // 已是最新版本
            ApiResponse(
                code = 204,
                message = "已是最新版本",
                data = null
            )
        }
    }
    
    override suspend fun getLatestVersion(channel: String): ApiResponse<OtaUpdate?> {
        Timber.d("模拟获取最新版本: 渠道=$channel")
        
        // 模拟网络延迟
        delay(800)
        
        return ApiResponse(
            code = 0,
            message = "成功",
            data = mockUpdate
        )
    }
}