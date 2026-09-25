$ErrorActionPreference = 'Stop'
$toolsDir = Join-Path $PSScriptRoot '../.tools'
New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
$archive = Join-Path $toolsDir 'ffmpeg.zip'
$checksum = Join-Path $toolsDir 'ffmpeg.zip.sha256'
Invoke-WebRequest 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip' -OutFile $archive
Invoke-WebRequest 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.sha256' -OutFile $checksum
$expected = ((Get-Content $checksum -Raw).Trim() -split '\s+')[0]
if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $expected) { throw 'FFmpeg checksum mismatch' }
Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $toolsDir 'ffmpeg-package') -Force
Get-ChildItem (Join-Path $toolsDir 'ffmpeg-package') -Recurse -File | Where-Object { $_.Name -in @('ffmpeg.exe', 'ffprobe.exe') } | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $toolsDir -Force }
Write-Output 'FFmpeg and ffprobe installed locally; SHA256 verified.'
