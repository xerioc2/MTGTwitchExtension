import type { Bbox, DetectOptions, FrameInput, VisionCard, VisionProvider } from './types.js';

const DEFAULT_GEMINI_MODELS = 'gemini-2.5-flash,gemini-2.5-flash-lite';
const GEMINI_ENDPOINT = 'https://generativelanguage.googleapis.com/v1beta/models';
const MAX_RETRIES_PER_MODEL = 2;
const DEFAULT_RETRY_DELAY_MS = 500;
const MAX_RETRY_DELAY_MS = 5000;

type GeminiBoxCandidate = {
  label?: unknown;
  name?: unknown;
  box_2d?: unknown;
  box2d?: unknown;
  bbox?: unknown;
};

type GeminiGenerateContentResponse = {
  candidates?: Array<{
    content?: {
      parts?: Array<{
        text?: string;
      }>;
    };
  }>;
};

export class GeminiVisionProvider implements VisionProvider {
  readonly apiKey: string;
  readonly model: string;
  readonly models: string[];

  constructor(opts: { apiKey: string; model?: string }) {
    this.apiKey = opts.apiKey;
    this.models = buildModelList(opts.model);
    this.model = this.models[0];
  }

  async detect(frame: FrameInput, opts: DetectOptions = {}): Promise<{ cards: VisionCard[] }> {
    if (!this.apiKey) {
      return { cards: [] };
    }

    for (const model of this.models) {
      for (let attempt = 0; attempt <= MAX_RETRIES_PER_MODEL; attempt += 1) {
        if (process.env.GEMINI_DEBUG === 'true') {
          console.error(`[gemini] trying model=${model} attempt=${attempt + 1}`);
        }

        try {
          const response = await fetchGemini(model, frame, opts, this.apiKey);

          if (!response.ok) {
            const errorBody = await response.text().catch(() => '');
            if (process.env.GEMINI_DEBUG === 'true') {
              console.error(`[gemini] model=${model} HTTP ${response.status} ${response.statusText}: ${errorBody.slice(0, 1000)}`);
            }

            if (isRetryableStatus(response.status) && attempt < MAX_RETRIES_PER_MODEL) {
              await sleep(parseRetryDelayMs(response, errorBody));
              continue;
            }

            break;
          }

          const payload = await response.json() as GeminiGenerateContentResponse;
          const text = extractText(payload);
          const cards = parseGeminiCards(text);
          if (process.env.GEMINI_DEBUG === 'true' && cards.length === 0) {
            console.error(`[gemini] model=${model} no cards parsed. raw model text (truncated): ${text.slice(0, 1000) || '<empty>'}`);
          }
          return { cards };
        } catch (error) {
          if (process.env.GEMINI_DEBUG === 'true') {
            console.error(`[gemini] model=${model} request failed: ${error instanceof Error ? error.message : String(error)}`);
          }
          break;
        }
      }
    }

    return { cards: [] };
  }
}

export function isRetryableStatus(code: number): boolean {
  return code === 429 || code === 503;
}

export function parseModelList(envValue: string | undefined, fallbackDefault: string, legacyModel?: string): string[] {
  const models = (envValue || fallbackDefault)
    .split(',')
    .map((model) => model.trim())
    .filter(Boolean);
  const legacyModels = legacyModel?.trim() ? [legacyModel.trim()] : [];
  const combinedModels = [...legacyModels, ...models];

  return combinedModels.length > 0
    ? Array.from(new Set(combinedModels))
    : parseModelList(fallbackDefault, DEFAULT_GEMINI_MODELS);
}

export function geminiBoxToBbox(box: readonly number[]): Bbox | null {
  if (box.length !== 4 || box.some((value) => !Number.isFinite(value))) {
    return null;
  }

  const [ymin, xmin, ymax, xmax] = box;
  const x = clamp01(xmin / 1000);
  const y = clamp01(ymin / 1000);
  const w = clamp01((xmax - xmin) / 1000);
  const h = clamp01((ymax - ymin) / 1000);

  if (w <= 0 || h <= 0) {
    return null;
  }

  return { x, y, w, h };
}

export function parseGeminiCards(text: string): VisionCard[] {
  const parsedCandidates = parseCandidateObjects(text);
  const cards: VisionCard[] = [];
  const seen = new Set<string>();

  for (const candidate of parsedCandidates) {
    const name = normalizeName(candidate.label ?? candidate.name);
    const rawBox = candidate.box_2d ?? candidate.box2d ?? candidate.bbox;
    const box = normalizeBox(rawBox);

    if (!name || !box) {
      continue;
    }

    const bbox = geminiBoxToBbox(box);
    if (!bbox) {
      continue;
    }

    const key = `${name}:${bbox.x}:${bbox.y}:${bbox.w}:${bbox.h}`;
    if (seen.has(key)) {
      continue;
    }

    seen.add(key);
    cards.push({ name, bbox });
  }

  return cards;
}

