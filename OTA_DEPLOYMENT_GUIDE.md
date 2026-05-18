# OTA 升级系统部署指南

## 概述

已为客服助手应用构建完整的OTA（Over-the-Air）升级系统，用户可以在应用内点击升级按钮自动下载和安装新版本。

## 主要功能

### 1. 更新检查
- 手动检查：在"我的"页面点击"检查更新"按钮
- 自动检查：应用启动后每24小时自动检查一次
- 后台检查：使用WorkManager在后台定期检查

### 2. 更新下载
- 使用Android DownloadManager下载APK文件
- 支持断点续传
- 显示下载进度
- 支持取消下载

### 3. 自动安装
- 下载完成后自动弹出安装界面
- 支持Android 7.0+的FileProvider安全机制
- 兼容Android 6.0及以下版本

### 4. 用户界面
- 在"我的"页面显示当前版本信息
- 显示可用更新信息（版本号、大小、更新内容）
- 显示下载进度和状态
- 错误信息提示

## 目录结构

```
src/main/java/com/csbaby/kefu/
├── data/
│   ├── model/OtaUpdate.kt          # OTA数据模型
│   ├── remote/OtaApiService.kt     # OTA API接口
│   ├── remote/MockOtaApiService.kt # 模拟API服务（测试用）
│   └── repository/OtaRepository.kt # OTA数据仓库
├── infrastructure/ota/
│   ├── OtaManager.kt              # OTA管理器（核心逻辑）
│   ├── OtaScheduler.kt            # 后台调度器
│   └── OtaUpdateWorker.kt         # WorkManager Worker
├── presentation/screens/profile/
│   ├── ProfileViewModel.kt        # 扩展了OTA功能
│   └── ProfileScreen.kt           # 添加了OTA更新UI
├── di/
│   └── OtaModule.kt               # Hilt依赖注入模块
└── KefuApplication.kt             # 应用启动时安排定期检查
```

## 配置步骤

### 1. 权限配置（已添加）
- `WRITE_EXTERNAL_STORAGE` - 写入外部存储（Android 13以下）
- `READ_EXTERNAL_STORAGE` - 读取外部存储（Android 13以下）
- `REQUEST_INSTALL_PACKAGES` - 请求安装包权限
- `DOWNLOAD_WITHOUT_NOTIFICATION` - 无通知下载
- `POST_NOTIFICATIONS` - 发送通知（Android 13+）

### 2. FileProvider配置（已添加）
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### 3. API服务器配置

#### 3.1 测试环境（当前使用模拟API）
当前使用`MockOtaApiService`模拟OTA检查，返回测试版本信息。

#### 3.2 生产环境配置
1. 创建真实的OTA API服务器，实现以下接口：
   - `GET /api/v1/ota/check` - 检查更新
   - `GET /api/v1/ota/latest` - 获取最新版本

2. 服务器响应格式：
```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "versionCode": 2,
    "versionName": "1.1.0",
    "downloadUrl": "https://your-server.com/app/kefu-v1.1.0.apk",
    "fileSize": 15728640,
    "md5": "a1b2c3d4e5f678901234567890123456",
    "releaseNotes": "版本更新内容...",
    "releaseDate": "2026-04-08",
    "isForceUpdate": false,
    "minRequiredVersion": 1
  }
}
```

3. 更新`NetworkModule.kt`中的Retrofit配置：
```kotlin
@Provides
@Singleton
fun provideOtaApiService(retrofit: Retrofit): OtaApiService {
    return retrofit.create(OtaApiService::class.java)
}
```

### 4. 构建配置

#### 4.1 版本号管理
在`app/build.gradle.kts`中：
```kotlin
defaultConfig {
    versionCode = 2  // 每次更新递增
    versionName = "1.1.0"
}
```

#### 4.2 依赖项（已添加）
```kotlin
dependencies {
    // OTA相关
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")
}
```

## 使用说明

