# 搜狗输入法常用语OCR提取指南

## 📋 概述

通过手机截图 + OCR 批量提取搜狗输入法常用语（共193条），解决输入法悬浮窗无法通过UI自动化直接访问的问题。

## 🛠️ 环境准备

### 1. 安装Python依赖
```bash
# 方法1：运行安装脚本（Windows）
setup_ocr.bat

# 方法2：手动安装
pip install Pillow pytesseract
```

### 2. 安装Tesseract OCR引擎
**Windows用户**：
1. 下载安装包：https://github.com/UB-Mannheim/tesseract/wiki
2. 运行安装程序（默认安装到 `C:\Program Files\Tesseract-OCR\`）
3. **重要**：安装时勾选"Additional language data"，选择**中文（简体）**
4. 或将安装目录添加到系统PATH

**验证安装**：
```bash
tesseract --version
```

## 📷 截图准备

1. **进入常用语界面**：
   - 打开微信/备忘录等应用，呼出搜狗输入法键盘
   - 点击键盘左上角工具图标
   - 找到"常用语"并点击进入

2. **截图要求**：
   - 确保屏幕亮度足够，文字清晰
   - **建议只截取常用语列表区域**（避开状态栏、导航栏）
   - 可能需要**多张截图**覆盖全部193条（上下滚动截屏）

3. **保存截图**：
   - 将截图保存为PNG或JPG格式
   - 放入本目录下的 `screenshots` 文件夹
   - 或自定义文件夹路径

## 🚀 运行提取

### 基本用法
```bash
python extract_phrases_ocr.py
```

### 高级选项
```bash
# 指定截图文件夹
python extract_phrases_ocr.py --input "my_screenshots"

# 指定输出文件
python extract_phrases_ocr.py --output "my_phrases.txt"

# 指定Tesseract路径（如果不在PATH中）
python extract_phrases_ocr.py --tesseract-path "C:\Program Files\Tesseract-OCR\tesseract.exe"

# 指定语言（简体中文+英文）
python extract_phrases_ocr.py --lang "chi_sim+eng"
```

## 📁 文件结构

```
项目目录/
├── extract_phrases_ocr.py    # OCR提取主脚本
├── setup_ocr.bat             # 环境安装脚本（Windows）
├── README_OCR.md            # 本指南
├── screenshots/             # 截图存放文件夹（手动创建）
│   ├── screenshot1.png
│   ├── screenshot2.png
│   └── ...
└── extracted_phrases.txt    # 输出文件（运行后生成）
```

## 🔧 故障排除

### 问题1：Tesseract未找到
**错误信息**：`pytesseract.pytesseract.TesseractNotFoundError`

**解决方案**：
1. 确认Tesseract已安装
2. 使用 `--tesseract-path` 参数指定路径
3. 或将Tesseract安装目录添加到系统PATH

### 问题2：识别率低
**可能原因**：
- 截图模糊或反光
- 文字区域未正确截取
- 未安装中文语言包

**解决方案**：
1. 重新截图，确保文字清晰
2. 只截取常用语列表区域（裁剪掉无关部分）
3. 安装Tesseract中文语言包

### 问题3：提取到无关文本
**原因**：OCR识别了状态栏、按钮等文字

**解决方案**：
- 脚本已内置过滤规则，会自动忽略常见界面元素
- 可手动编辑输出文件删除无关行

## 📝 结果校对

OCR识别可能存在少量误差，建议：

1. 打开生成的 `extracted_phrases.txt`
2. 逐条核对常用语
3. 修正识别错误的文字
4. 补充缺失的条目

## ⚡ 备选方案

如果OCR方案效果不佳，可考虑：

1. **方案3（坐标记录法）**：手动点击每条常用语，脚本记录坐标并提取文本（准确率100%）
2. **手动复制**：直接在手机上一一复制粘贴到文本文件

## 📞 支持

如需进一步帮助，请提供：
- 截图示例
- 错误信息截图
- 设备型号和系统版本