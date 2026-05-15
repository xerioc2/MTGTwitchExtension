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