export function buildPrompt(opts: DetectOptions): string {
  const knownCards = normalizeKnownCards(opts.knownCards);
  const knownCardInstruction = knownCards.length > 0
    ? `Only report cards whose exact English name is in this list: ${knownCards.map((name) => JSON.stringify(name)).join(', ')}. Do not report any card outside this list.`
    : 'Report any Magic: The Gathering card whose exact English name is clearly readable.';

  return [
    'Find Magic: The Gathering cards in this image.',
    'Return JSON only: an array of objects with "label" and "box_2d".',
    '"label" must be the exact English card name visible on the card.',
    '"box_2d" must be [ymin, xmin, ymax, xmax] normalized from 0 to 1000.',
    'Use the full image frame as the coordinate space, top-left origin.',
    knownCardInstruction,
    'Only report actual physical card objects in the game: cards in play on the game board, cards in a player\'s hand, or cards on the stack.',
    'Ignore deck lists, decklist panels, card galleries, card browsers, collection lists, sideboard lists, menus, buttons, UI chrome, and any UI text.',
    'Ignore usernames, channel names, stream overlays, chat, and text references to cards.',
    'Exclude face-down cards, morph/disguise cards, libraries/decks, hidden zones, and any card whose name cannot actually be read.',
    'Do not guess. If no readable cards are visible, return [].'
  ].join('\n');
}

function buildModelList(explicitModel: string | undefined): string[] {
  if (explicitModel?.trim()) {
    return [explicitModel.trim()];
  }

  return parseModelList(process.env.GEMINI_MODELS, DEFAULT_GEMINI_MODELS, process.env.GEMINI_MODEL);
}

function fetchGemini(model: string, frame: FrameInput, opts: DetectOptions, apiKey: string): Promise<Response> {
  return fetch(`${GEMINI_ENDPOINT}/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      contents: [{
        role: 'user',
        parts: [
          { text: buildPrompt(opts) },
          {
            inlineData: {
              mimeType: frame.mimeType,
              data: frame.dataBase64
            }
          }
        ]
      }],
      generationConfig: {
        temperature: 0,
        responseMimeType: 'application/json'
      }
    })
  });
}

function parseRetryDelayMs(response: Response, body: string): number {
  const retryAfter = response.headers.get('retry-after');
  const retryAfterSeconds = retryAfter ? Number(retryAfter) : NaN;

  if (Number.isFinite(retryAfterSeconds) && retryAfterSeconds >= 0) {
    return Math.min(MAX_RETRY_DELAY_MS, retryAfterSeconds * 1000);
  }

  const retryDelayMatch = body.match(/"retryDelay"\s*:\s*"(\d+(?:\.\d+)?)s"/);
  if (retryDelayMatch) {
    return Math.min(MAX_RETRY_DELAY_MS, Number(retryDelayMatch[1]) * 1000);
  }

  return DEFAULT_RETRY_DELAY_MS;
}

function sleep(milliseconds: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
  });
}

function extractText(payload: GeminiGenerateContentResponse): string {
  return (payload.candidates ?? [])
    .flatMap((candidate) => candidate.content?.parts ?? [])
    .map((part) => part.text ?? '')
    .filter(Boolean)
    .join('\n');
}

function parseCandidateObjects(text: string): GeminiBoxCandidate[] {
  const trimmed = stripCodeFence(text.trim());

  try {
    const parsed = JSON.parse(trimmed) as unknown;
    return extractCandidates(parsed);
  } catch {
    return recoverCandidates(trimmed);
  }
}

function extractCandidates(value: unknown): GeminiBoxCandidate[] {
  if (Array.isArray(value)) {
    return value.filter(isRecord) as GeminiBoxCandidate[];
  }

  if (!isRecord(value)) {
    return [];
  }

  const cards = value.cards ?? value.objects ?? value.detections;
  if (Array.isArray(cards)) {
    return cards.filter(isRecord) as GeminiBoxCandidate[];
  }

  if (Array.isArray(value.card_detections)) {
    return value.card_detections.filter(isRecord) as GeminiBoxCandidate[];
  }

  return [value as GeminiBoxCandidate];
}

function recoverCandidates(text: string): GeminiBoxCandidate[] {
  const candidates: GeminiBoxCandidate[] = [];
  const objectPattern = /\{[^{}]*(?:"box_2d"|"box2d"|"bbox")[^{}]*}/gi;
  const matches = text.match(objectPattern) ?? [];

  for (const match of matches) {
    try {
      const parsed = JSON.parse(match) as unknown;
      if (isRecord(parsed)) {
        candidates.push(parsed as GeminiBoxCandidate);
      }
    } catch {
      const label = match.match(/"(?:label|name)"\s*:\s*"([^"]+)"/i)?.[1];
      const boxText = match.match(/"(?:box_2d|box2d|bbox)"\s*:\s*\[([^\]]+)]/i)?.[1];
      const box = boxText?.split(',').map((value) => Number(value.trim()));
      if (label && box) {
        candidates.push({ label, box_2d: box });
      }
    }
  }

  return candidates;
}

function stripCodeFence(text: string): string {
  const fenceMatch = text.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
  return fenceMatch?.[1] ?? text;
}

function normalizeBox(value: unknown): number[] | null {
  if (!Array.isArray(value) || value.length !== 4) {
    return null;
  }

  const box = value.map((entry) => Number(entry));
  return box.every(Number.isFinite) ? box : null;
}

function normalizeName(value: unknown): string {
  return typeof value === 'string' ? value.replace(/\s+/g, ' ').trim() : '';
}

function normalizeKnownCards(cards: string[] | undefined): string[] {
  return Array.from(new Set((cards ?? []).map(normalizeName).filter(Boolean)));
}

function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
