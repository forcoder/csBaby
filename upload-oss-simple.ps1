# 简化版OSS上传脚本
param(
    [string]$ApkPath = "app/build/outputs/apk/debug/app-debug.apk"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "阿里云OSS自动上传脚本（简化版）" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查APK文件是否存在
if (-not (Test-Path $ApkPath)) {
    Write-Host "错误：未找到APK文件: $ApkPath" -ForegroundColor Red
    Write-Host "请先运行编译脚本生成APK文件" -ForegroundColor Yellow
    exit 1
}

# 硬编码的OSS配置（测试用）
$endpoint = "apk-ota.oss-cn-shenzhen.aliyuncs.com"
$bucket = "apk-ota"
$accessKeyId = "LTAI5tMdpcET7GxLaJv96gV9"
$accessKeySecret = "yzIt7gKffm5ZpDMSiW7sXCYXPvATUx"
$appName = "kefu"

# 检查配置
if ([string]::IsNullOrWhiteSpace($accessKeyId) -or [string]::IsNullOrWhiteSpace($accessKeySecret)) {
    Write-Host "错误：OSS配置信息不完整" -ForegroundColor Red
    exit 1
}

Write-Host "OSS配置检查通过" -ForegroundColor Green

# 获取APK文件信息
Write-Host "分析APK文件..." -ForegroundColor Green
$apkFile = Get-Item $ApkPath
$fileSizeMB = [math]::Round($apkFile.Length / 1MB, 2)
Write-Host "  APK文件: $($apkFile.Name)" -ForegroundColor White
Write-Host "  文件大小: $fileSizeMB MB" -ForegroundColor White

# 计算MD5
Write-Host "计算文件MD5..." -ForegroundColor Green
$md5Hash = Get-FileHash -Path $ApkPath -Algorithm MD5
$md5 = $md5Hash.Hash.ToLower()
$md5Short = $md5.Substring(0, 8)
Write-Host "  MD5: $md5" -ForegroundColor White
Write-Host "  MD5（短）: $md5Short" -ForegroundColor White

# 使用默认版本信息
$versionName = "1.0.0"
$versionCode = "1"

# 尝试提取版本信息
try {
    $androidSdk = $env:ANDROID_HOME
    if (-not $androidSdk) {
        $androidSdk = $env:ANDROID_SDK_ROOT
    }
    
    if ($androidSdk) {
        $aaptPath = "$androidSdk\build-tools\*\aapt.exe"
        $aaptExe = Get-ChildItem -Path $aaptPath -ErrorAction SilentlyContinue | Select-Object -First 1
        
        if ($aaptExe -and (Test-Path $aaptExe.FullName)) {
            Write-Host "尝试使用aapt提取版本信息..." -ForegroundColor Yellow
            $aaptOutput = & $aaptExe.FullName dump badging $ApkPath 2>$null
            
            if ($LASTEXITCODE -eq 0) {
                if ($aaptOutput -match "versionName='([^']+)'") {
                    $versionName = $matches[1]
                }
                if ($aaptOutput -match "versionCode='([^']+)'") {
                    $versionCode = $matches[1]
                }
            }
        }
    }
} catch {
    Write-Host "版本提取失败，使用默认值" -ForegroundColor Yellow
}

Write-Host "  版本名称: $versionName" -ForegroundColor White
Write-Host "  版本代码: $versionCode" -ForegroundColor White

# 生成对象键
$dateStr = Get-Date -Format "yyyy-MM-dd"
$timeStr = Get-Date -Format "HHmmss"
$objectKey = "apks/$appName/v${versionName}_${versionCode}/$dateStr/${timeStr}_${md5Short}.apk"

Write-Host "生成OSS对象键..." -ForegroundColor Green
Write-Host "  对象键: $objectKey" -ForegroundColor White

# 构建上传URL
$uploadUrl = "https://$bucket.$endpoint/$objectKey"
Write-Host "构建上传URL..." -ForegroundColor Green
Write-Host "  上传URL: $uploadUrl" -ForegroundColor White

# 准备上传请求
$contentType = "application/vnd.android.package-archive"
$dateStrRfc = [DateTime]::UtcNow.ToString("r")
$contentMd5 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($md5))

