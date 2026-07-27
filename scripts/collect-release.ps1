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
$obsSourceDir = Join-Path $repoRoot "obs"

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

# Bridge: package the freshest app-image build (backend\dist\windows-package) as a
# versioned portable zip. The app-image's cfg carries the authoritative version.
$appImage = Join-Path $repoRoot "backend\dist\windows-package\MTGO Twitch Bridge"
if (Test-Path $appImage) {
    if (Test-Path $obsSourceDir) {
        $obsBundleDir = Join-Path $appImage "obs"
        if (Test-Path $obsBundleDir) {
            Remove-Item -LiteralPath $obsBundleDir -Recurse -Force
        }
        New-Item -ItemType Directory -Force -Path $obsBundleDir | Out-Null
        Copy-Item -Path (Join-Path $obsSourceDir "*") -Destination $obsBundleDir -Recurse -Force
        Write-Host "Included OBS auto-launch files in bridge app-image."
    }

    $cfg = Get-Content (Join-Path $appImage "app\MTGO Twitch Bridge.cfg") -ErrorAction SilentlyContinue
    $bridgeVersion = ($cfg | Select-String "app-version=(.+)$").Matches.Groups[1].Value
    if (-not $bridgeVersion) { $bridgeVersion = "unknown" }
    $bridgeZip = Join-Path $OutputDir ("MTGO-Twitch-Bridge-" + $bridgeVersion + "-portable.zip")
    Push-Location (Split-Path $appImage)
    & "$env:WINDIR\System32\tar.exe" -a -cf $bridgeZip "MTGO Twitch Bridge"
    Pop-Location
    Write-Host ("Packaged bridge portable zip (v" + $bridgeVersion + ").")
} else {
    Write-Warning "No bridge app-image found. Build one with backend\package-windows.ps1 -Type app-image"
}

# Signed installer exes (when we have them) also ride along if present in backend dist.
Get-ChildItem -Path (Join-Path $repoRoot "backend\dist\windows-package") -Filter "*.exe" -ErrorAction SilentlyContinue |
    Where-Object { $_.Length -gt 50MB } |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $OutputDir $_.Name) -Force; Write-Host ("Copied installer: " + $_.Name) }

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
4. Optional: check "Start automatically when Windows starts" so the bridge is always
   running in the background.
5. Start the bridge BEFORE joining your MTGO league/match (deck detection reads the
   log from startup), then play. The extension lights up within seconds.

OPTIONAL: HAVE OBS START THE BRIDGE FOR YOU
If you sometimes forget to start the bridge before going live, OBS can launch it
automatically the moment you hit Start Streaming or Start Recording -- no need to
remember it at all.

1. In the MTGO Twitch Bridge app, click "Set up OBS auto-launch".
   - This copies a small script to your OBS scripts folder
     (%APPDATA%\obs-studio\scripts) and opens a folder window showing it.
   - If OBS is not detected, open OBS at least once first, then try again.
2. In OBS: Tools -> Scripts -> click the "+" button (Add Scripts).
   - The dialog OBS opens by default is usually OBS's own bundled scripts folder,
     NOT the one we just copied into. If it doesn't take you there automatically,
     either paste this into the "File name" box and hit Open:
       %APPDATA%\obs-studio\scripts\mtgo-twitch-bridge-launcher.lua
     or drag the .lua file from the folder window the bridge opened directly into
     the Add Scripts dialog.
3. Select "mtgo-twitch-bridge-launcher.lua" in the Loaded Scripts list on the left.
   Check the "MTGO Twitch Bridge.exe" field on the right:
   - If it's already filled in, you're done -- click Close.
   - If it's blank, click Browse and manually select your
     "MTGO Twitch Bridge.exe" (wherever you installed/unzipped it), then click
     Close.
4. That's it. From now on, starting a stream or recording in OBS will launch the
   bridge automatically if it isn't already running. If it's already running, OBS
   won't pop up any "already running" dialog -- it just quietly does nothing.

To verify it's working: fully close the bridge, then click Start Recording in OBS
(safer than Start Streaming for a test run) and watch for the bridge window to
appear within a couple seconds. If nothing happens, open OBS's Tools -> Scripts ->
Script Log to check for errors.
"@ | Set-Content -Path (Join-Path $OutputDir "README.txt") -Encoding utf8

Write-Host ""
Write-Host "Release folder ready: $OutputDir"
