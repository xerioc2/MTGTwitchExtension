# MTGO Twitch Extension

A Twitch Extension that shows viewers what cards are in each zone 
of your Magic: The Gathering Online game in real time — hand, 
battlefield, graveyard, and exile — with Scryfall card images 
and oracle text on hover.

## How It Works

1. A local bridge app watches your MTGO log file
2. Game state (zones, card movements, deck list) is parsed in real time
3. State is broadcast via WebSocket to the Twitch Extension panel
4. Viewers see a live decklist organized by zone with card hover previews

## Features

- Auto-discovers your MTGO log file (no manual path configuration needed)
- Real-time zone tracking — hand, battlefield, graveyard, exile
- Scryfall card image and oracle text on hover
- Reconnect button handles MTGO client updates mid-stream
- Raw debug view at `/debug` for local development

## For Streamers

- `backend/` — Java 17 + Spring Boot 3 backend. File watching, 
  WebSocket broadcasting, MTGO log parsing, Scryfall API integration.
- `frontend/` — React + Vite Twitch Extension panel.
- `docs/` — Design notes and project documentation.
- `ops/` — Local development environment examples.

## Development Setup

### Backend
```bash
cd backend
mvn spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

### Environment Variables
Copy `frontend/.env.example` to `frontend/.env.local` and fill in 
your values.

## Roadmap

- [ ] Windows .exe launcher for streamers (no Java required)
- [ ] System tray support
- [ ] GitHub Releases distribution
- [ ] Multi-streamer cloud relay
