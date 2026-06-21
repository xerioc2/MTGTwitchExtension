# V2 Screen Hover Twitch Test Setup

This is a side-by-side local test setup for V2 screen-hover regions. Do not modify or replace Twitch version `0.0.2`, which is the current review-ready V1 path.

Use a separate Twitch developer version for V2 testing, for example:

`0.0.3 V2 Screen Hover Test`

Do not submit this V2 setup for Twitch review yet.

## Safety Rules

- Keep the existing bridge installation, launcher, tray behavior, shortcuts, and startup setup unchanged.
- Keep the normal frontend build as the V1/review-safe build.
- Do not edit `.env.local` or production environment files.
- Do not deploy Supabase functions or backend changes from this setup.
- Do not touch Twitch version `0.0.2` in review.

## Compatibility

The backend `GameState` payload remains backwards compatible:

- Existing V1 fields are not renamed or removed.
- `detectionRegions` is additive and defaults to `[]`.
- V1 frontend builds ignore `detectionRegions`.
- Existing WebSocket and Supabase game-state paths are unchanged.
- V2 hover hitboxes render only when `VITE_ENABLE_SCREEN_DETECTIONS=true`.

## V2 Backend Local Test Flags

Use these only for local V2 testing:

```powershell
$env:ENABLE_SCREEN_DETECTIONS="true"
$env:ENABLE_SCREEN_DETECTOR="true"
$env:SCREEN_DETECTOR_MODE="manual" # or "mock"
$env:SCREEN_DETECTOR_MIN_INTERVAL="PT1S"
$env:SUPABASE_CHANNEL_ID="xerioc2"
```

Or run the additive dev helper:

```powershell
.\scripts\run-v2-screen-hover-dev.ps1 -Mode manual -ChannelId xerioc2
```

This starts the backend from source with local environment variables only. It does not replace the installed bridge.

### Relay Safety

If `BRIDGE_PUBLISH_TOKEN` and `SUPABASE_RELAY_FUNCTION_URL` are set, V2 mock/manual detections can relay to live Twitch viewers. The V2 dev runner clears these variables by default for local-only testing.

Only remove that safety behavior if intentionally testing live relay.

## V2 Frontend Test Build

The normal build remains:

```powershell
cd frontend
npm run build
```

The V2 test build is separate:

```powershell
cd frontend
npm run build:v2-screen-hover
```

This enables `VITE_ENABLE_SCREEN_DETECTIONS=true` for that command and outputs to:

`frontend/dist-v2-screen-hover`

The normal `frontend/dist` review build is not used for this V2 test bundle.

## V2 Twitch Upload Package

Create the V2-only Twitch upload zip with:

```powershell
cd frontend
npm run package:v2-screen-hover
```

This command runs the V2 build with `VITE_ENABLE_SCREEN_DETECTIONS=true`, verifies the built entry files, rejects env/secret-looking files, and writes:

`frontend/twitch-packages/magiccontent-v2-screen-hover-0.0.3.zip`

The zip contains the built extension files at the root of the archive. It does not nest them under `dist-v2-screen-hover/`.

Do not upload this zip to Twitch version `0.0.2`. Create or clone a separate Twitch developer version for V2 testing, such as `0.0.3`, and upload the V2 package there only.

Do not submit the V2 test version for Twitch review yet. The backend V2 detector mode is local/dev only.

## Trigger Manual Detector

With the backend running in V2 test mode:

```http
POST /api/detection-regions/detector/run?channelId=xerioc2
```

The detector does not inspect pixels. It only maps known `GameState` cards to mock/manual normalized regions for local hover testing.

## Verify V1 Remains Unchanged

1. Leave all V2 flags unset or false.
2. Run the normal frontend build:

   ```powershell
   cd frontend
   npm run build
   ```

3. Confirm the extension still connects through the existing game-state WebSocket/Supabase path.
4. Confirm no detection endpoint or detector behavior is active unless `ENABLE_SCREEN_DETECTIONS=true`.

## Rollback

Disable the V2 flags:

```powershell
$env:ENABLE_SCREEN_DETECTIONS="false"
$env:ENABLE_SCREEN_DETECTOR="false"
$env:SCREEN_DETECTOR_MODE="none"
$env:VITE_ENABLE_SCREEN_DETECTIONS="false"
```

Then use the normal V1 build and current bridge setup.

For Twitch-side rollback, switch back to the normal V1 frontend package or keep using the existing `0.0.2` review version. Leave V2 flags disabled.

## Known Limitations

- No OBS integration yet.
- No OpenCV, OCR, screenshot capture, or template matching yet.
- No calibration UI yet.
- Detection write/run endpoints are local/dev only and are not auth-hardened.
- Do not submit V2 for Twitch review yet.
