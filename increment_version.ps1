$file = 'D:\workspace\workbuddy\csBaby\gradle.properties'
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)

# 查找 APP_VERSION_CODE 行
$found = $false
$newLines = @()
foreach ($line in $content -split "`r`n") {
    if ($line -match '^APP_VERSION_CODE\s*=\s*(\d+)') {
        $old = [int]$Matches[1]
        $new = $old + 1
        $newLines += "APP_VERSION_CODE=$new"
        Write-Host "versionCode: $old -> $new"
        $found = $true
    } else {
        $newLines += $line
    }
}

if ($found) {
    [System.IO.File]::WriteAllText($file, ($newLines -join "`r`n"), [System.Text.Encoding]::UTF8)
    Write-Host 'gradle.properties updated.'
} else {
    Write-Host 'No APP_VERSION_CODE found, skipping.'
}
