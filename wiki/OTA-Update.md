# OTA Update

## System Overview

OTA (Over-The-Air) 热更新系统实现应用内无感更新：

### Features

| 功能 | 说明 |
|------|------|
| 增量更新 | 支持差分包下载 |
| 强制更新 | 可配置强制升级 |
| 灰度发布 | 支持部分用户先更新 |
| 断点续传 | 支持下载中断恢复 |

## Update Flow

```
检查更新 → 下载APK → 验签 → 安装 → 重启
```

## Configuration

```kotlin
data class OtaConfig(
    val checkIntervalHours: Int = 24,
    val autoDownload: Boolean = true,
    val forceUpdate: Boolean = false
)
```

## Implementation

### UpdateChecker
```kotlin
class UpdateChecker @Inject constructor(
    private val api: OtaApiService
) {
    suspend fun checkForUpdate(): OtaUpdate? {
        val currentVersion = getCurrentVersion()
        return api.checkUpdate(currentVersion)
    }
}
```

### DownloadManager
- 后台下载
- 进度通知
- 断点续传

### Signature Verification
- APK签名校验
- 完整性检查

## Security

- HTTPS传输
- 签名验证
- 权限控制

## Related
- [[Cloud Sync]]
- [[Database Schema]]
- [[Coding Standards]]