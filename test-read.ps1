$content = [System.IO.File]::ReadAllText('gradle.properties')
Write-Host "Length:" $content.Length
$lines = $content -split "`n"
Write-Host "Lines:" $lines.Count
foreach($l in $lines) { 
    if($l -match 'APP_VERSION') { 
        Write-Host "LINE:|$l|" 
        Write-Host "LINE CHARS:" ($l.ToCharArray() | ForEach-Object { [int]$_ }) -Separator ","
    } 
}
