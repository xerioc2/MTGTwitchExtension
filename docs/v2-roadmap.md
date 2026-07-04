# V2 Roadmap — Bridge & Website (next version)

Next-version features beyond the current `feature/v4-decklist-view` work. This is the **bridge + website** track; the vision-only detection producer is a separate track in [v2-vision-reconciliation.md](v2-vision-reconciliation.md). New work for these items lands on `feature/v5-next-version`, keeping `feature/v4-decklist-view` frozen for its own approval.

## Track A — Bridge / gameplay accuracy

### A1. Multiple concurrent games (e.g. `DB_xerioc` + `xerioc`)
- **What:** track more than one MTGO game/account at once — players routinely double-queue leagues.
- **Why it's the big rock:** `GameStateService` is currently a **singleton** (one `localPlayerName`, one set of zones, one `gameId`, resets on each new game). Concurrent games means re-keying all state **by `gameId` (and/or account)**: a map of `GameState`s, per-game relay publishing, and an extension-side game switcher.
- **Effort:** high (real backend refactor). Schedule deliberately; other items ride on top of it.

### A2. Refresh decklist mid-match (post-sideboard)
- **What:** between games in a match, players sideboard, so the revealed list changes. Re-read the deck on each new-game/match-transition.
- **Approach:** today the deck loads once (`DeckCatalogEvent` → `updateDeckCatalogIds`). Hook a deck refresh into the game/match transition the log already signals (near `GameStatusEvent` handling in `MtgoLogParserService` / `GameStateService`).
- **Effort:** medium. Pairs naturally with A1 (both hinge on game/match boundaries).

## Track B — Website as the data backbone (Website-main)

These three are really one initiative: make **MTGContent the extension's backend + discovery layer**. Both projects already share the same Supabase, so this is low-friction.

### B1. Cached card prices (daily fetch, not on-demand)
- **What:** stop fetching prices per-card at hover time; keep a **price table refreshed 1–2×/day**, and have the extension read from it.
- **Approach:** a **daily cron** (Website-main already has `app/api/cron`) writes prices into a Supabase table; the extension queries that. Faster, cheaper, fewer rate-limit issues.
- **Effort:** low–medium.

### B2. Tie the extension to the MTGContent website
- **What:** the website becomes the extension's backend (price cache, decklist storage) **and** discovery layer (it already has streamers/streams/creators infra — live-detection streamer pages are a natural fit).
- **Note:** more a **direction to commit to** than a single feature; B1 and B3 are its first concrete pieces.
- **Effort:** ongoing.

### B3. Export streamer decklists
- **What:** let viewers export/download the streamer's deck (the "get the streamer's deck" feature).
- **Approach:** the bridge already tracks the registered deck (`deckCatalogIds` on `GameState`), and the frontend already resolves catalog IDs to cards and has ManaPool links. Add an **export** that groups the deck by quantity and emits shareable formats (plain text / Moxfield-style / ManaPool). Mostly frontend; the website can host a hosted export later (B2).
- **Effort:** low–medium. High viewer visibility.

## Recommended sequence

1. **B3 (decklist export)** — fastest visible value, self-contained, uses data already in the payload. Good first slice on `feature/v5-next-version`.
2. **B1 (price cache)** — perf win; first concrete piece of the website-backbone (B2).
3. **A2 (sideboard refresh)** — contained gameplay-accuracy fix.
4. **A1 (multiple games)** — the deliberate refactor; schedule when the above are landed.
5. **B2 (website integration)** — accretes across B1/B3 and beyond.

None of these conflict with the vision-producer track; they're complementary.
