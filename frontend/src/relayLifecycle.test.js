import assert from 'node:assert/strict';
import test from 'node:test';
import {
  disconnectSupabaseRelay,
  fetchLatestRelayGameState,
  mergeRelayDetectionRegions,
  requestRelayOwnership,
  resolveExtensionVisibility,
  shouldApplyRelayGameState,
  shouldConnectToSupabaseRelay,
  shouldScheduleRelayReconnect
} from './relayLifecycle.js';

test('detection-region messages merge without clearing the current game state', () => {
  const currentState = {
    gameId: 123,
    hand: ['Lightning Bolt'],
    detectionRegions: []
  };
  const detectionRegions = [{ id: 'region-1', cardName: 'Lightning Bolt' }];

  assert.deepEqual(
    mergeRelayDetectionRegions(currentState, { detectionRegions }),
    {
      gameId: 123,
      hand: ['Lightning Bolt'],
      detectionRegions
    }
  );
  assert.equal(mergeRelayDetectionRegions(currentState, {}), currentState);
});

test('relay subscription requires configuration, authorization, and visibility', () => {
  assert.equal(shouldConnectToSupabaseRelay({ configured: true, channelId: '1234', visible: true }), true);
  assert.equal(shouldConnectToSupabaseRelay({ configured: true, channelId: '1234', visible: false }), false);
  assert.equal(shouldConnectToSupabaseRelay({ configured: true, channelId: '', visible: true }), false);
  assert.equal(shouldConnectToSupabaseRelay({
    configured: true,
    channelId: '1234',
    visible: true,
    ownsConnection: false
  }), false);
});

test('document and Twitch visibility both gate the relay', () => {
  assert.equal(resolveExtensionVisibility({ documentVisible: true, twitchVisible: undefined }), true);
  assert.equal(resolveExtensionVisibility({ documentVisible: false, twitchVisible: undefined }), false);
  assert.equal(resolveExtensionVisibility({ documentVisible: true, twitchVisible: false }), false);
  assert.equal(resolveExtensionVisibility({ documentVisible: false, twitchVisible: true }), false);
  assert.equal(resolveExtensionVisibility({ documentVisible: true, twitchVisible: true }), true);
});

test('older persisted state cannot replace a newer live state', () => {
  assert.equal(shouldApplyRelayGameState(
    { updatedAt: '2026-07-31T12:00:10Z' },
    { updatedAt: '2026-07-31T12:00:09Z' }
  ), false);
  assert.equal(shouldApplyRelayGameState(
    { updatedAt: '2026-07-31T12:00:10Z' },
    { updatedAt: '2026-07-31T12:00:11Z' }
  ), true);
});

test('latest relay state loads only while the bridge is fresh', async () => {
  const nowMs = Date.parse('2026-07-31T12:01:00Z');
  const fetchImpl = async () => ({
    ok: true,
    async json() {
      return [{
        game_state: { gameId: 123, updatedAt: '2026-07-31T12:00:00Z' },
        updated_at: '2026-07-31T12:00:00Z',
        last_seen_at: '2026-07-31T12:00:45Z'
      }];
    }
  });

  const state = await fetchLatestRelayGameState({
    supabaseUrl: 'https://example.supabase.co',
    anonKey: 'anon-key',
    channelId: '1234',
    nowMs,
    fetchImpl
  });
  assert.equal(state.gameId, 123);

  const staleState = await fetchLatestRelayGameState({
    supabaseUrl: 'https://example.supabase.co',
    anonKey: 'anon-key',
    channelId: '1234',
    maxAgeMs: 10_000,
    nowMs,
    fetchImpl
  });
  assert.equal(staleState, null);
});

test('hidden or disposed relay clients do not schedule reconnects', () => {
  assert.equal(shouldScheduleRelayReconnect({ disposed: false, reconnectPending: false, visible: true }), true);
  assert.equal(shouldScheduleRelayReconnect({ disposed: false, reconnectPending: false, visible: false }), false);
  assert.equal(shouldScheduleRelayReconnect({ disposed: true, reconnectPending: false, visible: true }), false);
  assert.equal(shouldScheduleRelayReconnect({ disposed: false, reconnectPending: true, visible: true }), false);
});

