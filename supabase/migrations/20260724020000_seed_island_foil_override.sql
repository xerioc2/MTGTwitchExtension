-- Foil-sibling catalog id for Island (Marvel's Spider-Man Secret Lair), confirmed
-- LIVE via a real MTGO game (Game Play Status Update log line during an active
-- solo/practice match on 2026-07-22/23). 153228 = foil sibling of nonfoil Island
-- 153227 (Marvel's Spider-Man). Same pattern as the earlier foil-sibling batch.

insert into mtgo_card_overrides (
  mtgo_catalog_id, name, type_line, mana_cost, oracle_text, image_url, token, source, notes
) values (
  153228,
  'Island',
  'Basic Land — Island',
  '',
  '({T}: Add {U}.)',
  'https://cards.scryfall.io/normal/front/1/4/14fcdc57-12e2-429b-8916-4df752e462d4.jpg?1783905292',
  false,
  'observed_log',
  'Foil catalog id sibling of nonfoil Island (mtgo_id 153227, Marvel''s Spider-Man). Confirmed live via Game Play Status Update log during an active match, not inferred.'
)
on conflict (mtgo_catalog_id) do nothing;
