import assert from 'node:assert/strict';
import test from 'node:test';
import { publishRegions } from './relay.js';

test('direct relay publishing uses the detection-regions event', async () => {
  let requestBody = '';
  const didPublish = await publishRegions([], {
    supabaseUrl: 'https://example.supabase.co',
    serviceRoleKey: 'dev-service-role',
    channelId: 'dev-channel',
    fetchImpl: async (_input, init) => {
      requestBody = String(init?.body ?? '');
      return { ok: true } as Response;
    }
  });

  const payload = JSON.parse(requestBody);
  assert.equal(didPublish, true);
  assert.equal(payload.messages[0].topic, 'game-state:dev-channel');
  assert.equal(payload.messages[0].event, 'detection-regions');
  assert.deepEqual(payload.messages[0].payload, { detectionRegions: [] });
});
