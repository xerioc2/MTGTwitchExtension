param(
    [string]$Version = "0.0.2",
    [ValidateSet("exe", "app-image")]
    [string]$Type = "exe"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage was not found on PATH. Install a JDK that includes jpackage, then rerun this script."
}

if ($Type -eq "exe" -and (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -or -not (Get-Command light.exe -ErrorAction SilentlyContinue))) {
    Write-Host "WiX Toolset is required for jpackage --type exe."
    Write-Host "Install WiX 3.x from: https://github.com/wixtoolset/wix3/releases"
    Write-Host "Make sure candle.exe and light.exe are on PATH, then rerun this script."
    exit 0
}

mvn -Pdesktop package
if ($LASTEXITCODE -ne 0) {
    throw "Maven desktop package failed with exit code $LASTEXITCODE."
}

$packageDir = Join-Path $projectRoot "dist\windows-package"
$inputDir = Join-Path $projectRoot "target\jpackage-input"
$resourceDir = Join-Path $projectRoot "src\main\jpackage"
$repoRoot = Split-Path -Parent $projectRoot
$obsSourceDir = Join-Path $repoRoot "obs"
if (Test-Path $packageDir) {
    Remove-Item -LiteralPath $packageDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $packageDir | Out-Null
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item -Path (Join-Path $projectRoot "target\mtgo-twitch-bridge.jar") -Destination $inputDir -Force
$obsInputDir = Join-Path $inputDir "obs"
if (Test-Path $obsInputDir) {
    Remove-Item -LiteralPath $obsInputDir -Recurse -Force
}
if (Test-Path $obsSourceDir) {
    New-Item -ItemType Directory -Force -Path $obsInputDir | Out-Null
    Copy-Item -Path (Join-Path $obsSourceDir "*") -Destination $obsInputDir -Recurse -Force
}
$iconPath = Join-Path $projectRoot "src\main\resources\icons\bridge-icon.ico"

$jpackageArgs = @(
    "--type", $Type,
    "--name", "MTGO Twitch Bridge",
    "--app-version", $Version,
    "--vendor", "magiccontent",
    "--description", "Local MTGO log bridge for the magiccontent Twitch Extension",
    "--input", $inputDir,
    "--main-jar", "mtgo-twitch-bridge.jar",
    "--dest", $packageDir
)

if (Test-Path $resourceDir) {
    $jpackageArgs += @("--resource-dir", $resourceDir)
}

if (Test-Path $iconPath) {
    $jpackageArgs += @("--icon", $iconPath)
}

if ($Type -eq "exe") {
    $jpackageArgs += @("--win-menu", "--win-shortcut")
}

jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE."
}

if ($Type -eq "app-image" -and (Test-Path $obsSourceDir)) {
    $appImageDir = Join-Path $packageDir "MTGO Twitch Bridge"
    if (Test-Path $appImageDir) {
        $obsBundleDir = Join-Path $appImageDir "obs"
        if (Test-Path $obsBundleDir) {
            Remove-Item -LiteralPath $obsBundleDir -Recurse -Force
        }
        New-Item -ItemType Directory -Force -Path $obsBundleDir | Out-Null
        Copy-Item -Path (Join-Path $obsSourceDir "*") -Destination $obsBundleDir -Recurse -Force
        Write-Host "Bundled OBS launcher at: $obsBundleDir"
    }
}

if ($Type -eq "exe") {
    Get-ChildItem -Path $packageDir -Filter "*.exe" | Select-Object FullName,Length,LastWriteTime
} else {
    Get-ChildItem -Path $packageDir -Directory | Select-Object FullName,LastWriteTime
}
