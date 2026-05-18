@echo off
chcp 65001 >nul
echo ========================================
echo 客服小秘 - Android SDK 自动安装脚本
echo ========================================
echo.

:: 创建目录
echo [1/4] 创建目录...
mkdir "D:\Android\SDK" 2>nul
mkdir "D:\temp" 2>nul

:: 下载JDK 17
echo.
echo [2/4] 下载 JDK 17...
echo    这可能需要几分钟，请耐心等待...
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.zip' -OutFile 'D:\temp\jdk17.zip'"

if errorlevel 1 (
    echo    GitHub下载失败，尝试腾讯云镜像...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://mirrors.tencent.com/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.zip' -OutFile 'D:\temp\jdk17.zip'"
)

:: 解压JDK
echo.
echo [3/4] 解压 JDK 17...
powershell -Command "Expand-Archive -Path 'D:\temp\jdk17.zip' -DestinationPath 'D:\Android' -Force"
move "D:\Android\jdk-17*" "D:\Android\jdk" 2>nul

:: 下载Android SDK Command Line Tools
echo.
echo [4/4] 下载 Android SDK Command Line Tools...
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile 'D:\temp\cmdline-tools.zip'"

:: 解压并配置SDK
echo.
echo 配置 Android SDK...
powershell -Command "Expand-Archive -Path 'D:\temp\cmdline-tools.zip' -DestinationPath 'D:\Android\SDK\cmdline-tools' -Force"
move "D:\Android\SDK\cmdline-tools\cmdline-tools" "D:\Android\SDK\cmdline-tools\latest" 2>nul

:: 安装SDK组件
echo.
echo 安装 SDK 组件 (platforms;android-34, build-tools;34.0.0)...
set JAVA_HOME=D:\Android\jdk
D:\Android\SDK\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=D:\Android\SDK "platforms;android-34" "build-tools;34.0.0"

:: 配置环境变量
echo.
echo 配置环境变量...
setx JAVA_HOME "D:\Android\jdk" /M
setx ANDROID_HOME "D:\Android\SDK" /M
setx ANDROID_SDK_ROOT "D:\Android\SDK" /M

:: 更新local.properties
echo sdk.dir=D\:\\Android\\SDK > "D:\workspace\workbuddy\csBaby\local.properties"

:: 清理
echo.
echo 清理临时文件...
del "D:\temp\jdk17.zip" 2>nul
del "D:\temp\cmdline-tools.zip" 2>nul
del "D:\temp\android-studio.exe" 2>nul

echo.
echo ========================================
echo 安装完成！
echo ========================================
echo.
echo 请重启终端后运行以下命令编译APK:
echo cd D:\workspace\workbuddy\csBaby
echo .\gradlew.bat assembleDebug
echo.
pause
