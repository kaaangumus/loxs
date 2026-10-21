$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

function Write-Step([string]$Message) {
    Write-Host "[*] $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "[+] $Message" -ForegroundColor Green
}

Write-Step "Checking Java and Gradle..."
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java not found. Install JDK 17+."
}
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage not found. Install JDK 17+."
}
$Gradle = Join-Path $Root "gradlew.bat"
if (-not (Test-Path $Gradle)) {
    if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
        throw "Gradle not found."
    }
    $Gradle = "gradle"
}

$WixDir = Join-Path $Root ".tools\wix"
$Candle = Join-Path $WixDir "candle.exe"
$Light = Join-Path $WixDir "light.exe"
if (-not (Test-Path $Candle) -or -not (Test-Path $Light)) {
    Write-Step "Downloading WiX Toolset 3.14..."
    $ToolsDir = Join-Path $Root ".tools"
    New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
    $Zip = Join-Path $ToolsDir "wix314-binaries.zip"
    if (-not (Test-Path $Zip)) {
        Invoke-WebRequest -Uri "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip" -OutFile $Zip
    }
    New-Item -ItemType Directory -Force -Path $WixDir | Out-Null
    Expand-Archive -Path $Zip -DestinationPath $WixDir -Force
}
$env:PATH = "$WixDir;$env:PATH"

Write-Step "Building and testing..."
& $Gradle :clean :test :fatJar --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed."
}

$Jar = Join-Path $Root "build\libs\loxs-java-2.1.0-all.jar"
if (-not (Test-Path $Jar)) {
    throw "Fat jar not found: $Jar"
}

$InputDir = Join-Path $Root "build\jpackage-input"
$DistDir = Join-Path $Root "dist"
Remove-Item $InputDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $InputDir | Out-Null
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
Copy-Item $Jar (Join-Path $InputDir "loxs-java-2.1.0-all.jar")

Get-ChildItem $DistDir -Filter "*.msi" -ErrorAction SilentlyContinue | Remove-Item -Force

Write-Step "Creating MSI..."
& jpackage `
    --type msi `
    --name "LOXS" `
    --app-version "2.1.0" `
    --vendor "Kaan Gumus" `
    --description "LOXS authorized web security scanner" `
    --copyright "Copyright (c) 2026" `
    --input $InputDir `
    --main-jar "loxs-java-2.1.0-all.jar" `
    --main-class "com.loxs.LoxsFxLauncher" `
    --dest $DistDir `
    --icon (Join-Path $Root "src\main\resources\loxs.ico") `
    --java-options "-Dfile.encoding=UTF-8" `
    --java-options "-Dsun.jnu.encoding=UTF-8" `
    --java-options "-Dstdout.encoding=UTF-8" `
    --java-options "-Dstderr.encoding=UTF-8" `
    --win-shortcut `
    --win-menu `
    --win-menu-group "LOXS" `
    --win-dir-chooser `
    --win-per-user-install `
    --win-upgrade-uuid "7a1b2c3d-4e5f-4a6b-8c9d-0e1f2a3b4c5d"

if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed. Check WiX Toolset."
}

$Msi = Get-ChildItem $DistDir -Filter "*.msi" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $Msi) {
    throw "MSI was not created."
}
Write-Ok "MSI ready: $($Msi.FullName)"
Write-Host "  Size: $([math]::Round($Msi.Length / 1MB, 2)) MB" -ForegroundColor Gray





