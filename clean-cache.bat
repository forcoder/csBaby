@echo off
chcp 65001 >nul

echo ========================================
echo 清理编译缓存
echo ========================================
echo.

echo 1. 清理Gradle构建缓存...
if exist "build" rmdir /s /q "build"
if exist "app\build" rmdir /s /q "app\build"

echo 2. 清理Gradle包装器缓存...
if exist ".gradle" rmdir /s /q ".gradle"

echo 3. 运行Gradle清理...
call gradlew.bat clean --no-daemon

echo.
echo 清理完成！
echo.
pause