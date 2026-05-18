# 阿里云OSS手动版本管理 - 集成测试方案

## 🧪 测试目标

验证阿里云OSS手动版本管理功能的完整性和可用性，包括：
1. OSS配置验证
2. APK文件上传
3. 版本列表管理
4. OSS更新检查
5. 与现有OTA系统的兼容性

## 🔧 测试环境准备

### 1. 项目配置检查
```bash
# 检查Gradle依赖
# build.gradle.kts 应该包含：
# - WorkManager (已存在)
# - Timber (已存在)
# - Hilt (已存在)
# - Compose (已存在)

# 检查AndroidManifest权限
# 应该包含：
# - READ_EXTERNAL_STORAGE
# - WRITE_EXTERNAL_STORAGE
# - REQUEST_INSTALL_PACKAGES
# - INTERNET
```

### 2. OSS配置检查
```kotlin
// 检查 AliOssManager 中的配置
// 应该包含正确的：
// - ENDPOINT
// - BUCKET  
// - ACCESS_KEY_ID
// - ACCESS_KEY_SECRET
```

## 📋 测试用例

### 测试用例 1: OSS配置验证
**测试步骤**:
1. 启动应用
2. 进入"我的"页面
3. 查看"手动版本管理"卡片
4. 点击"验证配置"按钮

**预期结果**:
- OSS配置状态显示"已配置"
- 状态消息显示"OSS配置验证成功"

**验证方法**:
- 检查UI显示是否正确
- 查看Logcat日志：`AliyunOssManager: 阿里云OSS配置验证成功`

### 测试用例 2: 手动APK上传流程
**测试步骤**:
1. 准备测试APK文件（任意有效的APK）
2. 点击"上传APK"按钮
3. 选择测试APK文件
4. 填写版本信息：
   - 版本号: 2
   - 版本名称: 1.1.0
   - 更新说明: 测试版本
5. 点击"开始上传"
6. 观察上传进度和状态

**预期结果**:
- 显示上传进度条（0% → 100%）
- 上传完成显示"上传成功！APK已保存到阿里云OSS"
- OSS更新卡片显示新版本信息

**验证方法**:
- 检查UI进度更新
- 查看上传状态消息
- 检查Logcat日志：`AliyunOssManager: APK上传成功`

### 测试用例 3: 版本列表查看
**测试步骤**:
1. 点击"版本列表"按钮
2. 查看弹出的版本列表对话框
3. 检查版本信息显示

**预期结果**:
- 对话框显示版本列表
- 每个版本显示：版本号、版本名称、上传时间、文件大小
- 显示上传者和下载次数

**验证方法**:
- 检查对话框是否正确显示
- 验证版本信息格式

### 测试用例 4: OSS更新检查
**测试步骤**:
1. 点击"检查OSS更新"按钮
2. 等待检查完成
3. 查看检查结果

**预期结果**:
- 显示检查状态消息
- 如果有新版本，显示更新信息
- 如果已是最新版本，显示相应消息

**验证方法**:
- 检查状态消息更新
- 验证更新信息显示

### 测试用例 5: 强制更新设置
**测试步骤**:
1. 打开版本列表
2. 找到测试版本（versionCode=2）
3. 点击"设强制"按钮
4. 再次打开版本列表检查状态

**预期结果**:
- 版本状态更新为强制更新
- UI显示强制更新标记
- 状态消息显示"已设置为强制更新"

**验证方法**:
- 检查版本状态更新
- 验证强制更新标记显示

### 测试用例 6: 与现有OTA系统兼容性
**测试步骤**:
1. 点击现有的"检查更新"按钮
2. 点击新的"检查OSS更新"按钮
3. 比较两种检查方式的结果

**预期结果**:
- 两种检查方式都能正常工作
- 更新信息显示格式一致
- 下载和安装流程相同

**验证方法**:
- 比较UI显示
- 测试下载功能

## 🔍 自动化测试代码（可选）

```kotlin
// 这是一个示例测试类，可以添加到测试目录
package com.csbaby.kefu.presentation.screens.profile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualVersionManagementTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testOssConfigValidation() {
        composeTestRule.setContent {
            // 设置测试内容
        }
        
        // 验证OSS配置显示
        composeTestRule.onNodeWithText("手动版本管理").assertIsDisplayed()
        composeTestRule.onNodeWithText("验证配置").performClick()
        composeTestRule.onNodeWithText("已配置").assertIsDisplayed()
    }
    
    @Test 
    fun testOssUpdateCheck() {
        composeTestRule.setContent {
            // 设置测试内容
        }
        
        // 测试OSS更新检查
        composeTestRule.onNodeWithText("检查OSS更新").performClick()
        composeTestRule.onNodeWithText("正在检查OSS更新...").assertIsDisplayed()
        // 等待检查完成，验证结果
    }
}
```

