create table if not exists mtgo_card_override_art (
  id bigint generated always as identity primary key,
  mtgo_catalog_id integer not null references mtgo_card_overrides(mtgo_catalog_id) on delete cascade,
  image_url text not null,
  scryfall_id text,
  created_at timestamptz not null default now()
);

alter table mtgo_card_override_art enable row level security;

insert into mtgo_card_override_art (
  mtgo_catalog_id,
  image_url,
  scryfall_id
)
select
  seed.mtgo_catalog_id,
  seed.image_url,
  seed.scryfall_id
from (
  values
    (125873, 'https://cards.scryfall.io/normal/front/7/e/7ebd7482-0f67-4afa-ba82-bf12e7e07b30.jpg?1783906758', '7ebd7482-0f67-4afa-ba82-bf12e7e07b30'),
    (125873, 'https://cards.scryfall.io/normal/front/2/8/2885d54c-9fb2-4f01-8937-54f8ac1ce5bc.jpg?1783908593', '2885d54c-9fb2-4f01-8937-54f8ac1ce5bc'),
    (125873, 'https://cards.scryfall.io/normal/front/7/4/74bacab2-a4c6-4ba5-a208-6bd09ae4cf9f.jpg?1783911119', '74bacab2-a4c6-4ba5-a208-6bd09ae4cf9f'),
    (125873, 'https://cards.scryfall.io/normal/front/5/c/5c53bf06-f681-4d5c-b303-cc7c050f5b01.jpg?1783913143', '5c53bf06-f681-4d5c-b303-cc7c050f5b01'),
    (125873, 'https://cards.scryfall.io/normal/front/5/f/5f458f39-27b6-4121-bda9-1a0d1b42f5fb.jpg?1783929500', '5f458f39-27b6-4121-bda9-1a0d1b42f5fb'),
    (125873, 'https://cards.scryfall.io/normal/front/f/b/fbdf8dc1-1b10-4fce-97b9-1f5600500cc1.jpg?1783930586', 'fbdf8dc1-1b10-4fce-97b9-1f5600500cc1')
) as seed(mtgo_catalog_id, image_url, scryfall_id)
where not exists (
  select 1
  from mtgo_card_override_art existing
  where existing.mtgo_catalog_id = seed.mtgo_catalog_id
    and existing.image_url = seed.image_url
);

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
  139517,
  'Monk',
  'Token Creature — Monk',
  '',
  'Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)',
  'https://cards.scryfall.io/normal/front/e/4/e49c80ac-5c90-4fd2-ad40-cc77af160f64.jpg?1783904746',
  true,
  'manual',
  'Observed MTGO token catalog id, 1/1 Prowess variant. Multiple art printings in mtgo_card_override_art; do not merge with other Monk token identities (e.g. Djinn Monk 2/2 flying) which are mechanically different cards.'
) on conflict (mtgo_catalog_id) do nothing;

insert into mtgo_card_override_art (
  mtgo_catalog_id,
  image_url,
  scryfall_id
)
select
  seed.mtgo_catalog_id,
  seed.image_url,
  seed.scryfall_id
from (
  values
    (139517, 'https://cards.scryfall.io/normal/front/e/4/e49c80ac-5c90-4fd2-ad40-cc77af160f64.jpg?1783904746', 'e49c80ac-5c90-4fd2-ad40-cc77af160f64'),
    (139517, 'https://cards.scryfall.io/normal/front/6/3/633d2d10-def7-426f-8496-ed6b45684299.jpg?1783906788', '633d2d10-def7-426f-8496-ed6b45684299'),
    (139517, 'https://cards.scryfall.io/normal/front/1/5/15597c74-0d47-44e3-87bb-f9174ca265c2.jpg?1783916663', '15597c74-0d47-44e3-87bb-f9174ca265c2'),
    (139517, 'https://cards.scryfall.io/normal/front/d/2/d27b3b91-8bef-4d3c-84ef-5015ca9e472c.jpg?1783916674', 'd27b3b91-8bef-4d3c-84ef-5015ca9e472c'),
    (139517, 'https://cards.scryfall.io/normal/front/3/8/388180bd-cc96-4b18-9ddd-18a3f7e06282.jpg?1783921127', '388180bd-cc96-4b18-9ddd-18a3f7e06282'),
    (139517, 'https://cards.scryfall.io/normal/front/8/f/8f9d16af-2b50-426f-98d2-992d97a4a150.jpg?1783921680', '8f9d16af-2b50-426f-98d2-992d97a4a150'),
    (139517, 'https://cards.scryfall.io/normal/front/3/1/3142cb28-23cc-405f-9db5-7c4d168aab19.jpg?1783938663', '3142cb28-23cc-405f-9db5-7c4d168aab19'),
    (139517, 'https://cards.scryfall.io/normal/front/1/e/1e498e42-f55c-4afa-b2e2-02345f91cdb5.jpg?1783938722', '1e498e42-f55c-4afa-b2e2-02345f91cdb5')
) as seed(mtgo_catalog_id, image_url, scryfall_id)
where not exists (
  select 1
  from mtgo_card_override_art existing
  where existing.mtgo_catalog_id = seed.mtgo_catalog_id
    and existing.image_url = seed.image_url
);
