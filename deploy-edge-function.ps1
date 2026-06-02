param(
    [string[]]$FunctionName = @("publish-game-state", "issue-bridge-token")
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot
$projectRef = "lgzmxstmqzwjbmurstye"
$supabaseUrl = "https://$projectRef.supabase.co"

foreach ($name in $FunctionName) {
    Write-Host "Deploying Supabase Edge Function '$name'..."
    supabase functions deploy $name
}

Write-Host ""
Write-Host "Deployment command finished."
Write-Host ""
Write-Host "Set these Supabase function secrets before live validation:"
Write-Host ""
Write-Host "  supabase secrets set SUPABASE_URL=$supabaseUrl"
Write-Host "  supabase secrets set SUPABASE_SERVICE_ROLE_KEY=<your service role key>"
Write-Host "  supabase secrets set TWITCH_CLIENT_ID=<your Twitch extension client id>"
Write-Host "  supabase secrets set TWITCH_CLIENT_SECRET=<your Twitch API client secret>"
Write-Host ""
Write-Host "Do not commit or paste real secret values into this script."
