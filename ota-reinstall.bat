@echo off
chcp 65001 >nul
echo ========================================
echo csBaby v1.4.8 直装脚本 (adb 卸载+重装)
echo ========================================
echo.

REM 检测 adb
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist %ADB% (
    echo [错误] 找不到 adb: %ADB%
    echo 修改脚本第 8 行的 ADB 路径, 指向您的 adb.exe
    pause
    exit /b 1
)

REM 1. 检测设备
echo [1/4] 检测设备...
%ADB% devices | findstr /R "device$" >nul
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 没有 adb 设备连接
    echo  - USB 调试已开?
    echo  - 数据线已连?
    echo  - 手机是否已授权 USB 调试?
    pause
    exit /b 1
)
echo ✓ 设备已连
echo.

REM 2. 卸载老 csBaby (debug 签名版, 不卸装不上 release)
echo [2/4] 卸载老 csBaby (com.csbaby.kefu)...
echo       会清本地数据, 云端 sync 保留
echo.
%ADB% shell pm uninstall com.csbaby.kefu
if %ERRORLEVEL% NEQ 0 (
    echo [警告] 卸载未成功, 这多半因为手机还没装 csBaby, 继续往下走
)
echo.

REM 3. 下载 v1.4.8 apk 到本地临时文件夹
echo [3/4] 下载 v1.4.8 release apk...
set APK_PATH=%TEMP%\csBaby-v1.4.8.apk
set APK_URL=https://shz.al/~csBabyApk_v19
curl -L -o "%APK_PATH%" "%APK_URL%" -w "  HTTP: %%{http_code} bytes: %%{size_download}\n"
echo.

REM 4. push apk 到手机 + 安装
echo [4/4] push 到手机 + adb install...
%ADB% push "%APK_PATH%" /data/local/tmp/csBaby-v1.4.8.apk
if %ERRORLEVEL% NEQ 0 (
    echo [错误] push 失败
    pause
    exit /b 1
)

%ADB% shell pm install /data/local/tmp/csBaby-v1.4.8.apk
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [错误] install 失败, 可能原因:
    echo  1. 释放包有问题 (查看 APK 大小 是否 ~11MB)
    echo  2. 手机上还有同名残留 (手动卸载后再试)
    echo  3. 手机空间不足
    pause
    exit /b 1
)

REM 清理
%ADB% shell rm /data/local/tmp/csBaby-v1.4.8.apk
del "%APK_PATH%" 2>nul

echo.
echo ========================================
echo ✓ 安装成功
echo ========================================
echo.
echo 启动 app 验证...
timeout /t 3 >nul
%ADB% shell am start -n com.csbaby.kefu/.MainActivity 2>nul
echo 启动指令已发, 您在手机上检查 app 是否正常运行
echo.

pause
