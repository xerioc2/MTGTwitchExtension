# magiccontent — MTGO Twitch Overlay

magiccontent is a Twitch video overlay for Magic: The Gathering Online. It watches the streamer's MTGO game log through a small Windows bridge app, sends the current game state to the hosted Twitch Extension, and lets viewers see cards grouped by zone with hover previews for card image, type line, mana cost, and oracle text.

## For Streamers

### Requirements

- Windows PC
- Magic: The Gathering Online installed
- A Twitch account

### Setup

1. Install the bridge
   Download the latest `MTGO-Twitch-Bridge-x.x.x-portable.zip` from the
   [Releases page](https://github.com/xerioc2/MTGTwitchExtension/releases),
   unzip it anywhere, and run `MTGO Twitch Bridge.exe` inside the folder.
   (Windows SmartScreen may warn on first run: click "More info", then "Run anyway".)

2. Login with Twitch
   Click `Login with Twitch` in the bridge window and complete the Twitch login in your browser.

3. Activate the extension
   Go to your Twitch Creator Dashboard, find `magiccontent` under Extensions, and add it as a Video Overlay.

### Using It

- Open MTGO before starting the bridge.
- The bridge runs in the system tray while you stream.
- If the overlay stops updating, click `Refresh Log` in the bridge window.
- To switch Twitch accounts, use `Log out` from the system tray.

## For Viewers

The overlay shows the streamer's visible MTGO zones: hand, battlefield, graveyard, and exile. Hover over a card row to open a preview with the card image and rules text. The overlay is designed to sit on the side of the video and only expand when viewers interact with it.

## For Developers

### Architecture

The production path is:

```text
MTGO log -> Spring Boot bridge -> Supabase Edge Function -> Supabase Realtime -> Twitch Hosted Extension
```

The Spring Boot bridge discovers and watches MTGO's `mtgo.log`, parses game-state updates, resolves card data through Scryfall, and publishes state through a Supabase relay. The Twitch Extension is hosted by Twitch and subscribes to Supabase Realtime. A local WebSocket path remains available for development and debugging.

### Local Development

Backend:

```powershell
cd backend
mvn spring-boot:run
```

Optional backend configuration:

```powershell
$env:MTGO_LOG_PATH="C:\path\to\mtgo.log"
$env:MTGO_DEBUG_RAW_LOG="true"
```

Frontend:

```powershell
cd frontend
npm install
npm run dev
```

Build Twitch assets:

```powershell
cd frontend
npm run build
```

The local debug page is available at:

```text
http://localhost:5173/debug
```

The main overlay entry point is:

```text
overlay.html
```

### Supabase Edge Functions

The relay functions live in `supabase/functions/`:

- `publish-game-state` validates bridge publish tokens, persists the latest state, and broadcasts only changed state.
- `issue-bridge-token` verifies Twitch login and issues a per-streamer bridge token.

Deploy functions:

```powershell
supabase functions deploy publish-game-state
supabase functions deploy issue-bridge-token
```

Set required Supabase secrets:

```powershell
supabase secrets set SUPABASE_URL=<your-supabase-url>
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=<your-service-role-key>
supabase secrets set TWITCH_CLIENT_ID=<your-twitch-client-id>
```

During a frontend rollout, set `PUBLISH_LEGACY_LOGIN_TOPIC=true` only while the
previous login-topic frontend is still active. Remove it after the numeric
Twitch-channel frontend is released.

Apply database migrations from `supabase/migrations/` before validating the relay flow.

### Building the Installer

Build the desktop bridge jar:

```powershell
cd backend
mvn -Pdesktop package -DskipTests
```

Build a portable Windows app folder:

```powershell
cd backend
.\package-windows.ps1 -Type app-image -Version 0.0.2
```

Build a Windows installer `.exe`:

```powershell
cd backend
.\package-windows.ps1 -Type exe -Version 0.0.2
```

The `.exe` build requires WiX Toolset 3.x so `candle.exe` and `light.exe` are available on `PATH`.

WiX installer tooling:

```text
https://github.com/wixtoolset/wix3/releases
```

### Project Structure

- `backend/` - Spring Boot bridge, MTGO log discovery/watcher, parser, REST API, WebSocket fallback, Scryfall resolution, Swing launcher, and Windows packaging script.
- `frontend/` - React + Vite Twitch Extension overlay and local debug page.
- `supabase/` - Edge Functions, Supabase config, and database migrations for the hosted relay.
- `docs/` - Architecture notes and project documentation.
- `ops/` - Local development environment examples.

### API Reference

- `GET /api/status` - returns backend status information, including the active port.
- `POST /api/rescan-log` - re-runs MTGO log discovery and restarts the watcher.
- `GET /api/cards/{catalogId}` - resolves card data through Scryfall.
- `ws://localhost:{port}/ws/game-state` - local development WebSocket fallback for current game state.
