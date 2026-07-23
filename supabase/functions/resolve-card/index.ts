import { applyStableOverrideArt } from "./variant-selection.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ADMIN_KEY = Deno.env.get("MTGO_SUPABASE_SECRET_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const SCRYFALL_USER_AGENT = Deno.env.get("SCRYFALL_USER_AGENT") ?? "MTGTwitchExtension/0.0.1";

type ResolveCardRequest = {
  catalogId?: number;
};

type StreamerRelayRow = {
  twitch_user_id: string;
};

type CardRow = {
  mtgo_catalog_id: number;
  name: string;
  type_line?: string | null;
  mana_cost?: string | null;
  oracle_text?: string | null;
  image_url?: string | null;
  inferred_back_face?: boolean | null;
  token?: boolean | null;
  source?: string | null;
};

type OverrideArtRow = {
  image_url: string;
  scryfall_id?: string | null;
};

type ResolvedCard = {
  catalogId: number;
  name: string;
  typeLine: string;
  manaCost: string;
  oracleText: string;
  imageUrl: string | null;
  inferredBackFace: boolean;
  token: boolean;
};

type ScryfallCard = {
  name?: string;
  type_line?: string;
  mana_cost?: string;
  oracle_text?: string;
  image_uris?: ScryfallImageUris;
  layout?: string;
  card_faces?: ScryfallCardFace[];
};

type ScryfallCardFace = {
  name?: string;
  type_line?: string;
  mana_cost?: string;
  oracle_text?: string;
  image_uris?: ScryfallImageUris;
};

type ScryfallImageUris = {
  normal?: string;
};

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders() });
  }

  if (request.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const token = bearerToken(request);
  if (!token) {
    return json({ error: "Unauthorized" }, 401);
  }

  if (!SUPABASE_URL || !SUPABASE_ADMIN_KEY) {
    return json({ error: "Card resolver is not configured" }, 500);
  }

  const tokenHash = await sha256Hex(token);
  const relay = await findStreamerRelay(tokenHash);
  if (!relay) {
    return json({ error: "Unauthorized" }, 401);
  }

  const body = await parseRequestBody(request);
  const catalogId = Number(body?.catalogId);
  if (!Number.isInteger(catalogId) || catalogId <= 0) {
    return json({ error: "catalogId must be a positive integer" }, 400);
  }

  const override = await findOverride(catalogId);
  if (override) {
    return json(override);
  }

  const cachedCard = await findCachedCard(catalogId);
  if (cachedCard) {
    return json(cachedCard);
  }

  let resolvedCard: ResolvedCard | null;
  try {
    resolvedCard = await resolveFromScryfall(catalogId);
  } catch (error) {
    console.warn(`Scryfall resolution failed for MTGO catalog id ${catalogId}:`, error);
    return json({ error: "Scryfall resolution failed" }, 502);
  }

  if (!resolvedCard) {
    return json({ error: "Card not found" }, 404);
  }

  await cacheResolvedCard(resolvedCard);
  return json(resolvedCard);
});

async function parseRequestBody(request: Request) {
  try {
    return await request.json() as ResolveCardRequest;
  } catch {
    return null;
  }
}

async function findOverride(catalogId: number) {
  const params = new URLSearchParams({
    mtgo_catalog_id: `eq.${catalogId}`,
    enabled: "eq.true",
    select: "mtgo_catalog_id,name,type_line,mana_cost,oracle_text,image_url,token",
    limit: "1"
  });

  const rows = await restGet<CardRow>("mtgo_card_overrides", params);
  if (!rows[0]) {
    return null;
  }

  const artRows = await findOverrideArt(catalogId);
  const card = cardFromRow(rows[0], { inferredBackFace: false, token: true });
  return applyStableOverrideArt(card, catalogId, artRows);
}

async function findOverrideArt(catalogId: number) {
  const params = new URLSearchParams({
    mtgo_catalog_id: `eq.${catalogId}`,
    select: "image_url,scryfall_id",
    order: "id.asc"
  });

  return await restGet<OverrideArtRow>("mtgo_card_override_art", params);
}

async function findCachedCard(catalogId: number) {
  const params = new URLSearchParams({
    mtgo_catalog_id: `eq.${catalogId}`,
    select: "mtgo_catalog_id,name,type_line,mana_cost,oracle_text,image_url,inferred_back_face,token,source",
    limit: "1"
  });

  const rows = await restGet<CardRow>("mtgo_card_cache", params);
  return rows[0] ? cardFromRow(rows[0]) : null;
}

async function restGet<T>(table: string, params: URLSearchParams) {
  const response = await fetch(`${restUrl(table)}?${params}`, {
    headers: supabaseHeaders()
  });

  if (!response.ok) {
    console.warn(`Supabase read failed for ${table}: ${response.status}`);
    return [];
  }

  return await response.json() as T[];
}

