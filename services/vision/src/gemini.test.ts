import assert from 'node:assert/strict';
import test from 'node:test';
import { geminiBoxToBbox, parseGeminiCards } from './gemini.js';

test('geminiBoxToBbox converts [ymin, xmin, ymax, xmax] 0..1000 boxes', () => {
  assert.deepEqual(geminiBoxToBbox([100, 200, 700, 600]), {
    x: 0.2,
    y: 0.1,
    w: 0.4,
    h: 0.6
  });
});

test('geminiBoxToBbox clamps coordinates and dimensions to [0,1]', () => {
  assert.deepEqual(geminiBoxToBbox([-50, -100, 1300, 1400]), {
    x: 0,
    y: 0,
    w: 1,
    h: 1
  });
});

test('geminiBoxToBbox drops degenerate boxes', () => {
  assert.equal(geminiBoxToBbox([100, 100, 100, 400]), null);
  assert.equal(geminiBoxToBbox([100, 400, 500, 400]), null);
  assert.equal(geminiBoxToBbox([100, 400, 500]), null);
});

test('parseGeminiCards recovers valid cards and dedupes exact duplicate boxes', () => {
  const cards = parseGeminiCards(JSON.stringify([
    { label: ' Lightning   Bolt ', box_2d: [100, 200, 700, 600] },
    { label: 'Lightning Bolt', box_2d: [100, 200, 700, 600] },
    { label: 'Island', box_2d: [0, 0, 100, 100] },
    { label: '', box_2d: [0, 0, 100, 100] },
    { label: 'Bad Box', box_2d: [1, 1, 1, 2] }
  ]));

  assert.deepEqual(cards, [
    { name: 'Lightning Bolt', bbox: { x: 0.2, y: 0.1, w: 0.4, h: 0.6 } },
    { name: 'Island', bbox: { x: 0, y: 0, w: 0.1, h: 0.1 } }
  ]);
});
