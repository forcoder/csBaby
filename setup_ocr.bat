@echo off
echo 🔧 搜狗常用语OCR提取工具 - 环境安装脚本
echo.

REM 检查Python
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ 未找到Python。请先安装Python 3.7+ 并添加到PATH。
    echo    下载地址: https://www.python.org/downloads/
    pause
    exit /b 1
)

echo ✅ Python 已安装

REM 安装Python依赖
echo.
echo 📦 安装Python依赖包...
pip install Pillow pytesseract

if errorlevel 1 (
    echo ❌ 依赖安装失败。请检查pip是否可用。
    pause
    exit /b 1
)

echo ✅ Python依赖安装完成
echo.

REM 检查Tesseract OCR
where tesseract >nul 2>&1
if errorlevel 1 (
    echo ⚠️  未找到Tesseract OCR引擎。
    echo.
    echo 📥 请手动安装Tesseract:
    echo    1. 下载安装包: https://github.com/UB-Mannheim/tesseract/wiki
    echo    2. 运行安装程序（默认安装到 C:\Program Files\Tesseract-OCR\）
    echo    3. 将安装目录添加到系统PATH
    echo       或运行脚本时使用 --tesseract-path 参数
    echo.
    echo 🌐 如果需要中文识别，请安装中文语言包：
    echo    在安装过程中勾选"Additional language data"，选择中文（简体）
    echo    或安装后下载 chi_sim.traineddata 放到 tessdata 目录
    echo.
    echo 📝 安装完成后可能需要重启命令行窗口。
    pause
    exit /b 0
)

echo ✅ Tesseract OCR 已安装
echo.
echo 🎉 环境准备完成！
echo.
echo 下一步：
echo   1. 将搜狗常用语截图放到 "screenshots" 文件夹中
echo   2. 运行: python extract_phrases_ocr.py
echo   3. 结果保存在 extracted_phrases.txt
pause