function cardFromRow(row: CardRow, defaults?: { inferredBackFace?: boolean; token?: boolean }): ResolvedCard {
  return {
    catalogId: row.mtgo_catalog_id,
    name: row.name,
    typeLine: row.type_line ?? "",
    manaCost: row.mana_cost ?? "",
    oracleText: row.oracle_text ?? "",
    imageUrl: row.image_url ?? null,
    inferredBackFace: row.inferred_back_face ?? defaults?.inferredBackFace ?? false,
    token: row.token ?? defaults?.token ?? false
  };
}

async function resolveFromScryfall(catalogId: number) {
  const card = await fetchScryfallCard(catalogId);
  if (card) {
    return mapScryfallCard(catalogId, card, false);
  }

  return inferBackFace(catalogId);
}

async function inferBackFace(catalogId: number) {
  for (const offset of [1, 2]) {
    const neighborCatalogId = catalogId - offset;
    if (neighborCatalogId <= 0) {
      continue;
    }

    const neighborCard = await fetchScryfallCard(neighborCatalogId);
    if (!neighborCard) {
      continue;
    }

    if (!isDoubleFacedLayout(neighborCard.layout)) {
      return null;
    }

    const backFace = neighborCard.card_faces?.[1];
    if (!backFace) {
      return null;
    }

    return {
      catalogId,
      name: backFace.name ?? "",
      typeLine: backFace.type_line ?? "",
      manaCost: backFace.mana_cost ?? "",
      oracleText: backFace.oracle_text ?? "",
      imageUrl: normalImageUrl(backFace.image_uris, neighborCard.image_uris),
      inferredBackFace: true,
      token: false
    };
  }

  return null;
}

async function fetchScryfallCard(catalogId: number) {
  const response = await fetch(`https://api.scryfall.com/cards/mtgo/${catalogId}`, {
    headers: {
      "accept": "application/json;q=0.9,*/*;q=0.8",
      "user-agent": SCRYFALL_USER_AGENT
    }
  });

  if (response.status === 404 || response.status === 400) {
    return null;
  }

  if (!response.ok) {
    throw new Error(`Scryfall returned ${response.status}`);
  }

  return await response.json() as ScryfallCard;
}

function mapScryfallCard(catalogId: number, card: ScryfallCard, inferredBackFace: boolean): ResolvedCard {
  return {
    catalogId,
    name: card.name ?? "",
    typeLine: card.type_line ?? "",
    manaCost: card.mana_cost ?? "",
    oracleText: card.oracle_text ?? "",
    imageUrl: normalImageUrl(card.image_uris, card.card_faces?.[0]?.image_uris),
    inferredBackFace,
    token: false
  };
}

function isDoubleFacedLayout(layout: unknown) {
  const normalizedLayout = String(layout ?? "").toLowerCase();
  return normalizedLayout === "modal_dfc" || normalizedLayout === "transform";
}

function normalImageUrl(...imageUris: Array<ScryfallImageUris | undefined>) {
  for (const imageUri of imageUris) {
    if (imageUri?.normal) {
      return imageUri.normal;
    }
  }

  return null;
}

async function cacheResolvedCard(card: ResolvedCard) {
  const response = await fetch(`${restUrl("mtgo_card_cache")}?on_conflict=mtgo_catalog_id`, {
    method: "POST",
    headers: {
      ...supabaseHeaders(),
      "prefer": "resolution=merge-duplicates,return=minimal"
    },
    body: JSON.stringify({
      mtgo_catalog_id: card.catalogId,
      name: card.name,
      type_line: card.typeLine,
      mana_cost: card.manaCost,
      oracle_text: card.oracleText,
      image_url: card.imageUrl,
      inferred_back_face: card.inferredBackFace,
      token: card.token,
      source: card.inferredBackFace ? "scryfall_back_face" : "scryfall_exact",
      updated_at: new Date().toISOString()
    })
  });

  if (!response.ok) {
    console.warn(`Failed to cache MTGO catalog id ${card.catalogId}: ${response.status}`);
  }
}

async function findStreamerRelay(bridgeTokenHash: string) {
  const params = new URLSearchParams({
    bridge_token_hash: `eq.${bridgeTokenHash}`,
    revoked_at: "is.null",
    select: "twitch_user_id",
    limit: "1"
  });

  const response = await fetch(`${restUrl("streamer_relays")}?${params}`, {
    headers: supabaseHeaders()
  });

  if (!response.ok) {
    return null;
  }

  const rows = await response.json() as StreamerRelayRow[];
  return rows[0] ?? null;
}

function bearerToken(request: Request) {
  const authorization = request.headers.get("authorization") ?? "";
  return authorization.replace(/^Bearer\s+/i, "").trim();
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
  return {
    "content-type": "application/json",
    "apikey": SUPABASE_ADMIN_KEY,
    "authorization": `Bearer ${SUPABASE_ADMIN_KEY}`
  };
}

function corsHeaders() {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "POST, OPTIONS",
    "access-control-allow-headers": "authorization, content-type"
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