### 1. 用户操作流程
1. 打开应用，进入"我的"页面
2. 查看"应用更新"卡片
3. 点击"检查更新"按钮
4. 如果有新版本，点击"下载更新"
5. 下载完成后，系统会自动弹出安装界面
6. 点击"安装"完成升级

### 2. 后台自动检查
- 应用启动30分钟后，安排首次检查
- 之后每24小时自动检查一次
- 仅在WiFi或移动数据连接时检查
- 电量充足时执行

### 3. 开发者测试

#### 3.1 模拟测试
当前已配置模拟更新：
- 当前版本：1.0.0 (versionCode=1)
- 可用更新：1.1.0 (versionCode=2)

要测试"已是最新版本"场景，临时修改`MockOtaApiService.kt`：
```kotlin
// 修改条件，让当前版本等于或高于模拟版本
if (versionCode < 2) {  // 改为 if (versionCode < 1)
```

#### 3.2 真实API测试
1. 部署OTA API服务器
2. 更新`NetworkModule.kt`中的Base URL
3. 在`OtaRepository.kt`中注释掉模拟服务代码，启用真实API：
```kotlin
override suspend fun checkForUpdate(currentVersionCode: Int): Result<OtaUpdate?> {
    return try {
        // 启用真实API（注释掉模拟服务）
        val response = apiService.checkForUpdate(currentVersionCode)
        // val mockService = MockOtaApiService()
        // val response = mockService.checkForUpdate(currentVersionCode)
        
        // ... 其他代码不变
    }
}
```

## 故障排除

### 1. 下载失败
- **权限问题**：确保已授予存储权限
- **存储空间不足**：检查设备存储空间
- **网络问题**：检查网络连接

### 2. 安装失败
- **Android版本兼容性**：确保APK与设备架构兼容
- **签名问题**：生产版本必须使用相同签名证书
- **权限不足**：确保已启用"未知来源应用"安装权限

### 3. 检查更新失败
- **服务器不可达**：检查API服务器状态
- **响应格式错误**：验证API响应格式
- **版本号配置错误**：确保versionCode正确递增

## 安全注意事项

### 1. APK安全
- 使用HTTPS下载APK
- 验证APK的MD5或SHA256签名
- 服务器端应验证APK完整性

### 2. 数据安全
- API请求应包含设备标识和当前版本
- 建议添加API密钥验证
- 记录更新统计信息

### 3. 用户隐私
- 不收集用户个人数据
- 更新检查只发送必要信息（版本号、设备型号）
- 符合Google Play政策要求

## 扩展功能建议

### 1. 高级功能
- 增量更新（减少下载大小）
- 灰度发布（部分用户先更新）
- A/B测试（不同版本对比）
- 回滚机制（出现问题可回退）

### 2. 管理后台
- 版本发布管理
- 更新统计数据
- 强制更新控制
- 发布说明编辑

### 3. 用户体验优化
- 后台静默下载（用户同意后）
- 智能更新时间（用户不使用时）
- 更新预告（提前通知）
- 更新反馈收集

## 支持与维护

### 1. 监控指标
- 更新检查成功率
- 下载完成率
- 安装成功率
- 用户反馈评分

### 2. 版本兼容性
- 确保新版本向后兼容
- 测试不同Android版本
- 验证不同设备分辨率
- 检查无障碍功能

### 3. 应急计划
- 紧急问题修复流程
- 版本撤回机制
- 用户支持渠道
- 故障排查指南

---

## 快速开始

1. **测试现有功能**：直接编译运行，在"我的"页面查看OTA功能
2. **配置生产服务器**：按"API服务器配置"章节部署服务器
3. **切换真实API**：按"开发者测试"章节启用真实API
4. **发布新版本**：递增versionCode，上传APK到服务器
5. **监控更新**：通过管理后台查看更新统计数据

如需进一步帮助，请参考代码注释或联系开发团队。