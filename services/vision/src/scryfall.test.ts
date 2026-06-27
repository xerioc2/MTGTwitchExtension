import assert from 'node:assert/strict';
import test from 'node:test';
import { extractScryfallImageUrl, normalizeCardName, resolveCardImages } from './scryfall.js';

test('extractScryfallImageUrl reads image_uris.normal', () => {
  assert.equal(
    extractScryfallImageUrl({ image_uris: { normal: 'https://img.test/front.jpg' } }),
    'https://img.test/front.jpg'
  );
});

test('extractScryfallImageUrl falls back to first face normal image', () => {
  assert.equal(
    extractScryfallImageUrl({
      card_faces: [
        { image_uris: { normal: 'https://img.test/face-a.jpg' } },
        { image_uris: { normal: 'https://img.test/face-b.jpg' } }
      ]
    }),
    'https://img.test/face-a.jpg'
  );
});

test('extractScryfallImageUrl returns null without normal image', () => {
  assert.equal(extractScryfallImageUrl({}), null);
  assert.equal(extractScryfallImageUrl({ card_faces: [{}] }), null);
});

test('normalizeCardName trims, lowers, and collapses whitespace', () => {
  assert.equal(normalizeCardName('  Lightning   Bolt  '), 'lightning bolt');
});

test('resolveCardImages resolves card names to image URLs from Scryfall collection', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async () => new Response(JSON.stringify({
    data: [
      { name: 'Lightning Bolt', image_uris: { normal: 'https://img.test/bolt.jpg' } },
      { name: 'Island', card_faces: [{ image_uris: { normal: 'https://img.test/island.jpg' } }] }
    ]
  }), { status: 200 })) as typeof fetch;

  try {
    const result = await resolveCardImages(['Lightning Bolt', 'Island', 'Unknown Card']);

    assert.equal(result.get('lightning bolt')?.imageUrl, 'https://img.test/bolt.jpg');
    assert.equal(result.get('island')?.imageUrl, 'https://img.test/island.jpg');
    assert.equal(result.get('unknown card')?.imageUrl, null);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('resolveCardImages returns null imageUrl when Scryfall chunk fails', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async () => new Response('error', { status: 500 })) as typeof fetch;

  try {
    const result = await resolveCardImages(['Lightning Bolt']);

    assert.equal(result.get('lightning bolt')?.imageUrl, null);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
