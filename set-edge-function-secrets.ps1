param(
    [string]$EnvFile = ".env.local"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

function Read-EnvFile($Path) {
    $values = @{}

    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()

        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -le 0) {
            return
        }

        $name = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1).Trim().Trim('"').Trim("'")
        $values[$name] = $value
    }

    return $values
}

$rootEnv = Read-EnvFile $EnvFile

$twitchClientId = $rootEnv["TWITCH_CLIENT_ID"]
$twitchClientSecret = $rootEnv["TWITCH_CLIENT_SECRET"]

if (-not $env:SUPABASE_ACCESS_TOKEN) {
    throw "SUPABASE_ACCESS_TOKEN is not set. Create a Supabase access token, set it in this PowerShell session, then rerun this script."
}

if (-not $twitchClientId) {
    throw "TWITCH_CLIENT_ID was not found in $EnvFile."
}

if (-not $twitchClientSecret) {
    throw "TWITCH_CLIENT_SECRET was not found in $EnvFile."
}

$supabase = Join-Path $env:USERPROFILE "scoop/shims/supabase.exe"
if (-not (Test-Path -LiteralPath $supabase)) {
    $supabase = "supabase"
}

& $supabase secrets set --project-ref lgzmxstmqzwjbmurstye `
    "TWITCH_CLIENT_ID=$twitchClientId" `
    "TWITCH_CLIENT_SECRET=$twitchClientSecret"

if ($LASTEXITCODE -ne 0) {
    throw "supabase secrets set failed with exit code $LASTEXITCODE"
}

Write-Host "Edge Function secrets were set for project lgzmxstmqzwjbmurstye."
