@echo off
chcp 65001 >nul
echo ========================================
echo 测试编译环境
echo ========================================
echo.

:: 检查Java版本
echo 检查Java版本：
java -version 2>&1 | findstr "version"
echo.

:: 尝试不同的Java路径
echo 尝试JDK 17路径：
if exist "C:\Users\13880\.jdks\ms-17.0.18\bin\java.exe" (
    echo JDK 17存在: C:\Users\13880\.jdks\ms-17.0.18\bin\java.exe
    "C:\Users\13880\.jdks\ms-17.0.18\bin\java.exe" -version 2>&1 | findstr "version"
) else (
    echo JDK 17不存在
)
echo.

:: 尝试build.bat中的Java路径
echo 尝试build.bat中的Java路径：
if exist "C:\Users\13880\.jdks\openjdk-26\bin\java.exe" (
    echo OpenJDK 26存在: C:\Users\13880\.jdks\openjdk-26\bin\java.exe
    "C:\Users\13880\.jdks\openjdk-26\bin\java.exe" -version 2>&1 | findstr "version"
) else (
    echo OpenJDK 26不存在
)
echo.

:: 设置JAVA_HOME为JDK 17
set JAVA_HOME=C:\Users\13880\.jdks\ms-17.0.18
set PATH=%JAVA_HOME%\bin;%PATH%
echo 设置JAVA_HOME为: %JAVA_HOME%
echo.

:: 检查Gradle包装器
echo 检查Gradle包装器版本：
if exist "gradlew.bat" (
    echo gradlew.bat存在
) else (
    echo gradlew.bat不存在
)
echo.

:: 显示当前配置
echo 当前配置：
echo Gradle版本: 8.2 (来自gradle-wrapper.properties)
echo AGP版本: 8.2.0 (来自build.gradle.kts)
echo JAVA_HOME: %JAVA_HOME%
echo.

pause