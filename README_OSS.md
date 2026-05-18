# 阿里云OSS自动上传系统

## 一句话说明
**编译完成后，自动将APK上传到阿里云OSS，并可选自动升级版本。**

## 三步快速开始

### 1. 准备配置文件
```bash
# 复制示例配置
copy oss-config.properties.example oss-config.properties

# 编辑配置文件（填写您的阿里云OSS信息）
notepad oss-config.properties
```

### 2. 编译并上传
```bash
# 方法一：使用专用脚本（推荐）
build-and-upload.bat

# 方法二：使用原有脚本（根据配置决定是否上传）
build.bat
```

### 3. 查看结果
- **上传成功**：查看 `upload-history.json` 和 `version-info.json`
- **下载APK**：从生成的URL下载测试

## 主要文件说明

| 文件 | 用途 |
|------|------|
| `oss-config.properties` | **配置文件**（不提交到Git）包含OSS密钥 |
| `oss-config.properties.example` | 配置示例文件（可提交到Git） |
| `upload-to-oss.ps1` | PowerShell上传脚本 |
| `build-and-upload.bat` | 编译+上传一键脚本 |
| `build.bat` | 原有编译脚本（集成上传功能） |

## 核心功能

### 🔧 配置管理
- **敏感信息外置**：OSS密钥从代码中提取到配置文件
- **安全示例**：提供不含敏感信息的示例文件
- **灵活配置**：支持启用/禁用自动上传和版本升级

### ⚡ 自动上传
- **编译即上传**：编译成功后自动上传到OSS
- **智能识别**：自动提取APK版本信息
- **文件命名**：使用`时间_MD5`格式，避免重复

### 🔄 版本管理
- **自动升级**：上传成功后自动增加版本号
- **历史记录**：保存最近20次上传记录
- **版本信息**：生成包含URL和MD5的版本文件

## 使用场景

### 1. 日常开发测试
```bash
# 每次修改代码后
build-and-upload.bat
# 同事通过生成的URL下载测试
```

### 2. 团队协作
1. 开发者上传APK到OSS
2. 生成版本信息文件
3. 测试人员从版本文件获取下载链接
4. 自动记录上传历史

### 3. 持续集成
可集成到CI/CD流水线，实现自动化部署。

## 配置示例

```properties
# 必须配置
oss.endpoint=apk-ota.oss-cn-shenzhen.aliyuncs.com
oss.bucket=apk-ota
oss.access_key_id=your_key_id
oss.access_key_secret=your_key_secret

# 可选配置
app.name=kefu
auto_upload.enabled=true
auto_upload.auto_upgrade=true
```

## 常见问题

### Q1: 上传失败怎么办？
- 检查OSS配置是否正确
- 检查AccessKey是否有上传权限
- 查看错误信息，通常会有明确提示

### Q2: 如何跳过自动上传？
- 方法1：配置文件中设置 `auto_upload.enabled=false`
- 方法2：使用原有 `build.bat` 脚本

### Q3: 如何手动上传？
```bash
powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1"
```

### Q4: 版本号如何管理？
- 自动升级：每次上传后版本代码+1，版本名称最后一位+1
- 手动控制：设置 `auto_upload.auto_upgrade=false`

## 高级用法

### 自定义APK路径
```bash
powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1" -ApkPath "custom/path/app.apk"
```

### 批量处理
可结合其他脚本实现批量编译和上传。

### 集成到现有流程
```bash
# 在现有构建脚本中添加
call gradlew.bat assembleDebug
if %ERRORLEVEL% EQU 0 (
    powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1"
)
```

## 安全提醒

⚠️ **重要**：
1. 不要将 `oss-config.properties` 提交到版本控制
2. 使用RAM子账户，限制权限
3. 定期更新AccessKey

## 技术支持

- 详细文档：`OSS_AUTO_UPLOAD_GUIDE.md`
- 示例配置：`oss-config.properties.example`
- 脚本帮助：运行 `upload-to-oss.ps1 -?`（查看帮助）

---
**一句话总结**：配置一次，从此编译完就能直接测试！