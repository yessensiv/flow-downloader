$ErrorActionPreference = 'Stop'
$toolsRoot = Join-Path $PSScriptRoot '../.tools/android'
New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
function Fetch-Zip($Url, $Name, $Checksum, $Destination) {
    $archive = Join-Path $toolsRoot $Name
    if (!(Test-Path $archive)) { Invoke-WebRequest $Url -OutFile $archive }
    if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $Checksum) { throw "Checksum mismatch: $Name" }
    if (!(Test-Path $Destination)) { Expand-Archive -LiteralPath $archive -DestinationPath $Destination }
}
if (!(Test-Path (Join-Path $toolsRoot 'jdk'))) {
    $release = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows'
    Fetch-Zip $release[0].binary.package.link 'jdk.zip' $release[0].binary.package.checksum (Join-Path $toolsRoot 'jdk')
}
Fetch-Zip 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' 'gradle.zip' 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6' (Join-Path $toolsRoot 'gradle')
Fetch-Zip 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' 'cmdline.zip' '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a' (Join-Path $toolsRoot 'sdk/cmdline-tools/latest')
Write-Output 'Downloaded and verified Java, Gradle and Android command-line tools in .tools/android.'
