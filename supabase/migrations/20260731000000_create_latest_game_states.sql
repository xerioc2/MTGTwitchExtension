create table if not exists latest_game_states (
  channel_id text primary key,
  game_state jsonb not null,
  content_hash text not null,
  published_hash text,
  updated_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  constraint latest_game_states_channel_id_check check (channel_id ~ '^[0-9]{1,32}$')
);

alter table latest_game_states enable row level security;

grant select on latest_game_states to anon, authenticated;

create policy "Public game states are readable"
  on latest_game_states
  for select
  to anon, authenticated
  using (true);

create or replace function upsert_latest_game_state(
  p_channel_id text,
  p_game_state jsonb,
  p_content_hash text
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  existing_hash text;
  existing_published_hash text;
begin
  select content_hash, published_hash
    into existing_hash, existing_published_hash
    from latest_game_states
   where channel_id = p_channel_id
   for update;

  if not found then
    insert into latest_game_states (channel_id, game_state, content_hash)
    values (p_channel_id, p_game_state, p_content_hash);
    return true;
  end if;

  if existing_hash = p_content_hash then
    update latest_game_states
       set last_seen_at = now()
     where channel_id = p_channel_id
       and last_seen_at < now() - interval '30 seconds';
    return existing_published_hash is distinct from p_content_hash;
  end if;

  update latest_game_states
     set game_state = p_game_state,
         content_hash = p_content_hash,
         updated_at = now(),
         last_seen_at = now()
   where channel_id = p_channel_id;
  return true;
end;
$$;

create or replace function mark_latest_game_state_published(
  p_channel_id text,
  p_content_hash text
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  update latest_game_states
     set published_hash = p_content_hash
   where channel_id = p_channel_id
     and content_hash = p_content_hash;
  return found;
end;
$$;

revoke all on function upsert_latest_game_state(text, jsonb, text) from public;
grant execute on function upsert_latest_game_state(text, jsonb, text) to service_role;
revoke all on function mark_latest_game_state_published(text, text) from public;
grant execute on function mark_latest_game_state_published(text, text) to service_role;