# 生成签名
Write-Host "生成OSS签名..." -ForegroundColor Green

# 构建签名字符串
$canonicalResource = "/$bucket/$objectKey"
$canonicalString = "PUT`n$contentMd5`n$contentType`n$dateStrRfc`n$canonicalResource"

# 使用HMAC-SHA1生成签名
$hmacsha = New-Object System.Security.Cryptography.HMACSHA1
$hmacsha.Key = [System.Text.Encoding]::UTF8.GetBytes($accessKeySecret)
$signatureBytes = $hmacsha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($canonicalString))
$signatureBase64 = [Convert]::ToBase64String($signatureBytes)

$authorization = "OSS $accessKeyId`:$signatureBase64"

# 设置请求头
$headers = @{
    "Date" = $dateStrRfc
    "Content-Type" = $contentType
    "Content-MD5" = $contentMd5
    "Authorization" = $authorization
    "Host" = "$bucket.$endpoint"
}

# 执行上传
Write-Host "开始上传到阿里云OSS..." -ForegroundColor Cyan
try {
    $apkBytes = [System.IO.File]::ReadAllBytes($ApkPath)
    
    Write-Host "正在上传文件..." -ForegroundColor Yellow
    
    # 执行上传请求
    $response = Invoke-RestMethod -Uri $uploadUrl -Method Put -Headers $headers -Body $apkBytes -ContentType $contentType -SkipCertificateCheck
    
    Write-Host "上传成功！" -ForegroundColor Green
    Write-Host "  文件URL: $uploadUrl" -ForegroundColor White
    Write-Host "  文件大小: $fileSizeMB MB" -ForegroundColor White
    Write-Host "  MD5: $md5" -ForegroundColor White
    
    # 保存上传记录
    $uploadRecord = @{
        "timestamp" = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        "apk_file" = $ApkPath
        "version_name" = $versionName
        "version_code" = $versionCode
        "object_key" = $objectKey
        "upload_url" = $uploadUrl
        "md5" = $md5
        "file_size_mb" = $fileSizeMB
    }
    
    $recordJson = $uploadRecord | ConvertTo-Json
    $recordPath = "upload-history.json"
    
    # 读取历史记录
    $history = @()
    if (Test-Path $recordPath) {
        try {
            $existingJson = Get-Content $recordPath -Raw -ErrorAction Stop
            $history = $existingJson | ConvertFrom-Json
            if ($history -isnot [System.Array]) {
                $history = @($history)
            }
        } catch {
            Write-Host "  读取历史记录失败，创建新记录" -ForegroundColor Yellow
        }
    }
    
    # 添加新记录
    $history += $uploadRecord
    
    # 保存历史记录（最多保留10条）
    if ($history.Count -gt 10) {
        $history = $history | Select-Object -Last 10
    }
    
    $history | ConvertTo-Json | Set-Content $recordPath
    
    Write-Host "  上传记录已保存到: $recordPath" -ForegroundColor White
    
    # 生成版本信息文件
    $versionInfo = @{
        "version_name" = $versionName
        "version_code" = $versionCode
        "build_time" = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        "apk_url" = $uploadUrl
        "md5" = $md5
        "file_size" = $apkFile.Length
        "object_key" = $objectKey
    }
    
    $versionJson = $versionInfo | ConvertTo-Json
    $versionPath = "version-info.json"
    $versionJson | Set-Content $versionPath
    
    Write-Host "  版本信息已保存到: $versionPath" -ForegroundColor White
    
} catch {
    Write-Host "上传失败: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "错误详情: $($_.ErrorDetails)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "上传完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "现在可以访问以下URL下载APK：" -ForegroundColor Yellow
Write-Host "$uploadUrl" -ForegroundColor White
Write-Host ""

Write-Host "脚本执行完成！" -ForegroundColor Green