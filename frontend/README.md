# Frontend

React + Vite frontend for the MTGO Twitch Extension panel.

## Included

- Twitch panel entry point at `twitch.html`
- Main extension panel with hand, battlefield, graveyard, and exile zones
- Local debug route at `/debug`
- WebSocket connection to the local bridge
- Scryfall card prefetch and hover popout
- Reconnect button that calls `POST /api/rescan-log`

## Development

```powershell
npm install
npm run dev
```

Local dev defaults to `ws://localhost:8080/ws/game-state` unless `VITE_BACKEND_WS_URL` is set.

## Build

```powershell
npm run build
```

The Twitch upload zip should contain the contents of `dist/`, not the `dist` folder itself. The current upload artifact is:

```text
magiccontent-0.0.2.zip
```

`twitch.html` must be at the root of the zip.
