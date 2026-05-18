@echo off
chcp 65001 >nul
echo ========================================
echo 客服小秘 - 编译并自动上传APK到阿里云OSS
echo ========================================
echo.

:: 设置环境变量
set JAVA_HOME=C:\Users\13880\.jdks\openjdk-26
set ANDROID_HOME=D:\Android\SDK
set ANDROID_SDK_ROOT=D:\Android\SDK
set PATH=%JAVA_HOME%\bin;%PATH%

:: 清理损坏的下载
echo 清理旧文件...
del "C:\Users\13880\.gradle\wrapper\dists\gradle-8.2-bin\bbg7u40eoinfdyxsxr3z4i7ta\*.*" /q 2>nul

:: 复制新下载的Gradle到缓存目录
echo 请将下载的 gradle-8.5-bin.zip 放到 C:\Users\13880\.gradle\wrapper\dists\gradle-8.2-bin\j2cdg1brpxvqbc9sxxdopggx\ 目录
echo 然后重命名为 gradle-8.2.zip
echo.

:: 检查配置文件
if not exist "oss-config.properties" (
    echo 错误：未找到 oss-config.properties 配置文件
    echo.
    echo 请按以下步骤操作：
    echo 1. 复制示例配置文件：
    echo    copy oss-config.properties.example oss-config.properties
    echo.
    echo 2. 编辑 oss-config.properties 文件：
    echo    - 填写您的阿里云OSS配置
    echo    - 确保 auto_upload.enabled=true
    echo.
    echo 3. 重新运行此脚本
    echo.
    pause
    exit /b 1
)

:: 编译APK
echo 开始编译...
cd /d D:\workspace\workbuddy\csBaby
call gradlew.bat assembleDebug --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo 编译失败，请检查错误信息。
    pause
    exit /b 1
)

echo.
echo ========================================
echo 编译成功！
echo APK位置: app\build\outputs\apk\debug\app-debug.apk
echo ========================================

:: 执行上传脚本
echo.
echo 开始上传到阿里云OSS...
echo.

:: 检查PowerShell版本
powershell -Command "if ($PSVersionTable.PSVersion.Major -ge 3) { exit 0 } else { exit 1 }"
if %ERRORLEVEL% NEQ 0 (
    echo 错误：需要PowerShell 3.0或更高版本
    echo 您的PowerShell版本过低，无法执行上传脚本
    echo.
    echo 请升级PowerShell或手动运行上传命令：
    echo   powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1"
    echo.
    pause
    exit /b 1
)

:: 执行上传脚本
echo 执行上传脚本...
powershell -ExecutionPolicy Bypass -File "upload-to-oss.ps1" -ForceUpload

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 上传成功！
    echo ========================================
    
    :: 显示上传历史
    if exist "upload-history.json" (
        echo.
        echo 最近上传记录：
        echo.
        powershell -Command "$history = Get-Content 'upload-history.json' -Raw | ConvertFrom-Json; if ($history -is [System.Array]) { $history = $history | Select-Object -Last 3 }; $history | ForEach-Object { Write-Host ('  ' + $_.timestamp + ' - v' + $_.version_name + ' (' + $_.version_code + ')') }"
    )
    
    if exist "version-info.json" (
        echo.
        echo 最新版本信息：
        echo.
        powershell -Command "$version = Get-Content 'version-info.json' -Raw | ConvertFrom-Json; Write-Host ('  版本: v' + $version.version_name + ' (' + $version.version_code + ')'); Write-Host ('  下载URL: ' + $version.apk_url); Write-Host ('  MD5: ' + $version.md5)"
    )
) else (
    echo.
    echo ========================================
    echo 上传失败，请检查错误信息
    echo ========================================
)

echo.
echo ========================================
echo 操作完成！
echo ========================================
echo.
pause