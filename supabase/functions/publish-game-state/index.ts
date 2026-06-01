const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const BRIDGE_PUBLISH_TOKEN = Deno.env.get("BRIDGE_PUBLISH_TOKEN") ?? "";
const GAME_STATE_EVENT = "game-state";

type PublishRequest = {
  channelId?: string;
  gameState?: unknown;
};

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const authorization = request.headers.get("authorization") ?? "";
  const token = authorization.replace(/^Bearer\s+/i, "").trim();

  if (!BRIDGE_PUBLISH_TOKEN || token !== BRIDGE_PUBLISH_TOKEN) {
    return json({ error: "Unauthorized" }, 401);
  }

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    return json({ error: "Relay is not configured" }, 500);
  }

  let payload: PublishRequest;
  try {
    payload = await request.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }

  const channelId = sanitizeChannelId(payload.channelId);
  if (!channelId || payload.gameState == null) {
    return json({ error: "channelId and gameState are required" }, 400);
  }

  const response = await fetch(`${SUPABASE_URL.replace(/\/+$/, "")}/realtime/v1/api/broadcast`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "apikey": SUPABASE_SERVICE_ROLE_KEY,
      "authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`
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

function sanitizeChannelId(channelId: unknown) {
  if (typeof channelId !== "string") {
    return "";
  }

  const trimmed = channelId.trim().toLowerCase();
  return /^[a-z0-9_]{3,32}$/.test(trimmed) ? trimmed : "";
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json"
    }
  });
}
