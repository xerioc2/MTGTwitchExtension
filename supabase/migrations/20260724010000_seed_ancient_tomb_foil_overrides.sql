-- Foil-sibling catalog ids for Ancient Tomb, confirmed LIVE via a real MTGO game
-- (not inferred from id-math alone -- both catalog ids were observed directly in
-- Game Play Status Update log lines during an active match on 2026-07-22/23).
--
-- 143394 = foil sibling of nonfoil Ancient Tomb 143393 (Edge of Eternities: Stellar
--   Sights #46). Streamer's deck runs mostly foil copies of this print (observed 9
--   objects at 143394 vs 2 at 143393 in a single hand+battlefield snapshot).
-- 111831 = foil sibling of nonfoil Ancient Tomb 111830 (Tales of Middle-earth
--   Commander #357).

insert into mtgo_card_overrides (
  mtgo_catalog_id, name, type_line, mana_cost, oracle_text, image_url, token, source, notes
) values
  (
    143394,
    'Ancient Tomb',
    'Land',
    '',
    '{T}: Add {C}{C}. This land deals 2 damage to you.',
    'https://cards.scryfall.io/normal/front/2/c/2c8b3180-6e29-484a-95f1-3e75af2766d3.jpg?1783905851',
    false,
    'observed_log',
    'Foil catalog id sibling of nonfoil Ancient Tomb (mtgo_id 143393, Edge of Eternities: Stellar Sights #46). Confirmed live via Game Play Status Update log during an active match, not inferred.'
  ),
  (
    111831,
    'Ancient Tomb',
    'Land',
    '',
    '{T}: Add {C}{C}. This land deals 2 damage to you.',
    'https://cards.scryfall.io/normal/front/d/f/dfd176e9-55e7-454c-bd35-24a6e3fb0d81.jpg?1783915842',
    false,
    'observed_log',
    'Foil catalog id sibling of nonfoil Ancient Tomb (mtgo_id 111830, Tales of Middle-earth Commander #357). Confirmed live via Game Play Status Update log during an active match, not inferred.'
  )
on conflict (mtgo_catalog_id) do nothing;
