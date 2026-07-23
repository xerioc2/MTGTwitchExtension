-- Foil-sibling catalog ids: MTGO assigns a separate MTGO catalog id to the foil
-- printing of a card (Scryfall's card.finishes includes both "nonfoil" and "foil",
-- and the foil id is observed to be nonfoil_id + 1 for these). Confirmed by direct
-- observation (streamer plays with foil display off, so these showed as unresolved
-- "Catalog ID N" even though the physical/digital object was the foil printing).
--
-- Not baked into the resolver as a general automatic rule -- each of these is an
-- exact, individually-confirmed override, same as any other manual entry. Card
-- data pulled directly from Scryfall's /cards/mtgo/{nonfoil_id} for each.

insert into mtgo_card_overrides (
  mtgo_catalog_id, name, type_line, mana_cost, oracle_text, image_url, token, source, notes
) values
  (
    126724,
    'Planar Nexus',
    'Land',
    '',
    E'This land is every nonbasic land type. (Nonbasic land types include Cave, Desert, Gate, Lair, Locus, Mine, Power-Plant, Sphere, Tower, and Urza''s.)\n{T}: Add {C}.\n{1}, {T}: Add one mana of any color.',
    'https://cards.scryfall.io/normal/front/2/8/28603c1c-f9b4-4001-bc56-d1453d5cacf5.jpg?1783911414',
    false,
    'manual',
    'Foil catalog id sibling of nonfoil Planar Nexus (mtgo_id 126723, Modern Horizons 3 Commander). Confirmed by streamer direct observation (foil display off).'
  ),
  (
    126628,
    'Eldrazi Confluence',
    'Instant',
    '{2}{C}{C}',
    E'Choose three. You may choose the same mode more than once.\n• Target creature gets +3/-3 until end of turn.\n• Exile target nonland permanent, then return it to the battlefield tapped under its owner''s control.\n• Create a 1/1 colorless Eldrazi Scion creature token with "Sacrifice this token: Add {C}."',
    'https://cards.scryfall.io/normal/front/7/8/78ee2013-29dc-4879-9d59-1b492996d297.jpg?1783911429',
    false,
    'manual',
    'Foil catalog id sibling of nonfoil Eldrazi Confluence (mtgo_id 126627, Modern Horizons 3 Commander). Confirmed by streamer direct observation (foil display off).'
  ),
  (
    138946,
    'Ugin, Eye of the Storms',
    'Legendary Planeswalker — Ugin',
    '{7}',
    E'When you cast this spell, exile up to one target permanent that''s one or more colors.\nWhenever you cast a colorless spell, exile up to one target permanent that''s one or more colors.\n+2: You gain 3 life and draw a card.\n0: Add {C}{C}{C}.\n−11: Search your library for any number of colorless nonland cards, exile them, then shuffle. Until end of turn, you may cast those cards without paying their mana costs.',
    'https://cards.scryfall.io/normal/front/6/4/64a5d494-efa1-446b-bebe-2ad36e154376.jpg?1783907421',
    false,
    'manual',
    'Foil catalog id sibling of nonfoil Ugin, Eye of the Storms (mtgo_id 138945, Kaldheim). Confirmed by streamer direct observation (foil display off).'
  )
on conflict (mtgo_catalog_id) do nothing;
