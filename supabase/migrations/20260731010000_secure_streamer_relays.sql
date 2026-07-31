alter table public.streamer_relays enable row level security;

revoke all privileges
on table public.streamer_relays
from anon, authenticated;

grant select, insert, update, delete
on table public.streamer_relays
to service_role;
