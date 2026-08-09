# V2 Vision + Reconciliation — Design Plan

The plan for V2 "hover-to-enlarge over the live video." This is the map; individual tasks are scoped and prompted one at a time (see [AGENTS.md](../AGENTS.md) for the workflow). Concrete tech choices marked **[OPEN]** are decided per task, not pre-committed here.

---

## Goal & framing

Let a Twitch viewer hover a card *on the streamed video* and see it larger. That needs **card locations on screen** (`detectionRegions`), which an LLM vision step produces from captured frames.

**Vision-only is the default; the bridge is progressive enhancement.** Most streamers won't install an unsigned `.exe`, so most channels run without the bridge — the LLM-only path is the hero we design and tune for first. The bridge, when present, makes a good experience great by acting as a ground-truth oracle. Nothing on the viewer side may require the bridge.

## Current state (the skeleton already built)

- **Transport works end to end.** A detector produces `DetectionRegion`s → `DetectionRegionService` normalizes/clamps/TTLs them → `GameStateService` stores + broadcasts → WebSocket **and** Supabase relay carry the full `GameState` (regions included) to viewers → `useScreenDetections` renders hover hitboxes → `CardPreview` shows the card.
- **`DetectionRegion`** carries: `id`, `channelId`, `cardId`, `catalogId`, `cardName`, `zone`, `imageUrl`, `confidence`, `bbox` (normalized `0..1`), `source`, `frameWidth/Height`, `observedAt`, `expiresAt`. Expiry is pruned on both ends.
- **The detector is a clean seam.** `ScreenCardDetector.detect(gameState, ctx)` is the swap point. Current impls (`ManualLayout`, `Mock`) only echo bridge cards into hardcoded boxes — there is **no real capture or vision yet**.
- **Backwards-compat is real.** `detectionRegions` defaults `[]`; V1 ignores it; everything is flag-gated.
- **The bridge tracks only the local player.** `GameStateService` filters status cards to `owner == localPlayerId`, so today the entire opponent side is unknown to the bridge.

What's missing is everything that turns frames into real boxes: capture, the vision call, identity reconciliation, and the bridge-optional + viewer-UX pieces below.

## Capture & inference architecture

The heavy compute (inference) must **not** run on the streamer's PC, and the streamer must not have to install or configure anything for the default path. **Decision: the bridge-less location producer is a hosted service that ingests the channel's public Twitch HLS stream** — the same video the viewer watches — runs vision on a frame every ~10–15s, and publishes regions to the relay channel.

```text
Hosted ingest + vision service   [OPEN: host + vision provider]
  • pulls the channel's public Twitch HLS  (the same frames the viewer sees)
  • 1 downsized frame every ~10–15s → vision model → [{ name, bbox }]
  • publishes DetectionRegions to the relay channel
        │
        ├─ bridge present →  service uses the bridge's frame-time card list to
        │                    constrain + reconcile identity (see Pillar 2)
        └─ no bridge       →  identity from vision + Scryfall name lookup only
```

The viewer subscribes to **one** channel and renders whatever regions arrive — it never knows or cares how they were produced.

**Why HLS ingest** (vs broadcaster-side screen capture): reading the same delayed stream the viewer watches makes both spatial and temporal alignment automatic (Pillar 1), and requires **zero streamer setup** — which is the entire point of the vision-only default. The accepted tradeoff is server-side ingest + compute + a little added latency on top of the broadcast delay.

**[OPEN] decisions, settled per task:** vision provider (behind a provider-agnostic `frame → [{name, bbox}]` interface so we can A/B); the ingest/service host; exact request/response shapes; capture cadence and frame downscale size; how the service learns which channels are active. A sibling local project (`Website-main`) has a working real-time scanner — `getDisplayMedia` capture + an OpenAI-compatible vision call with a strict JSON schema + Scryfall name-collection validation — that returns **names only, no boxes**. We reuse its vision-call + Scryfall-validation shape and extend the schema to `{ name, bbox }`; its browser capture front-end is replaced by HLS frame extraction.

---

## Pillar 1 — Coordinate-space contract (LOCKED)

**Invariant (the viewer depends on this; it never changes):** `bbox` is normalized `[0,1]` relative to the **Twitch broadcast video frame as the viewer sees it** — top-left origin, x→right, y→down, `(x,y)` = box top-left corner, `(w,h)` = box size, 16:9 canvas. The viewer renders `bbox × overlay size` and nothing more. All coordinate mapping is the producer's responsibility; the viewer stays dumb and stable.

**How the producer satisfies it:** the producer reads the **same public Twitch HLS the viewer watches**, so the frame it analyzes *is* the viewer's frame. Alignment is then automatic on both axes:

- **Spatial:** producer frame == viewer frame → boxes are already in broadcast space. No raw-screen→composite transform, no calibration UI.
- **Temporal:** producer and viewer share the same delayed time base. (A producer that captured the broadcaster's *live* local screen would be ahead of the viewer by the full broadcast delay **and** would need a raw→composite calibration — which is exactly why that's not the default path.)

**Locked assumptions:**

