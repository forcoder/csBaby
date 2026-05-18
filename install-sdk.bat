@echo off
chcp 65001 >nul
echo ========================================
echo 客服小秘 - 一键安装Android SDK
echo ========================================
echo.

:: 设置环境变量
set JAVA_HOME=C:\Users\13880\.jdks\openjdk-26
set ANDROID_HOME=D:\Android\SDK
set ANDROID_SDK_ROOT=D:\Android\SDK

:: 安装SDK组件
echo 正在安装 Android SDK 组件...
echo 这可能需要10-20分钟，请耐心等待...
echo.

D:\Android\SDK\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=D:\Android\SDK "platforms;android-34" "build-tools;34.0.0"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SDK 安装成功！
    echo ========================================
    echo.
    echo 现在可以编译APK了：
    echo cd D:\workspace\workbuddy\csBaby
    echo .\gradlew.bat assembleDebug
    echo.
) else (
    echo.
    echo SDK 安装可能遇到问题，请检查网络连接后重试。
)
pause
