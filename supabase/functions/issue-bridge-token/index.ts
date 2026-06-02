const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ADMIN_KEY = Deno.env.get("MTGO_SUPABASE_SECRET_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const TWITCH_CLIENT_ID = Deno.env.get("TWITCH_CLIENT_ID") ?? "";
const TWITCH_CLIENT_SECRET = Deno.env.get("TWITCH_CLIENT_SECRET") ?? "";

type IssueBridgeTokenRequest = {
  code?: string;
  codeVerifier?: string;
  redirectUri?: string;
};

type TwitchTokenResponse = {
  access_token?: string;
};

type TwitchUsersResponse = {
  data?: TwitchUser[];
};

type TwitchUser = {
  id: string;
  login: string;
};

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders() });
  }

  if (request.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  if (!SUPABASE_URL || !SUPABASE_ADMIN_KEY || !TWITCH_CLIENT_ID || !TWITCH_CLIENT_SECRET) {
    return json({ error: "Token issuer is not configured" }, 500);
  }

  const body = await parseRequestBody(request);
  if (!body?.code || !body.codeVerifier || !body.redirectUri) {
    return json({ error: "code, codeVerifier, and redirectUri are required" }, 400);
  }

  const twitchAccessToken = await exchangeTwitchCode(body.code, body.codeVerifier, body.redirectUri);
  if (!twitchAccessToken) {
    return json({ error: "Twitch token exchange failed" }, 401);
  }

  const twitchUser = await verifyTwitchUser(twitchAccessToken);
  if (!twitchUser) {
    return json({ error: "Unauthorized" }, 401);
  }

  const channelId = sanitizeChannelId(twitchUser.login);
  if (!channelId) {
    return json({ error: "Invalid Twitch login" }, 400);
  }

  const bridgeToken = randomHexToken(32);
  const bridgeTokenHash = await sha256Hex(bridgeToken);

  const revokeResponse = await fetch(
    `${restUrl("streamer_relays")}?twitch_user_id=eq.${encodeURIComponent(twitchUser.id)}&revoked_at=is.null`,
    {
      method: "PATCH",
      headers: supabaseHeaders(),
      body: JSON.stringify({ revoked_at: new Date().toISOString() })
    }
  );

  if (!revokeResponse.ok) {
    return json({ error: "Failed to revoke previous bridge token" }, 502);
  }

  const insertResponse = await fetch(restUrl("streamer_relays"), {
    method: "POST",
    headers: supabaseHeaders(),
    body: JSON.stringify({
      twitch_user_id: twitchUser.id,
      twitch_login: twitchUser.login,
      bridge_token_hash: bridgeTokenHash,
      created_at: new Date().toISOString()
    })
  });

  if (!insertResponse.ok) {
    return json({ error: "Failed to issue bridge token" }, 502);
  }

  return json({
    channelId,
    bridgeToken,
    relayFunctionUrl: `${SUPABASE_URL.replace(/\/+$/, "")}/functions/v1/publish-game-state`
  });
});

async function parseRequestBody(request: Request) {
  try {
    return await request.json() as IssueBridgeTokenRequest;
  } catch {
    return null;
  }
}

async function exchangeTwitchCode(code: string, codeVerifier: string, redirectUri: string) {
  const formBody = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: TWITCH_CLIENT_ID,
    client_secret: TWITCH_CLIENT_SECRET,
    code,
    code_verifier: codeVerifier,
    redirect_uri: redirectUri
  });

  const response = await fetch("https://id.twitch.tv/oauth2/token", {
    method: "POST",
    headers: {
      "content-type": "application/x-www-form-urlencoded"
    },
    body: formBody
  });

  if (!response.ok) {
    return null;
  }

  const payload = await response.json() as TwitchTokenResponse;
  return payload.access_token ?? null;
}

async function verifyTwitchUser(accessToken: string) {
  const response = await fetch("https://api.twitch.tv/helix/users", {
    headers: {
      "authorization": `Bearer ${accessToken}`,
      "client-id": TWITCH_CLIENT_ID
    }
  });

  if (!response.ok) {
    return null;
  }

  const payload = await response.json() as TwitchUsersResponse;
  return payload.data?.[0] ?? null;
}

function randomHexToken(byteLength: number) {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return bytesToHex(bytes);
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

function restUrl(table: string) {
  return `${SUPABASE_URL.replace(/\/+$/, "")}/rest/v1/${table}`;
}

function supabaseHeaders() {
  const headers: Record<string, string> = {
    "content-type": "application/json",
    "apikey": SUPABASE_ADMIN_KEY
  };

  headers.authorization = SUPABASE_ADMIN_KEY.startsWith("sb_secret_")
    ? SUPABASE_ADMIN_KEY
    : `Bearer ${SUPABASE_ADMIN_KEY}`;

  return headers;
}

function sanitizeChannelId(channelId: unknown) {
  if (typeof channelId !== "string") {
    return "";
  }

  const trimmed = channelId.trim().toLowerCase();
  return /^[a-z0-9_]{3,32}$/.test(trimmed) ? trimmed : "";
}

function corsHeaders() {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "POST, OPTIONS",
    "access-control-allow-headers": "content-type"
  };
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders(),
      "content-type": "application/json"
    }
  });
}
