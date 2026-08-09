# Backend

Spring Boot backend and Windows desktop bridge for the MTGO Twitch Extension.

## Included

- Spring MVC for REST endpoints
- Spring WebSocket for real-time state delivery
- MTGO log file watcher that tails newly appended lines from `MTGO_LOG_PATH`
- MTGO log parser for hand, battlefield, graveyard, and exile zone events
- 2-second polling fallback for MTGO log writes that do not trigger filesystem watch events
- Twitch deck log capture for raw MTGO catalog IDs in the current game state
- Twitch game status update parsing for player life totals and catalog-id zone snapshots
- Scryfall card resolution by MTGO catalog ID with multiverse fallback
- Game state WebSocket endpoint at `/ws/game-state`
- Swing desktop launcher at `com.mtgtwitch.extension.desktop.MtgoBridgeLauncher`
- Disabled-by-default local OBS/screenshot detector documented in [`../docs/local-screen-detector.md`](../docs/local-screen-detector.md)
- Actuator health/info endpoints

The parser prefers the structured `Twitch Info|Game Play Status Update` payload when MTGO emits it,
then falls back to the initial heuristic implementation for common MTGO-style zone log lines.

## Configuration

- `MTGO_LOG_PATH` - optional full path to the MTGO `mtgo.log` file. When omitted, the backend scans `%LOCALAPPDATA%\Apps\2.0` up to depth 4 and uses the newest `Logs\mtgo.log`.
- `MTGO_DEBUG_RAW_LOG=true` or `mtgo.debug.raw-log=true` - logs every newly detected raw log line before parsing.

## Endpoints

- `GET /api/status` - returns the active backend port, for example `{ "port": 8080 }`.
- `POST /api/rescan-log` - re-runs log discovery, restarts the file watcher, and returns the resolved log path.
- `GET /api/cards/{catalogId}` - resolves card image/name/type/mana/oracle text through Scryfall.

## Desktop Packaging

Build the portable app-image:

```powershell
.\package-windows.ps1 -Type app-image -Version 0.0.2
```

The app-image is written to `dist/windows-package/MTGO Twitch Bridge/`.

Build an installer after installing WiX 3.x:

```powershell
.\package-windows.ps1 -Type exe -Version 0.0.2
```

WiX: https://github.com/wixtoolset/wix3/releases

The launcher scans ports `8080` through `8090`, starts Spring Boot on the first available port, and displays the active WebSocket URL in the status window.
