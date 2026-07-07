param(
    [string]$OutputDir = (Join-Path $env:USERPROFILE "Desktop\MTGTwitch Release"),
    [switch]$SkipBuild
)

# Gathers every artifact needed for a release hand-off into one Desktop folder:
#   - the Twitch extension upload zip (rebuilt from the current branch unless -SkipBuild)
#   - the bridge installer exe (newest found)
#   - the portable bridge zip fallback (if present)
# Zip entries must use forward slashes (Twitch CDN rejects backslash paths),
# so packaging goes through tar.exe, never Compress-Archive.

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$frontendDir = Join-Path $repoRoot "frontend"
$zipPath = Join-Path $frontendDir "twitch-packages\magiccontent-upload.zip"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

if (-not $SkipBuild) {
    Write-Host "Building frontend..."
    Push-Location $frontendDir
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend build failed." }

    Write-Host "Packaging Twitch upload zip (forward-slash entries via tar)..."
    Push-Location (Join-Path $frontendDir "dist")
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    & "$env:WINDIR\System32\tar.exe" -a -cf $zipPath assets config.html index.html overlay.html twitch.html
    if ($LASTEXITCODE -ne 0) { throw "tar packaging failed." }
    Pop-Location
    Pop-Location
}

$branch = (git -C $repoRoot branch --show-current).Trim()
$commit = (git -C $repoRoot rev-parse --short HEAD).Trim()
$stamp = Get-Date -Format "yyyy-MM-dd HHmm"

if (Test-Path $zipPath) {
    Copy-Item $zipPath (Join-Path $OutputDir "magiccontent-upload.zip") -Force
    Write-Host "Copied Twitch upload zip."
}

# Newest bridge installer exe from known locations.
$exeCandidates = @(
    Get-ChildItem -Path (Join-Path $repoRoot "backend\dist\windows-package") -Filter "*.exe" -ErrorAction SilentlyContinue | Where-Object { $_.Length -gt 50MB }
    Get-ChildItem -Path (Join-Path $env:USERPROFILE "Desktop") -Filter "MTGO Twitch Bridge*.exe" -ErrorAction SilentlyContinue
) | Where-Object { $_ } | Sort-Object LastWriteTime -Descending

if ($exeCandidates.Count -gt 0) {
    Copy-Item $exeCandidates[0].FullName (Join-Path $OutputDir $exeCandidates[0].Name) -Force
    Write-Host ("Copied bridge installer: " + $exeCandidates[0].Name)
} else {
    Write-Warning "No bridge installer exe found. Build one with backend\package-windows.ps1 -Type exe"
}

$portableZip = Join-Path $env:USERPROFILE "Desktop\MTGO-Twitch-Bridge-portable.zip"
if (Test-Path $portableZip) {
    Copy-Item $portableZip (Join-Path $OutputDir "MTGO-Twitch-Bridge-portable.zip") -Force
    Write-Host "Copied portable bridge zip."
}

@"
MTGTwitch Release folder  (generated $stamp)
Source: branch $branch @ $commit
Regenerate anytime:  scripts\collect-release.ps1   (-SkipBuild to just re-copy)

WHAT'S WHAT
- magiccontent-upload.zip ............ upload this to the Twitch dev console
                                       (extension version assets)
- MTGO Twitch Bridge-*.exe ........... the bridge installer to send to streamers
                                       (SmartScreen: More info -> Run anyway, until signed)
- MTGO-Twitch-Bridge-portable.zip .... no-installer fallback: unzip, run the exe inside

STREAMER ONBOARDING (copy/paste)
1. Install the extension from the Twitch extension page and activate it on your channel.
2. Run the bridge installer (More info -> Run anyway on the SmartScreen warning).
3. Open the MTGO Twitch Bridge app -> Login with Twitch.
4. Start the bridge BEFORE joining your MTGO league/match (deck detection reads the
   log from startup), then play. The extension lights up within seconds.
"@ | Set-Content -Path (Join-Path $OutputDir "README.txt") -Encoding utf8

Write-Host ""
Write-Host "Release folder ready: $OutputDir"
