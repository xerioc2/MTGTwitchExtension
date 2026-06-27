# MTG Twitch Vision Module

Standalone TypeScript module for detecting readable Magic: The Gathering cards in one image frame.

This package is intentionally isolated from the frontend, backend, relay, HLS ingest, and Scryfall. It exposes a provider-agnostic API so future providers can implement the same `VisionProvider` interface.

## Setup

```powershell
cd services/vision
npm install
```

For real Gemini scans, create a local env file or set process env vars:

```powershell
$env:GEMINI_API_KEY="your_key"
$env:GEMINI_MODEL="gemini-2.5-flash"
$env:GEMINI_MODELS="gemini-2.5-flash,gemini-2.5-flash-lite"
```

Do not commit API keys. `.env.example` documents expected variables only.

`GEMINI_MODELS` is a comma-separated fallback list. By default the provider tries `gemini-2.5-flash` first and falls back to `gemini-2.5-flash-lite` if Gemini returns transient throttling or overload responses like 429 or 503. `GEMINI_MODEL` remains supported as a legacy single preferred model and is prepended to the list.

For the relay wire CLI, set these in `services/vision/.env` or as process env vars:

```powershell
$env:SUPABASE_URL="https://your-dev-project.supabase.co"
$env:SUPABASE_SERVICE_ROLE_KEY="your_dev_service_role_key"
$env:RELAY_CHANNEL_ID="your-dev-channel"
```

`RELAY_CHANNEL_ID` must be a dedicated development channel. The wire CLI refuses to run if it is empty or `xerioc2`.

## API

```ts
import { GeminiVisionProvider } from './dist/index.js';

const provider = new GeminiVisionProvider({
  apiKey: process.env.GEMINI_API_KEY ?? ''
});

const result = await provider.detect(
  { dataBase64: jpegBase64, mimeType: 'image/jpeg' },
  { knownCards: ['Island', 'Lightning Bolt'] }
);
```

Returned boxes use the locked viewer coordinate contract:

- normalized `[0,1]`
- top-left origin
- `x` and `y` are the top-left corner
- `w` and `h` are the box size
- relative to the Twitch broadcast video frame as the viewer sees it

## Scripts

```powershell
npm run build
npm test
npm run lint
npm run scan -- C:\path\to\frame.jpg
npm run wire -- C:\path\to\frame.jpg
npm run wire -- C:\path\to\frame.jpg --loop 10
npm run wire -- C:\path\to\frame.jpg --debug
npm run wire -- C:\path\to\frame.jpg --debug C:\path\to\wire-debug.html
npm run wire -- --channel beefygg --debug
npm run wire -- --url https://twitch.tv/beefygg --debug
```

`npm run scan` runs the real Gemini provider only when `GEMINI_API_KEY` is set. Without a key it prints `skipped: no GEMINI_API_KEY`.

`npm run wire` runs:

```text
image file -> GeminiVisionProvider -> Scryfall image lookup -> DetectionRegions -> Supabase realtime broadcast
```

It publishes one broadcast payload to:

```text
topic: game-state:${RELAY_CHANNEL_ID}
event: game-state
payload: { detectionRegions }
```

The `--loop <seconds>` option republishes periodically so regions stay live past the default 30 second TTL.

The `--debug [path]` option writes a standalone HTML file that displays the analyzed frame with the raw vision `bbox` rectangles outlined and labeled. If no path is provided, it writes `./wire-debug.html`. Open it in a browser to verify box alignment offline, with no relay or Twitch frontend required.

The wire CLI accepts exactly one frame source:

- a static local image path
- `--channel <name>` for a public Twitch channel handle
- `--url <twitchUrl>` for a public Twitch channel URL

Twitch frame extraction reads only the public live stream. It requires external binaries on `PATH`:

```powershell
scoop install yt-dlp ffmpeg
```

`yt-dlp` resolves the live HLS manifest, and `ffmpeg` extracts one downscaled JPEG frame from that manifest without temp files.

## Local V2 Viewer Test

1. Pick a development relay channel, not `xerioc2`.
2. Configure the V2 frontend to subscribe to the same dev channel:

   ```powershell
   cd ..\..\frontend
   $env:VITE_SUPABASE_URL="https://your-dev-project.supabase.co"
   $env:VITE_SUPABASE_ANON_KEY="your_dev_anon_key"
   $env:VITE_SUPABASE_CHANNEL_ID="your-dev-channel"
   npm run build:v2-screen-hover
   ```

3. Serve/open the V2 overlay that subscribes to that dev channel.
4. In `services/vision`, run:

   ```powershell
   npm run wire -- C:\path\to\screenshot.jpg --loop 10
   ```

This can use a static image or a public live Twitch channel as the frame source. Server hosting, auth hardening, and automatic relay orchestration are later slices.

## Current Provider

`GeminiVisionProvider` asks Gemini for readable MTG card names and native-style object boxes:

```json
{ "label": "Lightning Bolt", "box_2d": [ymin, xmin, ymax, xmax] }
```

Gemini boxes are normalized to `0..1000` and converted to viewer boxes with:

```ts
x = xmin / 1000
y = ymin / 1000
w = (xmax - xmin) / 1000
h = (ymax - ymin) / 1000
```

Malformed model output is parsed defensively; total parse failure returns `{ cards: [] }`.

## Detection Region Mapping

The wire CLI maps vision cards to dev-only `DetectionRegion` objects:

- `cardId = null`
- `catalogId = null`
- `zone = "UNKNOWN"`
- `confidence = 0.5`
- `source = "LLM"`
- `observedAt = now`
- `expiresAt = now + 30000ms`

Scryfall is used only to resolve a canonical name and normal image URL for display. Failed Scryfall chunks are skipped without throwing.
