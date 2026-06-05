const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ADMIN_KEY = Deno.env.get("MTGO_SUPABASE_SECRET_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const GAME_STATE_EVENT = "game-state";

type PublishRequest = {
  channelId?: string;
  gameState?: unknown;
};

type StreamerRelayRow = {
  twitch_login: string;
};

Deno.serve(async (request) => {
  if (request.method !== "POST" && request.method !== "DELETE") {
    return json({ error: "Method not allowed" }, 405);
  }

  const token = bearerToken(request);

  if (!token) {
    return json({ error: "Unauthorized" }, 401);
  }

  if (!SUPABASE_URL || !SUPABASE_ADMIN_KEY) {
    return json({ error: "Relay is not configured" }, 500);
  }

  const tokenHash = await sha256Hex(token);

  if (request.method === "DELETE") {
    return revokeStreamerRelay(tokenHash);
  }

  let payload: PublishRequest;
  try {
    payload = await request.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }

  if (payload.gameState == null) {
    return json({ error: "gameState is required" }, 400);
  }

  const relay = await findStreamerRelay(tokenHash);
  if (!relay) {
    return json({ error: "Unauthorized" }, 401);
  }

  const channelId = sanitizeChannelId(relay.twitch_login);
  if (!channelId) {
    return json({ error: "Invalid streamer relay channel" }, 500);
  }

  const response = await fetch(`${SUPABASE_URL.replace(/\/+$/, "")}/realtime/v1/api/broadcast`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...supabaseHeaders()
    },
    body: JSON.stringify({
      messages: [
        {
          topic: `game-state:${channelId}`,
          event: GAME_STATE_EVENT,
          payload: payload.gameState
        }
      ]
    })
  });

  if (!response.ok) {
    return json({ error: "Failed to publish game state" }, 502);
  }

  return json({ ok: true, channelId });
});

function bearerToken(request: Request) {
  const authorization = request.headers.get("authorization") ?? "";
  return authorization.replace(/^Bearer\s+/i, "").trim();
}

async function findStreamerRelay(bridgeTokenHash: string) {
  const params = new URLSearchParams({
    bridge_token_hash: `eq.${bridgeTokenHash}`,
    revoked_at: "is.null",
    select: "twitch_login",
    limit: "1"
  });

  const response = await fetch(`${SUPABASE_URL.replace(/\/+$/, "")}/rest/v1/streamer_relays?${params}`, {
    headers: {
      ...supabaseHeaders()
    }
  });

  if (!response.ok) {
    return null;
  }

  const rows = await response.json() as StreamerRelayRow[];
  return rows[0] ?? null;
}

async function revokeStreamerRelay(bridgeTokenHash: string) {
  const params = new URLSearchParams({
    bridge_token_hash: `eq.${bridgeTokenHash}`,
    revoked_at: "is.null"
  });

  const response = await fetch(`${SUPABASE_URL.replace(/\/+$/, "")}/rest/v1/streamer_relays?${params}`, {
    method: "PATCH",
    headers: {
      "content-type": "application/json",
      "prefer": "return=minimal",
      ...supabaseHeaders()
    },
    body: JSON.stringify({
      revoked_at: new Date().toISOString()
    })
  });

  if (!response.ok) {
    return json({ error: "Failed to revoke bridge token" }, 502);
  }

  return json({ ok: true });
}

async function sha256Hex(value: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return bytesToHex(new Uint8Array(digest));
}

function bytesToHex(bytes: Uint8Array) {
  return Array.from(bytes)
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function sanitizeChannelId(channelId: unknown) {
  if (typeof channelId !== "string") {
    return "";
  }

  const trimmed = channelId.trim().toLowerCase();
  return /^[a-z0-9_]{3,32}$/.test(trimmed) ? trimmed : "";
}

function supabaseHeaders() {
  const headers: Record<string, string> = {
    "apikey": SUPABASE_ADMIN_KEY
  };

  headers.authorization = `Bearer ${SUPABASE_ADMIN_KEY}`;

  return headers;
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json"
    }
  });
}
