# Backend

Spring Boot scaffold for the MTGO Twitch Extension backend.

## Included

- Spring MVC for REST endpoints
- Spring WebSocket for real-time state delivery
- MTGO log file watcher that tails newly appended lines from `MTGO_LOG_PATH`
- MTGO log parser for hand, battlefield, graveyard, and exile zone events
- Game state WebSocket endpoint at `/ws/game-state`
- Actuator health/info endpoints

The parser is an initial heuristic implementation for common MTGO-style zone log lines.

## Configuration

- `MTGO_LOG_PATH` - optional full path to the MTGO `mtgo.log` file. When omitted, the backend scans `%LOCALAPPDATA%\Apps\2.0` up to depth 4 and uses the newest `Logs\mtgo.log`.
- `MTGO_DEBUG_RAW_LOG=true` or `mtgo.debug.raw-log=true` - logs every newly detected raw log line before parsing.

## Endpoints

- `POST /api/rescan-log` - re-runs log discovery, restarts the file watcher, and returns the resolved log path.
