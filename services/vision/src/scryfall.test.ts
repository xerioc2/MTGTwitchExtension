import assert from 'node:assert/strict';
import test from 'node:test';
import { extractScryfallImageUrl, normalizeCardName } from './scryfall.js';

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
