# csBaby - Auto Build and Upload to shz.al
param(
    [string]$Password = 'Abc@0987',
    [string]$ExpireDays = '7d'
)
$ErrorActionPreference = 'Stop'
$ApiBaseUrl = 'https://shz.al'
$VersionFileName = 'csBabyLog'
$ApkFileName = 'csBabyApk'
$ApkPath = 'app\build\outputs\apk\debug\app-debug.apk'
$VersionFile = 'gradle.properties'

function Write-Log {
    param([string]$Message, [string]$Level = 'INFO')
    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    $color = switch ($Level) {
        'ERROR' { 'Red' }
        'WARN' { 'Yellow' }
        'SUCCESS' { 'Green' }
        default { 'White' }
    }
    Write-Host "[$timestamp] $Message" -ForegroundColor $color
}

function Get-VersionInfo {
    $lines = [System.IO.File]::ReadAllLines($VersionFile)
    $versionCode = $null
    $versionName = $null
    foreach ($line in $lines) {
        if ($line -match 'APP_VERSION_CODE=(\d+)') {
            $versionCode = [int]$matches[1]
        }
        if ($line -match 'APP_VERSION_NAME=(.+)') {
            $versionName = $matches[1].Trim()
        }
    }
    if ($null -eq $versionCode -or $null -eq $versionName) {
        throw '无法读取版本信息'
    }
    return @{ VersionCode = $versionCode; VersionName = $versionName }
}

function Save-VersionInfo {
    param($VersionCode, $VersionName)
    $lines = [System.IO.File]::ReadAllLines($VersionFile)
    $newLines = @()
    foreach ($line in $lines) {
        if ($line -match 'APP_VERSION_CODE=') {
            $newLines += "APP_VERSION_CODE=$VersionCode"
        } elseif ($line -match 'APP_VERSION_NAME=') {
            $newLines += "APP_VERSION_NAME=$VersionName"
        } else {
            $newLines += $line
        }
    }
    [System.IO.File]::WriteAllLines($VersionFile, $newLines)
    Write-Log '版本号已更新到 gradle.properties' -Level 'SUCCESS'
}

function Increment-Version {
    param($CurrentVersionCode, $CurrentVersionName)
    $newVersionCode = $CurrentVersionCode + 1
    # 正确解析和递增版本号
    $parts = $CurrentVersionName -split '\.'
    if ($parts.Length -ge 3) {
        $major = [int]$parts[0]
        $minor = [int]$parts[1]
        $patch = [int]$parts[2]
        $patch++
        $newVersionName = "$major.$minor.$patch"
    } else {
        # 如果版本格式不正确，使用默认值
        $newVersionName = "1.1.62"
    }
    Write-Log "版本递增: v$CurrentVersionName ($CurrentVersionCode) -> v$newVersionName ($newVersionCode)" -Level 'SUCCESS'
    return @{ VersionCode = $newVersionCode; VersionName = $newVersionName }
}

function Get-FileMD5 {
    param([string]$FilePath)
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $stream = [System.IO.File]::OpenRead($FilePath)
    $hash = $md5.ComputeHash($stream)
    $stream.Close()
    return [System.BitConverter]::ToString($hash).Replace('-', '').ToLower()
}

function Curl-Delete {
    param([string]$Name)
    $url = if ($Password -ne '') { "$ApiBaseUrl/~$Name`:$Password" } else { "$ApiBaseUrl/~$Name" }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'curl.exe'
    $psi.Arguments = "-s -X DELETE $url"
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $proc = [System.Diagnostics.Process]::Start($psi)
    $stdout = $proc.StandardOutput.ReadToEnd()
    $proc.WaitForExit()
    $short = $stdout.Substring(0, [Math]::Min(80, $stdout.Length))
    Write-Log "删除响应: $short"
}

function Curl-Upload {
    param([string]$Name, [string]$Content, [byte[]]$BinaryData, [string]$Filename, [string]$ExpireTime)
    Curl-Delete -Name $Name
    $tempFile = [System.IO.Path]::GetTempFileName()
    $curlArgs = @('-s', '-X', 'POST', '-F', "n=$Name", '-F', "e=$ExpireTime")
    if ($BinaryData) {
        [System.IO.File]::WriteAllBytes($tempFile, $BinaryData)
        $curlArgs += '-F', "filename=$Filename"
        $curlArgs += '-F', "c=@$tempFile"
    } else {
        [System.IO.File]::WriteAllText($tempFile, $Content, [System.Text.Encoding]::UTF8)
        $curlArgs += '-F', "c=@$tempFile"
    }
    if ($Password -ne '') { $curlArgs += '-F', "s=$Password" }
    $curlArgs += $ApiBaseUrl
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'curl.exe'
    $psi.Arguments = $curlArgs -join ' '
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $proc = [System.Diagnostics.Process]::Start($psi)
    $stdout = $proc.StandardOutput.ReadToEnd()
    $proc.WaitForExit()
    $short = $stdout.Substring(0, [Math]::Min(100, $stdout.Length))
    Write-Log "API响应: $short"
    $result = $stdout | ConvertFrom-Json
    if ($result.url) {
        Write-Log "上传成功: $($result.url)" -Level 'SUCCESS'
    } else {
        throw "上传失败: $stdout"
    }
    Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
}

try {
    Write-Log '========== 开始构建和上传 ==========' -Level 'INFO'
    $currentVersion = Get-VersionInfo
    Write-Log "当前版本: v$($currentVersion.VersionName) ($($currentVersion.VersionCode))"
    $versionInfo = Increment-Version -CurrentVersionCode $currentVersion.VersionCode -CurrentVersionName $currentVersion.VersionName
    Save-VersionInfo -VersionCode $versionInfo.VersionCode -VersionName $versionInfo.VersionName
    Write-Log "新版本: v$($versionInfo.VersionName) ($($versionInfo.VersionCode))" -Level 'SUCCESS'
    Write-Log '跳过编译步骤，使用现有的APK文件...' -Level 'INFO'
    Write-Log 'APK准备完成' -Level 'SUCCESS'
    if (-not (Test-Path $ApkPath)) { throw 'APK不存在' }
    $apkSize = (Get-Item $ApkPath).Length
    $apkMd5 = Get-FileMD5 $ApkPath
    Write-Log "APK: $apkSize bytes, MD5: $apkMd5"
    $dt = (Get-Date).ToString('yyyy-MM-dd')
    $versionJson = @{
        versionCode = $versionInfo.VersionCode
        versionName = $versionInfo.VersionName
        releaseDate = $dt
        fileSize = $apkSize
        md5 = $apkMd5
        downloadUrl = "$ApiBaseUrl/~$ApkFileName"
        releaseNotes = "v$($versionInfo.VersionName) 更新"
        forceUpdate = $false
    } | ConvertTo-Json -Depth 10
    Write-Log '[1/2] 上传版本信息...'
    Curl-Upload -Name $VersionFileName -Content $versionJson -ExpireTime $ExpireDays
    Write-Log '[2/2] 上传APK...'
    Curl-Upload -Name $ApkFileName -BinaryData ([System.IO.File]::ReadAllBytes($ApkPath)) -Filename 'app-debug.apk' -ExpireTime $ExpireDays
    Write-Log '========== 完成 ==========' -Level 'SUCCESS'
    Write-Log '版本信息: https://shz.al/~csBabyLog' -Level 'SUCCESS'
    Write-Log 'APK下载: https://shz.al/~csBabyApk' -Level 'SUCCESS'
} catch {
    Write-Log '错误: $_' -Level 'ERROR'
    exit 1
}
