export type StreamerRelayRow = {
  twitch_user_id: string;
  twitch_login: string;
};

export function relayChannelIds(
  relay: StreamerRelayRow,
  publishLegacyLoginTopic: boolean,
) {
  const twitchUserChannelId = sanitizeTwitchUserId(relay.twitch_user_id);
  if (!twitchUserChannelId) {
    return [];
  }

  if (!publishLegacyLoginTopic) {
    return [twitchUserChannelId];
  }

  const twitchLoginChannelId = sanitizeTwitchLogin(relay.twitch_login);
  return Array.from(
    new Set([twitchUserChannelId, twitchLoginChannelId].filter(Boolean)),
  );
}

export function sanitizeTwitchUserId(channelId: unknown) {
  if (typeof channelId !== "string") {
    return "";
  }

  const trimmed = channelId.trim();
  return /^\d{1,32}$/.test(trimmed) ? trimmed : "";
}

export function sanitizeTwitchLogin(login: unknown) {
  if (typeof login !== "string") {
    return "";
  }

  const trimmed = login.trim().toLowerCase();
  return /^[a-z0-9_]{3,25}$/.test(trimmed) ? trimmed : "";
}

export function stableGameStateJson(gameState: Record<string, unknown>) {
  const content = { ...gameState };
  delete content.updatedAt;
  return stableStringify(content);
}

export function stableStringify(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(",")}]`;
  }

  if (value && typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right));
    return `{${
      entries.map(([key, entry]) =>
        `${JSON.stringify(key)}:${stableStringify(entry)}`
      ).join(",")
    }}`;
  }

  return JSON.stringify(value) ?? "null";
}
