param(
    [string]$EnvFile = ".env.local",
    [string]$BackendEnvFile = "backend/.env.local"
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
$backendEnv = Read-EnvFile $BackendEnvFile

$serviceRoleKey = $rootEnv["SUPABASE_SERVICE_ROLE_KEY"]
$bridgePublishToken = $backendEnv["BRIDGE_PUBLISH_TOKEN"]

if (-not $env:SUPABASE_ACCESS_TOKEN) {
    throw "SUPABASE_ACCESS_TOKEN is not set. Create a Supabase access token, set it in this PowerShell session, then rerun this script."
}

if (-not $serviceRoleKey) {
    throw "SUPABASE_SERVICE_ROLE_KEY was not found in $EnvFile."
}

if (-not $bridgePublishToken) {
    throw "BRIDGE_PUBLISH_TOKEN was not found in $BackendEnvFile."
}

$supabase = Join-Path $env:USERPROFILE "scoop/shims/supabase.exe"
if (-not (Test-Path -LiteralPath $supabase)) {
    $supabase = "supabase"
}

& $supabase secrets set --project-ref lgzmxstmqzwjbmurstye `
    "SUPABASE_URL=https://lgzmxstmqzwjbmurstye.supabase.co" `
    "SUPABASE_SERVICE_ROLE_KEY=$serviceRoleKey" `
    "BRIDGE_PUBLISH_TOKEN=$bridgePublishToken"

if ($LASTEXITCODE -ne 0) {
    throw "supabase secrets set failed with exit code $LASTEXITCODE"
}

Write-Host "Edge Function secrets were set for project lgzmxstmqzwjbmurstye."
