# 客服小秘 - 开发环境配置指南

## 当前状态

⚠️ **APK无法直接编译** - 当前环境缺少必要的开发工具。

## 需要的工具

### 1. JDK 17
Android Studio 需要的Java Development Kit。

**下载地址**：
- https://adoptium.net/temurin/releases/?version=17

**安装步骤**：
1. 下载 `jdk-17_x64_windows.exe`
2. 运行安装程序
3. 设置环境变量：
   ```
   JAVA_HOME = C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot
   PATH = %JAVA_HOME%\bin;%PATH%
   ```

### 2. Android SDK
Android应用开发包。

**安装方式一：Android Studio（推荐）**
1. 下载 Android Studio：https://developer.android.com/studio
2. 安装时选择 "Custom" 安装
3. 确保勾选：
   - Android SDK
   - Android SDK Platform 34
   - Android SDK Build-Tools

**安装方式二：独立SDK**
1. 下载 Command Line Tools：https://developer.android.com/studio#command-line-tools-only
2. 解压到 `D:\Android\SDK`
3. 运行以下命令安装组件：
   ```cmd
   cd D:\Android\SDK\cmdline-tools\latest\bin
   sdkmanager.bat "platforms;android-34" "build-tools;34.0.0"
   ```

## 编译APK

环境配置完成后，运行以下命令：

```cmd
cd D:\workspace\workbuddy\csBaby
.\gradlew.bat assembleDebug
```

APK文件将生成在：
```
app\build\outputs\apk\debug\app-debug.apk
```

## 一键安装到设备

```cmd
.\gradlew.bat installDebug
```

## 常见问题

### Q: 编译时提示 "JAVA_HOME is not set"
**解决**：确认JAVA_HOME环境变量已正确设置，重启终端窗口。

### Q: 提示 "SDK location not found"
**解决**：确保 `local.properties` 文件中的 `sdk.dir` 指向正确的Android SDK路径。

### Q: 编译失败，提示缺少某些依赖
**解决**：首次编译会自动下载依赖，确保网络连接正常。如果下载失败，可尝试配置国内镜像：
在 `gradle.properties` 中添加：
```properties
distributionUrl=https://mirrors.cloud.tencent.com/gradle/gradle-8.2-bin.zip
```
