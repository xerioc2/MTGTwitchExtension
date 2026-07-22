create table if not exists mtgo_card_overrides (
  mtgo_catalog_id integer primary key check (mtgo_catalog_id > 0),
  name text not null,
  type_line text not null default '',
  mana_cost text not null default '',
  oracle_text text not null default '',
  image_url text,
  token boolean not null default true,
  enabled boolean not null default true,
  source text not null default 'manual',
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists mtgo_card_cache (
  mtgo_catalog_id integer primary key check (mtgo_catalog_id > 0),
  name text not null,
  type_line text not null default '',
  mana_cost text not null default '',
  oracle_text text not null default '',
  image_url text,
  inferred_back_face boolean not null default false,
  token boolean not null default false,
  source text not null default 'scryfall_exact',
  first_seen_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table mtgo_card_overrides enable row level security;
alter table mtgo_card_cache enable row level security;

insert into mtgo_card_overrides (
  mtgo_catalog_id,
  name,
  type_line,
  mana_cost,
  oracle_text,
  image_url,
  token,
  source,
  notes
) values (
  125873,
  'Cat Token',
  'Token Creature - Cat',
  '',
  '',
  'https://cards.scryfall.io/normal/front/7/d/7d400b41-813d-4a63-848f-5eb4db4bf3bb.jpg?1783903575',
  true,
  'seed',
  'Observed MTGO token catalog id. Exact override only; not a fallback namespace.'
) on conflict (mtgo_catalog_id) do nothing;
