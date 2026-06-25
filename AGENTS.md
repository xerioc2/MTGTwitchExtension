# AGENTS.md — Standing context for coding agents

This file is the always-on orientation for any coding agent (Codex, etc.) working in this repo. It captures **what the project is**, **the direction we're heading**, and the **hard guardrails**. It deliberately does **not** lock specific tech choices — those are decided per task as we prompt them.

For the V2 plan in depth, read [docs/v2-vision-reconciliation.md](docs/v2-vision-reconciliation.md).

---

## What this is

An MTG Twitch extension that shows viewers the cards in play and lets them hover a card to see it larger. Two parts:

- **`backend/`** — a Java/Spring "bridge" that runs on the **streamer's** machine. It parses MTGO logs into a `GameState` and publishes it over a local WebSocket and through a Supabase Edge Function relay to Twitch viewers. This is the V1, review-passed path.
- **`frontend/`** — a React/Vite Twitch extension (the viewer-facing overlay/panel/config). Viewers install nothing; they just load the extension in their browser and subscribe to the Supabase relay channel.

Twitch version **`0.0.2`** is the live, review-approved V1. A separate dev version (`0.0.3 V2 Screen Hover Test`) is used for V2 work. See [docs/v2-screen-hover-twitch-test.md](docs/v2-screen-hover-twitch-test.md).

## Where we're heading (V2)

V2 adds **hover-to-enlarge over the live video**: the viewer hovers a card *on the stream* and sees it bigger. That requires knowing **where each card is on screen**, which an LLM vision step produces (`detectionRegions`, normalized `0..1` bounding boxes already plumbed through `GameState`).

The single most important framing decision:

> **Vision-only is the default experience. The bridge is a progressive enhancement.**

Most streamers will not install an unsigned `.exe` from GitHub, so most channels will run **without** the bridge. The LLM-only path therefore has to stand on its own and is the path we design and tune for first. When a streamer *does* run the bridge, it acts as a ground-truth oracle that constrains and validates the LLM's reads for higher accuracy. Never treat the bridge as required.

## Hard guardrails (do not violate without explicit sign-off)

- **Do not touch Twitch version `0.0.2`** or anything that would alter the live V1 review build. The normal `frontend/dist` build stays review-safe.
- **Additive only on the wire.** `GameState` must stay backwards compatible — don't rename or remove V1 fields. `detectionRegions` defaults to `[]`; V1 clients ignore it.
- **V2 behavior is flag-gated.** Frontend hover hitboxes render only when `VITE_ENABLE_SCREEN_DETECTIONS=true`; backend detector behavior only under the `screen-detections.*` flags. With flags off/unset, behavior must be identical to V1.
- **No deploys or production changes from V2 work.** Do not deploy Supabase functions or backend changes, and **do not edit `.env.local` or any production env file.** V2 detector/relay paths are local/dev only and are not auth-hardened.
- **Don't break the bridge-optional contract.** The viewer must work with the bridge absent — render hover boxes off `detectionRegions` alone, even when every `GameState` zone array is empty.

## How we work

1. **We (human + Claude) decide intent** for a small, scoped slice.
2. **Claude writes a focused prompt** for that slice.
3. **Codex executes** the prompt.
4. **We review** the result against the stated intent, confirm alignment, then decide the next slice.

Start slow and incremental. Prefer one well-scoped change we can review over a large speculative one. When a task's intent isn't written down yet, ask rather than assume.
