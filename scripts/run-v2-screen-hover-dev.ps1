param(
    [ValidateSet("manual", "mock")]
    [string] $Mode = "manual",
    [string] $ChannelId = "xerioc2",
    [string] $MinInterval = "PT1S"
)

$ErrorActionPreference = "Stop"

$env:ENABLE_SCREEN_DETECTIONS = "true"
$env:ENABLE_SCREEN_DETECTOR = "true"
$env:SCREEN_DETECTOR_MODE = $Mode
$env:SCREEN_DETECTOR_MIN_INTERVAL = $MinInterval
$env:SUPABASE_CHANNEL_ID = $ChannelId

# Safety: clear relay credentials so mock/manual V2 detections do not reach live viewers.
# Remove these lines only if you intentionally want V2 detections to relay live.
$env:BRIDGE_PUBLISH_TOKEN = ""
$env:SUPABASE_RELAY_FUNCTION_URL = ""

Push-Location "$PSScriptRoot\..\backend"
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
