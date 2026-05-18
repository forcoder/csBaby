# 阿里云OSS自动上传配置指南

## 概述

本系统实现了APK编译完成后自动上传到阿里云OSS的功能，支持自动版本升级。通过配置文件管理OSS敏感信息，避免硬编码在代码中。

## 快速开始

### 1. 配置文件设置

1. 复制示例配置文件：
   ```bash
   copy oss-config.properties.example oss-config.properties
   ```

2. 编辑配置文件 `oss-config.properties`，填写您的阿里云OSS信息：
   ```properties
   # OSS域名（从阿里云控制台获取）
   oss.endpoint=your-oss-endpoint.aliyuncs.com
   
   # Bucket名称
   oss.bucket=your-bucket-name
   
   # AccessKey ID（从阿里云RAM控制台获取）
   oss.access_key_id=your_access_key_id
   
   # AccessKey Secret（从阿里云RAM控制台获取）
   oss.access_key_secret=your_access_key_secret
   
   # 应用名称（用于构建文件路径）
   app.name=your_app_name
   
   # APK文件夹路径
   oss.apk_folder=apks/
   
   # 是否启用自动上传（true/false）
   auto_upload.enabled=true
   
   # 自动上传后是否自动升级版本号
   auto_upload.auto_upgrade=true
   ```

### 2. 使用方式

#### 方式一：普通编译（可选自动上传）
运行普通编译脚本，如果配置文件启用了自动上传，编译成功后会询问是否上传：
```bash
build.bat
```

#### 方式二：编译并强制上传
运行专门的上传脚本，编译成功后强制上传到OSS：
```bash
build-and-upload.bat
```

#### 方式三：仅上传现有APK
如果已有编译好的APK文件，可以直接上传：
```bash
powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1"
```

### 3. 配置参数说明

| 参数 | 说明 | 示例值 |
|------|------|--------|
| `oss.endpoint` | OSS域名 | `apk-ota.oss-cn-shenzhen.aliyuncs.com` |
| `oss.bucket` | Bucket名称 | `apk-ota` |
| `oss.access_key_id` | AccessKey ID | `LTAI5tMdpcET7GxLaJv96gV9` |
| `oss.access_key_secret` | AccessKey Secret | `yzIt7gKffm5ZpDMSiW7sXCYXPvATUx` |
| `app.name` | 应用名称（用于文件路径） | `kefu` |
| `oss.apk_folder` | OSS中APK存放的文件夹 | `apks/` |
| `auto_upload.enabled` | 是否启用自动上传 | `true` |
| `auto_upload.auto_upgrade` | 上传后是否自动升级版本号 | `true` |

## 文件路径规则

上传到OSS的文件路径格式为：
```
{apk_folder}/{app_name}/v{version_name}_{version_code}/{date}/{time}_{md5_short}.apk
```

示例：
```
apks/kefu/v1.2.3_45/2026-04-08/143025_a1b2c3d4.apk
```

## 版本自动升级

如果 `auto_upload.auto_upgrade=true`，上传成功后会自动更新 `build.gradle.kts` 中的版本号：

1. **版本代码（versionCode）**：自动加1
2. **版本名称（versionName）**：最后一位数字加1
   - 例如：`1.2.3` → `1.2.4`

## 文件生成

上传过程中会生成以下文件：

### 1. 上传历史记录 (`upload-history.json`)
记录最近20次上传的历史，包含时间、版本、URL等信息。

### 2. 版本信息文件 (`version-info.json`)
包含最新版本的详细信息，便于其他系统读取。

### 3. 配置文件示例 (`oss-config.properties.example`)
不含敏感信息的配置示例，方便新用户参考。

## 错误排查

### 常见问题

#### 1. 上传失败：认证错误
- 检查 `oss.access_key_id` 和 `oss.access_key_secret` 是否正确
- 检查AccessKey是否有足够的权限（PutObject权限）

#### 2. 上传失败：Bucket不存在
- 检查 `oss.bucket` 名称是否正确
- 检查Bucket是否在正确的Region

#### 3. 自动上传未执行
- 检查 `auto_upload.enabled` 是否设置为 `true`
- 检查配置文件是否存在
- 检查PowerShell版本（需要3.0+）

#### 4. 版本提取失败
- 确保Android SDK已正确安装
- 检查aapt工具路径

### 手动测试

可以手动测试上传功能：
```bash
# 直接运行上传脚本（需先编译生成APK）
powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1"

# 强制上传（忽略配置文件中的auto_upload.enabled设置）
powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1" -ForceUpload
```

## 安全建议

1. **不要提交敏感信息到版本控制**
   - 确保 `oss-config.properties` 在 `.gitignore` 中
   - 只提交 `oss-config.properties.example`

2. **使用RAM子账户**
   - 创建专门的RAM用户用于APK上传
   - 只授予必要的OSS权限

3. **定期更新AccessKey**
   - 定期更换AccessKey，增强安全性

## 高级配置

### 1. 自定义APK路径
```bash
powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1" -ApkPath "path/to/your.apk"
```

### 2. 自定义配置文件路径
```bash
powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1" -ConfigPath "path/to/config.properties"
```

### 3. 禁用自动版本升级
在配置文件中设置：
```properties
auto_upload.auto_upgrade=false
```

## 集成到CI/CD

可以将上传脚本集成到CI/CD流程中：

```bash
# 编译
gradlew assembleDebug

# 上传
powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1"
```

## 支持的功能

✅ 配置文件管理敏感信息  
✅ 自动上传APK到OSS  
✅ 自动版本升级  
✅ 上传历史记录  
✅ 版本信息文件生成  
✅ 多种使用方式  
✅ 错误处理和日志  
✅ 安全建议和最佳实践  

## 联系方式

如有问题，请检查：
1. 配置文件是否正确
2. OSS权限是否足够
3. 网络连接是否正常
4. PowerShell版本是否支持

如需进一步帮助，请参考阿里云OSS官方文档或联系技术支持。