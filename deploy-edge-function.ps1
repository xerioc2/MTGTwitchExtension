param(
    [string]$FunctionName = "publish-game-state"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

Write-Host "Deploying Supabase Edge Function '$FunctionName'..."
supabase functions deploy $FunctionName

Write-Host ""
Write-Host "Deployment command finished."
Write-Host ""
Write-Host "Set these Supabase function secrets before live validation:"
Write-Host ""
Write-Host "  supabase secrets set SUPABASE_URL=https://lgzmxstmqzwjbmurstye.supabase.co"
Write-Host "  supabase secrets set SUPABASE_SERVICE_ROLE_KEY=<your service role key>"
Write-Host "  supabase secrets set BRIDGE_PUBLISH_TOKEN=<your long random bridge publish token>"
Write-Host ""
Write-Host "Do not commit or paste real secret values into this script."
