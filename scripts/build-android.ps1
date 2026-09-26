param([string[]]$Tasks = @('assembleDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolsRoot = Join-Path $projectRoot '.tools/android'
$jdk = Get-ChildItem (Join-Path $toolsRoot 'jdk') -Directory | Select-Object -First 1
if (!$jdk) { throw 'Run scripts/setup-android.ps1 first.' }
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = Join-Path $toolsRoot 'sdk'
$env:GRADLE_USER_HOME = Join-Path $toolsRoot 'gradle-cache'
$env:PATH = "$($jdk.FullName)/bin;$env:PATH"
& (Join-Path $toolsRoot 'gradle/gradle-8.11.1/bin/gradle.bat') -p (Join-Path $projectRoot 'android') @Tasks
if ($LASTEXITCODE -ne 0) { throw "Android build failed: $LASTEXITCODE" }
