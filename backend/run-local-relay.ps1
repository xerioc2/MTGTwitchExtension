param(
    [string]$EnvFile = ".env.local"
)

$ErrorActionPreference = "Stop"

$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$envPath = Join-Path $backendRoot $EnvFile

if (-not (Test-Path -LiteralPath $envPath)) {
    throw "Relay env file not found: $envPath"
}

Get-Content -LiteralPath $envPath | ForEach-Object {
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
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

if (-not $env:SUPABASE_RELAY_FUNCTION_URL) {
    Write-Warning "SUPABASE_RELAY_FUNCTION_URL is not set. Relay publishing will be disabled."
}

Write-Host "Loaded relay env from $envPath"
Write-Host "SUPABASE_RELAY_FUNCTION_URL present: $(if ($env:SUPABASE_RELAY_FUNCTION_URL) { 'yes' } else { 'no' })"
Write-Host "BRIDGE_PUBLISH_TOKEN present: $(if ($env:BRIDGE_PUBLISH_TOKEN) { 'yes' } else { 'no' })"
Write-Host "SUPABASE_CHANNEL_ID: $($env:SUPABASE_CHANNEL_ID)"

Set-Location $backendRoot
mvn spring-boot:run
