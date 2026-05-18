# PowerShell脚本：自动上传APK到阿里云OSS
# 使用方法：在编译完成后运行此脚本自动上传APK

param(
    [string]$ApkPath = "app/build/outputs/apk/debug/app-debug.apk",
    [string]$ConfigPath = "oss-config.properties",
    [switch]$ForceUpload = $false
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "阿里云OSS自动上传脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查APK文件是否存在
if (-not (Test-Path $ApkPath)) {
    Write-Host "错误：未找到APK文件: $ApkPath" -ForegroundColor Red
    Write-Host "请先运行编译脚本生成APK文件" -ForegroundColor Yellow
    exit 1
}

# 检查配置文件是否存在
if (-not (Test-Path $ConfigPath)) {
    Write-Host "错误：未找到配置文件: $ConfigPath" -ForegroundColor Red
    Write-Host "请创建配置文件或使用示例文件创建" -ForegroundColor Yellow
    Write-Host "示例：copy oss-config.properties.example oss-config.properties" -ForegroundColor Yellow
    exit 1
}

# 加载配置文件
Write-Host "加载配置文件: $ConfigPath" -ForegroundColor Green
$config = @{}
Get-Content $ConfigPath | ForEach-Object {
    if ($_ -match "^\s*([^#].*?)\s*=\s*(.*?)\s*$") {
        $config[$matches[1]] = $matches[2]
    }
}

# 检查必要的配置项
$requiredConfigs = @("oss.endpoint", "oss.bucket", "oss.access_key_id", "oss.access_key_secret")
foreach ($key in $requiredConfigs) {
    if (-not $config.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($config[$key])) {
        Write-Host "错误：配置文件缺少必要的配置项: $key" -ForegroundColor Red
        exit 1
    }
}

# 检查是否启用自动上传
if ($config["auto_upload.enabled"] -eq "false" -and -not $ForceUpload) {
    Write-Host "提示：自动上传已禁用，跳过上传" -ForegroundColor Yellow
    Write-Host "如需上传，请在配置中设置 auto_upload.enabled=true 或使用 -ForceUpload 参数" -ForegroundColor Yellow
    exit 0
}

# 获取APK文件信息
Write-Host "分析APK文件..." -ForegroundColor Green
$apkFile = Get-Item $ApkPath
$fileSizeMB = [math]::Round($apkFile.Length / 1MB, 2)
Write-Host "  APK文件: $($apkFile.Name)" -ForegroundColor White
Write-Host "  文件大小: $fileSizeMB MB" -ForegroundColor White
Write-Host "  修改时间: $($apkFile.LastWriteTime)" -ForegroundColor White

# 计算MD5
Write-Host "计算文件MD5..." -ForegroundColor Green
$md5Hash = Get-FileHash -Path $ApkPath -Algorithm MD5
$md5 = $md5Hash.Hash.ToLower()
Write-Host "  MD5: $md5" -ForegroundColor White

# 从APK文件中提取版本信息
Write-Host "提取APK版本信息..." -ForegroundColor Green
try {
    # 尝试使用aapt工具提取版本信息
    $androidSdk = $env:ANDROID_HOME
    if (-not $androidSdk) {
        $androidSdk = $env:ANDROID_SDK_ROOT
    }
    
    $aaptPath = "$androidSdk\build-tools\*\aapt.exe"
    $aaptExe = Get-ChildItem -Path $aaptPath | Select-Object -First 1
    
    if ($aaptExe -and (Test-Path $aaptExe.FullName)) {
        $aaptOutput = & $aaptExe.FullName dump badging $ApkPath
        $versionName = ($aaptOutput | Select-String "versionName='([^']+)'").Matches.Groups[1].Value
        $versionCode = ($aaptOutput | Select-String "versionCode='([^']+)'").Matches.Groups[1].Value
        $packageName = ($aaptOutput | Select-String "package: name='([^']+)'").Matches.Groups[1].Value
        
        Write-Host "  应用包名: $packageName" -ForegroundColor White
        Write-Host "  版本名称: $versionName" -ForegroundColor White
        Write-Host "  版本代码: $versionCode" -ForegroundColor White
    } else {
        # 如果没有aapt，使用默认值
        $versionName = "1.0.0"
        $versionCode = "1"
        $packageName = $config["app.name"] ?? "kefu"
        Write-Host "  警告：未找到aapt工具，使用默认版本信息" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  警告：提取版本信息失败，使用默认值" -ForegroundColor Yellow
    $versionName = "1.0.0"
    $versionCode = "1"
    $packageName = $config["app.name"] ?? "kefu"
}

# 生成对象键
$appName = $config["app.name"] ?? "kefu"
$dateStr = Get-Date -Format "yyyy-MM-dd"
$timeStr = Get-Date -Format "HHmmss"
$md5Short = $md5.Substring(0, 8)
$objectKey = "apks/$appName/v${versionName}_${versionCode}/$dateStr/${timeStr}_${md5Short}.apk"

Write-Host "生成OSS对象键..." -ForegroundColor Green
Write-Host "  对象键: $objectKey" -ForegroundColor White

# 构建上传URL
$endpoint = $config["oss.endpoint"]
$bucket = $config["oss.bucket"]
$uploadUrl = "https://$bucket.$endpoint/$objectKey"

Write-Host "构建上传URL..." -ForegroundColor Green
Write-Host "  上传URL: $uploadUrl" -ForegroundColor White

# 准备上传请求
Write-Host "准备上传请求..." -ForegroundColor Green
$contentType = "application/vnd.android.package-archive"
$dateStrRfc = [DateTime]::UtcNow.ToString("r")
$contentMd5 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($md5))

# 生成签名
Write-Host "生成OSS签名..." -ForegroundColor Green
$accessKeyId = $config["oss.access_key_id"]
$accessKeySecret = $config["oss.access_key_secret"]

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
        $existingJson = Get-Content $recordPath -Raw
        $history = $existingJson | ConvertFrom-Json
        if ($history -isnot [System.Array]) {
            $history = @($history)
        }
    }
    
    # 添加新记录
    $history += $uploadRecord
    
    # 保存历史记录（最多保留20条）
    if ($history.Count -gt 20) {
        $history = $history | Select-Object -Last 20
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
    Write-Host "上传失败: $_" -ForegroundColor Red
    Write-Host "错误详情: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "上传完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查是否自动升级版本
if ($config["auto_upload.auto_upgrade"] -eq "true") {
    Write-Host "检测到自动升级版本配置，开始升级版本..." -ForegroundColor Yellow
    
    # 更新build.gradle.kts中的版本号
    $gradleFile = "build.gradle.kts"
    if (Test-Path $gradleFile) {
        $gradleContent = Get-Content $gradleFile
        
        # 增加版本代码
        $newVersionCode = [int]$versionCode + 1
        
        # 解析版本名称（如1.2.3）
        $versionParts = $versionName.Split('.')
        if ($versionParts.Count -eq 3) {
            $major = [int]$versionParts[0]
            $minor = [int]$versionParts[1]
            $patch = [int]$versionParts[2] + 1
            
            $newVersionName = "$major.$minor.$patch"
        } else {
            $newVersionName = $versionName
        }
        
        # 更新文件内容
        $newContent = @()
        foreach ($line in $gradleContent) {
            if ($line -match "versionCode\s+(\d+)") {
                $newContent += $line -replace "versionCode\s+(\d+)", "versionCode $newVersionCode"
            } elseif ($line -match "versionName\s+\""([^\""]+)\""") {
                $newContent += $line -replace "versionName\s+\""([^\""]+)\""", "versionName `"$newVersionName`""
            } else {
                $newContent += $line
            }
        }
        
        $newContent | Set-Content $gradleFile -Encoding UTF8
        
        Write-Host "  版本已升级:" -ForegroundColor White
        Write-Host "    旧版本: v$versionName ($versionCode)" -ForegroundColor White
        Write-Host "    新版本: v$newVersionName ($newVersionCode)" -ForegroundColor White
        Write-Host "  请重新编译以使用新版本" -ForegroundColor Yellow
    } else {
        Write-Host "  警告：未找到build.gradle.kts文件，无法自动升级版本" -ForegroundColor Yellow
    }
}

Write-Host "脚本执行完成！" -ForegroundColor Green