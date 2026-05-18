@echo off
chcp 65001 >nul

echo ========================================
echo 最简APK编译测试
echo ========================================
echo.

:: 显示当前配置
echo 配置检查:
echo - Java版本:
java -version 2>&1 | findstr "version"
echo.
echo - Gradle配置: Gradle 7.5 + AGP 7.4.2 + Kotlin 1.8.21
echo - compileSdk: 33, targetSdk: 33
echo - 应用ID: com.csbaby.kefu
echo - 版本: 1.1.0 (versionCode=2)
echo.

:: 尝试编译
echo 开始编译...
echo.
call gradlew.bat assembleDebug --no-daemon --stacktrace

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 编译成功！
    echo APK位置: app\build\outputs\apk\debug\app-debug.apk
    echo ========================================
    
    :: 显示APK信息
    if exist "app\build\outputs\apk\debug\app-debug.apk" (
        echo.
        echo APK文件信息:
        dir "app\build\outputs\apk\debug\app-debug.apk" | findstr "app-debug"
        echo.
        echo 编译完成，可以安装测试！
    )
) else (
    echo.
    echo ========================================
    echo 编译失败！
    echo ========================================
    echo.
    echo 建议：
    echo 1. 清理Gradle缓存: gradlew.bat clean
    echo 2. 删除.gradle目录后重试
    echo 3. 检查网络连接能否下载依赖
)

pause