- 16:9 broadcast canvas. The Twitch extension video-overlay is anchored to the video frame, so `bbox` is relative to the 16:9 video content, not any letterbox padding.
- The viewer pads each hitbox by a small tolerance (a few %) to absorb vision imprecision.
- `frameWidth`/`frameHeight` on a region are **informational/debug only** — never used by the viewer for scaling; may be used to detect an aspect mismatch and warn.
- Regions self-heal via the existing TTL: a scene switch or layout change simply expires the stale boxes.

**Known residual (acceptable for v1):** the bridge's *identity* truth is live, but the HLS frame is delayed — so reconciling bridge cards against an HLS frame needs the bridge's state **as of the frame's broadcast time**, not its live state (see Pillar 2). For slow-changing MTGO board state this is usually fine; the timestamped-history lookup is the precise fix.

## Pillar 2 — Constrain / reconcile / confidence (bridge as oracle)

Only possible where the bridge has ground truth (today: the local player's zones). Two uses, plus a calibration trick:

1. **Constrain (before the call).** Pass the known card set per zone into the prompt: *"Your hand is exactly [A, B, C, D] — locate each."* Turns open-vocabulary identification into closed-set matching — the biggest accuracy win.
2. **Reconcile (after the call).** Diff LLM output against ground truth: name matches a bridge card → high confidence, attach real `catalogId`, `source = BRIDGE_VALIDATED`; name not in the set → hallucination, drop or snap to nearest; bridge card the LLM missed → leave unplaced rather than guess.
3. **Confidence transfer.** Reconciling gradeable zones (your hand) is a live accuracy probe: if the LLM nailed the zones you *can* grade this frame, trust its reads of the opponent's revealed cards you *can't*; if it botched yours, downweight/suppress opponent boxes for that frame.

Maps onto existing fields: bridge cards → `catalogId` (resolved on frontend); LLM-only cards → `name` → Scryfall name lookup → lower `confidence`, `source = LLM`. **Never invent identities for face-down/hidden cards** — keep the "don't guess hidden zones" instruction.

**Frame-time alignment.** Because the HLS frame is delayed relative to the bridge's live state, reconcile against the bridge's card list **as of the frame's broadcast timestamp**, not the live list. The bridge keeps a short timestamped history for this; for slow MTGO state, the live list is often close enough as a first cut.

## Pillar 3 — Bridge-optional degradation

Reframed as **progressive enhancement toward** the bridge, not degradation from it.

- **Decouple `GameState` from `detectionRegions`.** Bridge-less mode has no `GameState` (no zones, no `gameId`); the viewer must render hover boxes off `detectionRegions` alone even when zone arrays are empty.
- **Same channel, swappable producer.** Bridge present → bridge fills the channel (with reconcile). Bridge absent → serverless vision fn publishes regions to the channel directly.
- **"Local bridge not connected" warning is streamer-facing.** Shown where the local backend is reachable (the streamer's config/overlay, which already tracks `connectionState`); on no connection, show the banner and run vision-only rather than erroring. An optional subtle viewer-side "vision-only / approximate" badge is secondary.
- All bridge-less regions are `source = LLM`, lower confidence, name-resolved; no reconcile step.

## Pillar 4 — Viewer pin-panel UX

Per-viewer client preference (localStorage), independent of bridge/streamer settings. From live demo feedback:

- **Pin toggle.** When on, the floating `CardPreview` becomes a **docked panel on the right edge** that does not auto-hide. Hovering any card/region updates its contents; it stays put so the viewer can read without holding the cursor.
- **Click-to-lock.** Hover fills the panel; a click freezes it on that card so it doesn't change as the cursor moves.
- Reuses the existing `CardPreview` + `handleDetectionRegionMouseEnter` / `handleCardMouseEnter`; the toggle only changes *where* it renders and *when* it clears.

---

## Bridge adoption & trust (why vision-only must stand alone)

The bridge is an `.exe` on the streamer's machine; many won't trust installing it. That's *why* vision-only is the default. To raise bridge adoption for the streamers who would consider it (not today's work, but the strategy):

- **Code-sign the `.exe` (Authenticode).** Biggest lever — an unsigned exe triggers SmartScreen's "unknown publisher" scare that kills most installs. (~$100–200/yr cert, or Azure Trusted Signing.)
- **Trusted distribution** — `winget` and/or published checksums + a real project domain, not only raw GitHub releases.
- **Transparency** — it's open-source and local-only; a plain-language "what it reads / what leaves your machine" note (see [privacy-and-tos.md](privacy-and-tos.md)) linked from the install prompt.
- **Minimal footprint** — no admin install, no autostart unless opted in.

## Out of scope / future notes

- **Extend the bridge to opponent public zones.** The log event already contains opponent cards (filtered out by `owner == localPlayerId`); their *public* zones (battlefield/GY/exile) are game-visible and likely carry `catalogId`s. Removing the filter for public zones could shrink the LLM-only surface to just genuinely-hidden info — **parked as a future enhancement**, pending confirmation of what the MTGO log actually exposes for opponent cards.
- An experimental bridge-local OBS/screenshot pipeline now implements OpenCV rectangles, known-card template matching, optional OCR, confidence reconciliation, and normalized calibration behind explicit opt-in detector settings. It remains a secondary testing path because local frames run ahead of Twitch viewer video; see [local-screen-detector.md](local-screen-detector.md).
