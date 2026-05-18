@echo off
chcp 65001 >nul
echo ========================================
echo 客服小秘 - 修复并编译APK
echo ========================================
echo.

echo 步骤1: 设置环境变量...
set JAVA_HOME=C:\Users\13880\.jdks\openjdk-26
set ANDROID_HOME=D:\Android\SDK
set ANDROID_SDK_ROOT=D:\Android\SDK
set PATH=%JAVA_HOME%\bin;%PATH%

echo 步骤2: 清理项目...
call gradlew.bat clean --no-daemon

echo 步骤3: 尝试编译...
call gradlew.bat assembleDebug --no-daemon --stacktrace

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 编译成功！
    echo APK位置: app\build\outputs\apk\debug\app-debug.apk
    echo ========================================
    
    echo.
    dir "app\build\outputs\apk\debug\app-debug.apk"
    
    echo.
    echo 编译完成，可以安装测试！
    echo 如果要测试OTA功能，请进入应用的"我的"页面查看更新功能
) else (
    echo.
    echo ========================================
    echo 编译失败！
    echo ========================================
    echo.
    echo 建议运行原有的build.bat脚本，它应该能正常工作
)

pause