## 📊 测试数据准备

### 测试APK文件
1. **文件1**: `test-app-v2.apk` (versionCode=2, versionName="1.1.0")
2. **文件2**: `test-app-v3.apk` (versionCode=3, versionName="1.2.0")

### 测试版本信息
```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "releaseNotes": "测试版本\n1. OSS集成测试\n2. 手动上传功能\n3. 版本管理测试",
  "isForceUpdate": false
}
```

## ⚠️ 常见问题测试

### 问题1: 网络连接失败
**测试方法**:
1. 关闭设备网络
2. 尝试上传APK
3. 尝试检查更新

**预期结果**:
- 显示网络错误消息
- 上传和检查操作失败并显示友好错误信息

### 问题2: 无效APK文件
**测试方法**:
1. 选择非APK文件（如.txt文件）
2. 尝试上传

**预期结果**:
- 显示文件格式错误
- 阻止上传无效文件

### 问题3: OSS配置错误
**测试方法**:
1. 临时修改错误的AK/SK
2. 尝试验证配置

**预期结果**:
- 配置验证失败
- 显示配置错误信息

## 📈 性能测试

### 上传性能
- **小文件** (<10MB): 应该在30秒内完成
- **中文件** (10-50MB): 应该在2分钟内完成  
- **大文件** (>50MB): 应该有进度显示和取消选项

### 内存使用
- 上传过程内存增长不应超过50MB
- 完成上传后内存应释放

### 网络使用
- 上传应该使用合理的分块大小
- 支持断点续传（如果服务器支持）

## 🎯 验收标准

### 必须完成的功能
- [x] OSS配置验证
- [x] APK文件选择
- [x] 版本信息填写
- [x] OSS文件上传
- [x] 上传进度显示
- [x] 版本列表查看
- [x] OSS更新检查
- [x] 强制更新设置

### 用户体验要求
- [x] 上传过程有明确的进度反馈
- [x] 错误信息友好易懂
- [x] 操作流程直观简单
- [x] 与现有OTA系统UI风格一致

### 技术质量要求
- [x] 代码结构清晰，注释完整
- [x] 错误处理完备
- [x] 内存管理合理
- [x] 网络请求优化

## 📝 测试记录模板

```markdown
### 测试日期: [日期]
### 测试版本: [应用版本]
### 测试设备: [设备型号]

#### 测试用例执行结果:
1. OSS配置验证: [通过/失败]
   - 备注: [问题描述]
   
2. 手动APK上传: [通过/失败]
   - 上传时间: [时间]
   - 文件大小: [大小]
   - 备注: [问题描述]
   
3. 版本列表查看: [通过/失败]
   - 显示版本数: [数量]
   - 备注: [问题描述]
   
4. OSS更新检查: [通过/失败]
   - 检查时间: [时间]
   - 结果: [有更新/无更新]
   - 备注: [问题描述]
   
5. 强制更新设置: [通过/失败]
   - 设置响应时间: [时间]
   - 备注: [问题描述]
   
6. OTA兼容性: [通过/失败]
   - 备注: [问题描述]

#### 发现的问题:
1. [问题1描述]
   - 严重程度: [高/中/低]
   - 状态: [未修复/已修复/无需修复]
   
2. [问题2描述]
   - 严重程度: [高/中/低]
   - 状态: [未修复/已修复/无需修复]

#### 测试结论:
[通过/不通过/有条件通过]

#### 建议:
[改进建议]
```

## 🔧 故障排查指南

### 如果上传失败
1. **检查网络连接**: 确保设备可以访问阿里云OSS
2. **验证OSS配置**: 检查AK/SK是否正确
3. **检查文件权限**: 确保应用有存储权限
4. **查看日志**: 检查Logcat中的详细错误信息

### 如果版本列表为空
1. **检查上传是否成功**: 确认APK已成功上传
2. **刷新列表**: 尝试重新获取版本列表
3. **检查网络连接**: 确保可以访问OSS

### 如果配置验证失败
1. **检查AK/SK**: 确认AccessKeyId和AccessKeySecret正确
2. **检查Bucket**: 确认Bucket名称正确且存在
3. **检查Endpoint**: 确认Endpoint格式正确

---

## 🎉 测试完成标志

当所有测试用例通过，且满足以下条件时，可以认为集成测试完成：

✅ **功能完整性**: 所有计划的功能都实现并测试通过  
✅ **用户体验**: 操作流程顺畅，界面友好  
✅ **性能要求**: 上传和检查性能满足要求  
✅ **错误处理**: 各种异常情况都有妥善处理  
✅ **兼容性**: 与现有OTA系统完美兼容  

**测试签名**: ____________________  
**测试日期**: ____________________