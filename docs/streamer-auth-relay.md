# Streamer Auth Relay Plan

The current Hosted Test flow proves the Twitch overlay can subscribe to Supabase Realtime. For public streamer use, the local bridge should not contain a Supabase service role key. The relay should look like this:

```text
MTGO Bridge -> Supabase Edge Function -> Supabase Realtime Broadcast -> Twitch Extension
```

## Phase 1: Limited Publish Token

Implemented scaffold:

- Supabase Edge Function: `supabase/functions/publish-game-state`
- Bridge config:
  - `SUPABASE_RELAY_FUNCTION_URL`
  - `BRIDGE_PUBLISH_TOKEN`
  - `SUPABASE_CHANNEL_ID`
- Edge Function secrets:
  - `SUPABASE_URL`
  - `SUPABASE_SERVICE_ROLE_KEY`

When `SUPABASE_RELAY_FUNCTION_URL` is configured, the bridge posts:

```json
{
  "channelId": "xerioc2",
  "gameState": {}
}
```

The Edge Function validates `Authorization: Bearer <bridge token>` by hashing it and looking up an active row in `streamer_relays`. It atomically stores the latest state in `latest_game_states` and publishes changed content to `game-state:{twitch_user_id}` using the service role key that stays inside Supabase. Timestamp-only duplicates refresh liveness at most once every 30 seconds and do not broadcast.

This removes the service role key from the streamer PC and avoids trusting channel IDs sent by the bridge.

## Phase 2: Twitch Login Token Issuer

Implemented scaffold:

- Supabase Edge Function: `supabase/functions/issue-bridge-token`
- Supabase migration: `supabase/migrations/*_create_streamer_relays.sql`
- Database table: `streamer_relays`
- Edge Function secrets:
  - `SUPABASE_URL`
  - `SUPABASE_SERVICE_ROLE_KEY`
  - `TWITCH_CLIENT_ID`

The token issuer accepts:

```http
POST /functions/v1/issue-bridge-token
Authorization: Bearer <twitch_access_token>
```

It verifies the Twitch access token with `GET https://api.twitch.tv/helix/users`, generates a random bridge token, stores only the SHA-256 hash in `streamer_relays`, and returns the raw token once:

```json
{
  "channelId": "xerioc2",
  "bridgeToken": "raw-token-returned-once",
  "relayFunctionUrl": "https://your-project.supabase.co/functions/v1/publish-game-state"
}
```

The raw bridge token is never stored in Supabase. `publish-game-state` validates the SHA-256 token hash against `streamer_relays` and uses the stored numeric `twitch_user_id` as the authoritative broadcast channel.

The bridge should open a browser-based Twitch OAuth login flow:

1. Streamer clicks `Login with Twitch` in the bridge.
2. Twitch redirects to a hosted callback endpoint.
3. The callback hands the Twitch access token to `issue-bridge-token`.
4. The relay service verifies Twitch identity and issues a limited bridge publish token scoped to that Twitch channel.
5. The bridge stores only:
   - channel ID/name
   - scoped bridge token
   - relay function URL
6. The Twitch Extension subscribes to `game-state:{channelId}`.

On viewer load, the elected frontend relay owner also reads the fresh row from
`latest_game_states`. This gives new viewers an immediate state without using
repeated broadcasts as replay. Same-origin panel/overlay instances share that
state through `BroadcastChannel`; only one owns the Supabase socket.

## Safe Numeric-Channel Rollout

1. Apply the `latest_game_states` migration.
2. Set `PUBLISH_LEGACY_LOGIN_TOPIC=true` and deploy `publish-game-state` while the old frontend is active.
3. Release and verify the numeric-channel frontend.
4. Remove `PUBLISH_LEGACY_LOGIN_TOPIC` (or set it to `false`) and redeploy the function.

The default is `false`. The login topic exists only as a temporary rollback aid;
leaving it enabled permanently doubles each changed-state broadcast.

The token issuer stores token metadata in:

```text
streamer_relays
- twitch_user_id
- twitch_login
- bridge_token_hash
- created_at
- revoked_at
```

Do not store raw bridge tokens in the database. Store a hash and compare hashes in the Edge Function.

## Deployment Notes

Deploy the Edge Function:

```powershell
supabase functions deploy publish-game-state
supabase functions deploy issue-bridge-token
```

Set secrets:

```powershell
supabase secrets set SUPABASE_URL=https://your-project.supabase.co
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=your_service_role_key
supabase secrets set TWITCH_CLIENT_ID=your_twitch_client_id
```

Bridge `.env.local` for Phase 1:

```text
SUPABASE_RELAY_FUNCTION_URL=https://your-project.supabase.co/functions/v1/publish-game-state
BRIDGE_PUBLISH_TOKEN=token_returned_by_issue_bridge_token
SUPABASE_CHANNEL_ID=xerioc2
```

Do not set `SUPABASE_SERVICE_ROLE_KEY` in the bridge once the Edge Function path is active.
