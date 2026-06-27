import assert from 'node:assert/strict';
import test from 'node:test';
import { buildPrompt, GeminiVisionProvider, geminiBoxToBbox, isRetryableStatus, parseGeminiCards, parseModelList } from './gemini.js';

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

test('buildPrompt excludes UI, overlays, lists, hidden cards, and preserves closed vocabulary', () => {
  const prompt = buildPrompt({ knownCards: ['Lightning Bolt', 'Island'] });

  assert.match(prompt, /deck lists/i);
  assert.match(prompt, /decklist panels/i);
  assert.match(prompt, /card galleries/i);
  assert.match(prompt, /card browsers/i);
  assert.match(prompt, /collection lists/i);
  assert.match(prompt, /sideboard lists/i);
  assert.match(prompt, /menus, buttons, UI chrome/i);
  assert.match(prompt, /usernames, channel names, stream overlays, chat/i);
  assert.match(prompt, /physically in play|physical card objects/i);
  assert.match(prompt, /player's hand/i);
  assert.match(prompt, /stack/i);
  assert.match(prompt, /face-down cards/i);
  assert.match(prompt, /Do not guess/i);
  assert.match(prompt, /Lightning Bolt/);
  assert.match(prompt, /Island/);
});

test('isRetryableStatus treats throttling and unavailable as retryable', () => {
  assert.equal(isRetryableStatus(503), true);
  assert.equal(isRetryableStatus(429), true);
  assert.equal(isRetryableStatus(200), false);
  assert.equal(isRetryableStatus(400), false);
  assert.equal(isRetryableStatus(404), false);
});

test('parseModelList splits, trims, drops empties, falls back, and prepends GEMINI_MODEL', () => {
  assert.deepEqual(
    parseModelList(' gemini-2.5-flash, , gemini-2.5-flash-lite ', 'fallback'),
    ['gemini-2.5-flash', 'gemini-2.5-flash-lite']
  );
  assert.deepEqual(
    parseModelList(undefined, 'gemini-2.5-flash,gemini-2.5-flash-lite'),
    ['gemini-2.5-flash', 'gemini-2.5-flash-lite']
  );
  assert.deepEqual(
    parseModelList('gemini-2.5-flash-lite', 'fallback', 'gemini-2.5-flash'),
    ['gemini-2.5-flash', 'gemini-2.5-flash-lite']
  );
  assert.deepEqual(
    parseModelList('gemini-2.5-flash', 'fallback', 'gemini-2.5-flash'),
    ['gemini-2.5-flash']
  );
});

test('GeminiVisionProvider retries retryable failures and falls back to next model', async () => {
  const originalFetch = globalThis.fetch;
  const originalGeminiModels = process.env.GEMINI_MODELS;
  const originalGeminiModel = process.env.GEMINI_MODEL;
  const requestedUrls: string[] = [];

  process.env.GEMINI_MODELS = 'gemini-primary,gemini-fallback';
  delete process.env.GEMINI_MODEL;

  globalThis.fetch = (async (input) => {
    const url = String(input);
    requestedUrls.push(url);

    if (url.includes('gemini-primary')) {
      return new Response('{"error":{"code":503}}', {
        status: 503,
        headers: { 'retry-after': '0' }
      });
    }

    return new Response(JSON.stringify({
      candidates: [{
        content: {
          parts: [{
            text: JSON.stringify([{ label: 'Island', box_2d: [0, 0, 100, 100] }])
          }]
        }
      }]
    }), { status: 200 });
  }) as typeof fetch;

  try {
    const provider = new GeminiVisionProvider({ apiKey: 'fake' });
    const result = await provider.detect({ dataBase64: 'abc', mimeType: 'image/jpeg' });

    assert.equal(requestedUrls.filter((url) => url.includes('gemini-primary')).length, 3);
    assert.equal(requestedUrls.filter((url) => url.includes('gemini-fallback')).length, 1);
    assert.deepEqual(result.cards, [
      { name: 'Island', bbox: { x: 0, y: 0, w: 0.1, h: 0.1 } }
    ]);
  } finally {
    globalThis.fetch = originalFetch;
    restoreEnv('GEMINI_MODELS', originalGeminiModels);
    restoreEnv('GEMINI_MODEL', originalGeminiModel);
  }
});

function restoreEnv(key: string, value: string | undefined): void {
  if (value === undefined) {
    delete process.env[key];
    return;
  }

  process.env[key] = value;
}
