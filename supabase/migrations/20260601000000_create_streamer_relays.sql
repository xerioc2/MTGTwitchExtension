create table if not exists streamer_relays (
  id uuid primary key default gen_random_uuid(),
  twitch_user_id text not null,
  twitch_login text not null,
  bridge_token_hash text not null,
  created_at timestamptz not null default now(),
  revoked_at timestamptz
);

create unique index if not exists streamer_relays_active_twitch_user_id_idx
  on streamer_relays (twitch_user_id)
  where revoked_at is null;
