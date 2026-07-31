export function shouldConnectToSupabaseRelay({ configured, channelId, visible, ownsConnection = true }) {
  return Boolean(configured && channelId && visible && ownsConnection);
}

export function shouldScheduleRelayReconnect({ disposed, reconnectPending, visible }) {
  return !disposed && !reconnectPending && visible;
}

export function resolveExtensionVisibility({ documentVisible, twitchVisible }) {
  return Boolean(documentVisible && twitchVisible !== false);
}

export function shouldApplyRelayGameState(currentState, nextState) {
  if (!currentState?.updatedAt || !nextState?.updatedAt) {
    return true;
  }

  const currentUpdatedAt = Date.parse(currentState.updatedAt);
  const nextUpdatedAt = Date.parse(nextState.updatedAt);
  if (!Number.isFinite(currentUpdatedAt) || !Number.isFinite(nextUpdatedAt)) {
    return true;
  }

  return nextUpdatedAt >= currentUpdatedAt;
}

export async function fetchLatestRelayGameState({
  supabaseUrl,
  anonKey,
  channelId,
  maxAgeMs = 5 * 60 * 1000,
  nowMs = Date.now(),
  fetchImpl = fetch
}) {
  const endpoint = new URL('/rest/v1/latest_game_states', supabaseUrl);
  endpoint.searchParams.set('channel_id', `eq.${channelId}`);
  endpoint.searchParams.set('select', 'game_state,updated_at,last_seen_at');
  endpoint.searchParams.set('limit', '1');

  try {
    const response = await fetchImpl(endpoint, {
      headers: {
        apikey: anonKey,
        Authorization: `Bearer ${anonKey}`
      }
    });
    if (!response.ok) {
      return null;
    }

    const rows = await response.json();
    const row = rows?.[0];
    const lastSeenAt = Date.parse(row?.last_seen_at);
    if (!row?.game_state || !Number.isFinite(lastSeenAt) || nowMs - lastSeenAt > maxAgeMs) {
      return null;
    }

    return {
      ...row.game_state,
      updatedAt: row.game_state.updatedAt ?? row.updated_at
    };
  } catch {
    return null;
  }
}

export async function disconnectSupabaseRelay(supabase) {
  const cleanupTasks = [];

  try {
    cleanupTasks.push(Promise.resolve(supabase.removeAllChannels()));
  } catch {
    // Continue to the explicit socket disconnect even if channel teardown throws.
  }

  try {
    cleanupTasks.push(Promise.resolve(supabase.realtime.disconnect()));
  } catch {
    // Cleanup is best-effort while the iframe is hiding or unloading.
  }

  await Promise.allSettled(cleanupTasks);
}

export function requestRelayOwnership({ lockManager, lockName, onOwnershipChange }) {
  let ownsConnection = false;
  const updateOwnership = (nextValue) => {
    if (ownsConnection === nextValue) {
      return;
    }

    ownsConnection = nextValue;
    onOwnershipChange(nextValue);
  };

  if (!lockManager?.request) {
    updateOwnership(true);
    return () => updateOwnership(false);
  }

  const abortController = new AbortController();
  let disposed = false;
  let releaseLock = null;

  try {
    void lockManager.request(lockName, { signal: abortController.signal }, async (lock) => {
      if (!lock || disposed) {
        return;
      }

      updateOwnership(true);
      try {
        await new Promise((resolve) => {
          releaseLock = resolve;
        });
      } finally {
        updateOwnership(false);
      }
    }).catch((error) => {
      if (error?.name !== 'AbortError' && !disposed) {
        updateOwnership(true);
      }
    });
  } catch {
    updateOwnership(true);
  }

  return (releaseAfter = Promise.resolve()) => {
    disposed = true;
    abortController.abort();
    updateOwnership(false);
    void Promise.resolve(releaseAfter)
      .catch(() => {})
      .finally(() => releaseLock?.());
  };
}
