# Relay Scaling Plan

## Current V5 Path

The V5 relay keeps the existing bridge contract while reducing avoidable Supabase usage:

- One visible browser surface owns Realtime per viewer/channel using Web Locks.
- Other panel, overlay, and tab surfaces receive local `BroadcastChannel` updates.
- Hidden surfaces disconnect and do not reconnect until visible.
- `latest_game_states` provides immediate bootstrap and reconnect recovery.
- The Edge Function hashes state without top-level `updatedAt`, persists one row per numeric Twitch channel, and broadcasts only changed or not-yet-successfully-broadcast content.
- The legacy login topic is disabled after the numeric-channel frontend rollout.

This removes duplicate surfaces, timestamp-only fanout, and heartbeat broadcasts
from Supabase message accounting. It does not remove the Free plan's concurrent
Realtime connection ceiling for genuinely visible viewers.

## Twitch-Native Fanout Decision

Twitch Extension PubSub is the preferred next transport experiment because the
Extension Helper owns viewer delivery; viewers would no longer open Supabase
Realtime sockets. Twitch documents `window.Twitch.ext.listen("broadcast", ...)`
for extension frontends and `POST /helix/extensions/pubsub` for an Extension
Backend Service.

The migration cannot safely send today's wire payload without measurement:

- Extension PubSub messages are limited to 5 KB.
- Broadcast traffic is limited to roughly one message per second per channel
  (the Helix endpoint also documents 100 messages/minute per extension/channel).
- `GameState` can grow with deck cards and V2 detection regions.

The recommended implementation is hybrid:

1. Keep `latest_game_states` as the durable bootstrap and recovery snapshot.
2. Measure serialized production payload sizes by state shape.
3. Define a compact, backwards-compatible Twitch PubSub update below 5 KB.
4. Have the frontend listen through the Twitch Helper and stop opening Supabase Realtime.
5. Use a small invalidation message plus a latest-state fetch only for oversized snapshots.
6. Roll out behind a transport flag, compare delivery/error metrics, then retire Supabase Realtime fanout.

Official references:

- https://dev.twitch.tv/docs/extensions/reference/
- https://dev.twitch.tv/docs/api/reference/#send-extension-pubsub-message
- https://dev.twitch.tv/docs/extensions/building/#broadcasting-via-pubsub

Do not place the Twitch extension shared secret in the bridge or frontend. JWT
signing and Helix publishing belong in the hosted EBS/Edge Function.