test('relay cleanup disconnects realtime without waiting for channel removal', async () => {
  const calls = [];
  let finishChannelRemoval;
  const supabase = {
    removeAllChannels() {
      calls.push('removeAllChannels');
      return new Promise((resolve) => {
        finishChannelRemoval = resolve;
      });
    },
    realtime: {
      disconnect() {
        calls.push('disconnect');
      }
    }
  };

  const cleanup = disconnectSupabaseRelay(supabase);
  assert.deepEqual(calls, ['removeAllChannels', 'disconnect']);
  finishChannelRemoval();
  await cleanup;
});

test('relay ownership is held until cleanup and then released', async () => {
  const ownershipChanges = [];
  let lockCallback;
  const lockManager = {
    request(_name, _options, callback) {
      lockCallback = callback;
      return Promise.resolve().then(() => callback({ name: 'relay-lock' }));
    }
  };

  const cleanup = requestRelayOwnership({
    lockManager,
    lockName: 'relay-lock',
    onOwnershipChange: (ownsConnection) => ownershipChanges.push(ownsConnection)
  });

  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(typeof lockCallback, 'function');
  assert.deepEqual(ownershipChanges, [true]);

  cleanup();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.deepEqual(ownershipChanges, [true, false]);
});

test('relay ownership keeps the lock until socket cleanup finishes', async () => {
  let resolveSocketCleanup;
  let lockReleased = false;
  const lockManager = {
    request(_name, _options, callback) {
      return callback({ name: 'relay-lock' }).then(() => {
        lockReleased = true;
      });
    }
  };
  const cleanup = requestRelayOwnership({
    lockManager,
    lockName: 'relay-lock',
    onOwnershipChange() {}
  });
  const socketCleanup = new Promise((resolve) => {
    resolveSocketCleanup = resolve;
  });

  await new Promise((resolve) => setTimeout(resolve, 0));
  cleanup(socketCleanup);
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(lockReleased, false);

  resolveSocketCleanup();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(lockReleased, true);
});

test('relay ownership falls back to the current surface without Web Locks', () => {
  const ownershipChanges = [];
  const cleanup = requestRelayOwnership({
    lockManager: null,
    lockName: 'relay-lock',
    onOwnershipChange: (ownsConnection) => ownershipChanges.push(ownsConnection)
  });

  cleanup();
  assert.deepEqual(ownershipChanges, [true, false]);
});

test('relay ownership falls back if the browser rejects the lock request', () => {
  const ownershipChanges = [];
  const cleanup = requestRelayOwnership({
    lockManager: {
      request() {
        throw new Error('Locks unavailable in this context');
      }
    },
    lockName: 'relay-lock',
    onOwnershipChange: (ownsConnection) => ownershipChanges.push(ownsConnection)
  });

  cleanup();
  assert.deepEqual(ownershipChanges, [true, false]);
});

test('two visible surfaces keep exactly one relay owner during handoff', async () => {
  let activeLocks = 0;
  let maximumActiveLocks = 0;
  const queue = [];
  const pump = () => {
    if (activeLocks > 0 || queue.length === 0) {
      return;
    }

    const next = queue.shift();
    activeLocks += 1;
    maximumActiveLocks = Math.max(maximumActiveLocks, activeLocks);
    void Promise.resolve(next.callback({ name: 'relay-lock' }))
      .then(next.resolve, next.reject)
      .finally(() => {
        activeLocks -= 1;
        pump();
      });
  };
  const lockManager = {
    request(_name, _options, callback) {
      return new Promise((resolve, reject) => {
        queue.push({ callback, resolve, reject });
        pump();
      });
    }
  };
  const firstChanges = [];
  const secondChanges = [];
  const cleanupFirst = requestRelayOwnership({
    lockManager,
    lockName: 'relay-lock',
    onOwnershipChange: (ownsConnection) => firstChanges.push(ownsConnection)
  });
  const cleanupSecond = requestRelayOwnership({
    lockManager,
    lockName: 'relay-lock',
    onOwnershipChange: (ownsConnection) => secondChanges.push(ownsConnection)
  });
  await new Promise((resolve) => setTimeout(resolve, 0));

  assert.deepEqual(firstChanges, [true]);
  assert.deepEqual(secondChanges, []);
  assert.equal(maximumActiveLocks, 1);

  let finishSocketCleanup;
  const socketCleanup = new Promise((resolve) => {
    finishSocketCleanup = resolve;
  });
  cleanupFirst(socketCleanup);
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.deepEqual(secondChanges, []);

  finishSocketCleanup();
  await new Promise((resolve) => setTimeout(resolve, 0));
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.deepEqual(secondChanges, [true]);
  assert.equal(maximumActiveLocks, 1);

  cleanupSecond();
});
