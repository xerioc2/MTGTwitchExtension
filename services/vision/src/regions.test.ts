import assert from 'node:assert/strict';
import test from 'node:test';
import { mapToDetectionRegions } from './regions.js';

test('mapToDetectionRegions produces LLM DetectionRegion shape', () => {
  const now = new Date('2026-06-24T12:00:00.000Z');
  const bbox = { x: 0.1, y: 0.2, w: 0.3, h: 0.4 };
  const regions = mapToDetectionRegions(
    [{ name: 'Lightning Bolt', bbox }],
    new Map([['lightning bolt', { name: 'Lightning Bolt', imageUrl: 'https://img.test/bolt.jpg' }]]),
    { channelId: 'dev-channel', ttlMs: 30000, now }
  );

  assert.equal(regions.length, 1);
  assert.equal(regions[0].channelId, 'dev-channel');
  assert.equal(regions[0].cardId, null);
  assert.equal(regions[0].catalogId, null);
  assert.equal(regions[0].cardName, 'Lightning Bolt');
  assert.equal(regions[0].zone, 'UNKNOWN');
  assert.equal(regions[0].imageUrl, 'https://img.test/bolt.jpg');
  assert.equal(regions[0].confidence, 0.5);
  assert.equal(regions[0].bbox, bbox);
  assert.equal(regions[0].source, 'LLM');
  assert.equal(regions[0].frameWidth, null);
  assert.equal(regions[0].frameHeight, null);
  assert.equal(regions[0].observedAt, '2026-06-24T12:00:00.000Z');
  assert.equal(regions[0].expiresAt, '2026-06-24T12:00:30.000Z');
  assert.ok(new Date(regions[0].expiresAt) > new Date(regions[0].observedAt));
  assert.match(regions[0].id, /^[0-9a-f-]{36}$/);
});

test('mapToDetectionRegions falls back to vision name and null image', () => {
  const regions = mapToDetectionRegions(
    [{ name: 'Island', bbox: { x: 0, y: 0, w: 0.1, h: 0.2 } }],
    new Map(),
    { channelId: 'dev-channel', now: new Date('2026-06-24T12:00:00.000Z') }
  );

  assert.equal(regions[0].cardName, 'Island');
  assert.equal(regions[0].imageUrl, null);
});
