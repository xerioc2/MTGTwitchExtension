# MTGO Twitch Extension

MTGO Twitch Extension shows Magic: The Gathering Online game zones in a Twitch panel. The local bridge watches MTGO's `mtgo.log`, parses game state, resolves card data through Scryfall, and serves the panel over WebSocket/REST.

## For Streamers

1. Install or unzip `MTGO Twitch Bridge`.
2. Open MTGO first.
3. Run `MTGO Twitch Bridge.exe`.
4. The bridge window should show:
   - Backend: `Running`
   - MTGO log: `Found`
   - WebSocket URL: `ws://localhost:8080/ws/game-state` or the next available port up to `8090`
5. If MTGO updates or the log is not found, click `Refresh Log`.
6. Use `Stop/Close` or the system tray `Exit` menu to shut the bridge down cleanly.

The bridge auto-discovers the newest `%LOCALAPPDATA%\Apps\2.0\...\Logs\mtgo.log`. No Java, Maven, or terminal is needed for the packaged app-image/exe build.

## Twitch Upload

The Twitch Extension asset zip is:

```text
frontend/magiccontent-0.0.2.zip
```

Upload the contents zip to Twitch Extension Asset Hosting. `twitch.html` is at the zip root.

## Local Development

Backend:

```powershell
cd backend
mvn spring-boot:run
```

Optional environment/config:

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

Build the portable Windows bridge app:

```powershell
cd backend
.\package-windows.ps1 -Type app-image -Version 0.0.2
```

Build a Windows installer exe after installing WiX 3.x:

```powershell
cd backend
.\package-windows.ps1 -Type exe -Version 0.0.2
```

WiX installer tooling: https://github.com/wixtoolset/wix3/releases

## Project Structure

- `backend/` - Spring Boot bridge, MTGO log watcher/parser, REST API, WebSocket server, Scryfall resolution, Swing launcher, packaging script.
- `frontend/` - React + Vite Twitch Extension panel and local debug page.
- `docs/` - Design notes and project documentation.
- `ops/` - Local development environment examples.

## Useful Endpoints

- `GET /api/status` - returns the active backend port.
- `POST /api/rescan-log` - re-runs MTGO log discovery and restarts the watcher.
- `GET /api/cards/{catalogId}` - resolves card data through Scryfall.
- `ws://localhost:{port}/ws/game-state` - broadcasts current game state.
