@echo off
chcp 65001 >nul
echo ========================================
echo csBaby - Auto Build + OSS Upload
echo ========================================
echo.

set JAVA_HOME=C:\Users\13880\.jdks\openjdk-26
set ANDROID_HOME=D:\Android\SDK
set ANDROID_SDK_ROOT=D:\Android\SDK
set PATH=%JAVA_HOME%\bin;%PATH%

echo [1/3] Incrementing version...
powershell -NoProfile -File "D:\workspace\workbuddy\csBaby\increment_version.ps1"
if errorlevel 1 (
    echo [ERROR] Version increment failed
    pause
    exit /b 1
)

echo.
echo [2/3] Building APK...
cd /d D:\workspace\workbuddy\csBaby
call gradlew.bat assembleDebug --no-daemon
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed
    pause
    exit /b 1
)

echo.
echo [OK] Build succeeded

echo.
echo [3/3] Uploading to OSS...
python "D:\workspace\workbuddy\csBaby\upload_oss.py"
if errorlevel 1 (
    echo.
    echo [ERROR] OSS upload failed
    pause
    exit /b 1
)

echo.
echo ========================================
echo ALL DONE!
echo ========================================
if exist "D:\workspace\workbuddy\csBaby\version-info.json" (
    powershell -NoProfile -Command "$v = Get-Content 'D:\workspace\workbuddy\csBaby\version-info.json' -Raw | ConvertFrom-Json; Write-Host ('  v' + $v.version_name + ' (' + $v.version_code + ') ' + [math]::Round($v.file_size/1MB,2) + ' MB'); Write-Host ('  ' + $v.apk_url)"
)
echo.
pause
