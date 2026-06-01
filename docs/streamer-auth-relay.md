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
  - `BRIDGE_PUBLISH_TOKEN`

When `SUPABASE_RELAY_FUNCTION_URL` is configured, the bridge posts:

```json
{
  "channelId": "xerioc2",
  "gameState": {}
}
```

The Edge Function validates `Authorization: Bearer <BRIDGE_PUBLISH_TOKEN>` and publishes to `game-state:{channelId}` using the service role key that stays inside Supabase.

This already removes the service role key from the streamer PC, but it is still a shared token model.

## Phase 2: Twitch Login

The bridge should open a browser-based Twitch OAuth login flow:

1. Streamer clicks `Login with Twitch` in the bridge.
2. Twitch redirects to a hosted callback endpoint.
3. The callback verifies the Twitch OAuth response and identifies the broadcaster channel.
4. The relay service issues a limited bridge publish token scoped to that Twitch channel.
5. The bridge stores only:
   - channel ID/name
   - scoped bridge token
   - relay function URL
6. The Twitch Extension subscribes to `game-state:{channelId}`.

For review and first public use, prefer issuing tokens from a hosted auth service or Supabase Edge Function backed by a table such as:

```text
streamer_relays
- twitch_user_id
- twitch_login
- channel_id
- bridge_token_hash
- created_at
- revoked_at
```

Do not store raw bridge tokens in the database. Store a hash and compare hashes in the Edge Function.

## Deployment Notes

Deploy the Edge Function:

```powershell
supabase functions deploy publish-game-state
```

Set secrets:

```powershell
supabase secrets set SUPABASE_URL=https://your-project.supabase.co
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=your_service_role_key
supabase secrets set BRIDGE_PUBLISH_TOKEN=your_long_random_publish_token
```

Bridge `.env.local` for Phase 1:

```text
SUPABASE_RELAY_FUNCTION_URL=https://your-project.supabase.co/functions/v1/publish-game-state
BRIDGE_PUBLISH_TOKEN=your_long_random_publish_token
SUPABASE_CHANNEL_ID=xerioc2
```

Do not set `SUPABASE_SERVICE_ROLE_KEY` in the bridge once the Edge Function path is active.
