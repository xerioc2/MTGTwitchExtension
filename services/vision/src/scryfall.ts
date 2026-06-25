const SCRYFALL_COLLECTION_URL = 'https://api.scryfall.com/cards/collection';
const SCRYFALL_CHUNK_SIZE = 75;
const SCRYFALL_CHUNK_DELAY_MS = 75;

type ScryfallCard = {
  name?: unknown;
  image_uris?: {
    normal?: unknown;
  };
  card_faces?: Array<{
    image_uris?: {
      normal?: unknown;
    };
  }>;
};

type ScryfallCollectionResponse = {
  data?: unknown;
};

export type ResolvedCardImage = {
  name: string;
  imageUrl: string | null;
};

export async function resolveCardImages(names: string[]): Promise<Map<string, ResolvedCardImage>> {
  const uniqueNames = Array.from(new Set(names.map((name) => name.replace(/\s+/g, ' ').trim()).filter(Boolean)));
  const resolved = new Map<string, ResolvedCardImage>();

  for (const name of uniqueNames) {
    resolved.set(normalizeCardName(name), { name, imageUrl: null });
  }

  for (let index = 0; index < uniqueNames.length; index += SCRYFALL_CHUNK_SIZE) {
    const chunk = uniqueNames.slice(index, index + SCRYFALL_CHUNK_SIZE);

    if (index > 0) {
      await sleep(SCRYFALL_CHUNK_DELAY_MS);
    }

    try {
      const response = await fetch(SCRYFALL_COLLECTION_URL, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
          'User-Agent': 'MTGTwitchVision/0.0.1'
        },
        body: JSON.stringify({
          identifiers: chunk.map((name) => ({ name }))
        })
      });

      if (!response.ok) {
        continue;
      }

      const payload = await response.json() as ScryfallCollectionResponse;
      const cards = Array.isArray(payload.data) ? payload.data.filter(isRecord) as ScryfallCard[] : [];

      for (const card of cards) {
        const cardName = typeof card.name === 'string' ? card.name.replace(/\s+/g, ' ').trim() : '';
        if (!cardName) {
          continue;
        }

        resolved.set(normalizeCardName(cardName), {
          name: cardName,
          imageUrl: extractScryfallImageUrl(card)
        });
      }
    } catch {
      continue;
    }
  }

  return resolved;
}

export function extractScryfallImageUrl(card: ScryfallCard): string | null {
  if (typeof card.image_uris?.normal === 'string') {
    return card.image_uris.normal;
  }

  const faceImage = card.card_faces?.find((face) => typeof face.image_uris?.normal === 'string')?.image_uris?.normal;
  return typeof faceImage === 'string' ? faceImage : null;
}

export function normalizeCardName(name: string): string {
  return name.replace(/\s+/g, ' ').trim().toLowerCase();
}

function sleep(milliseconds: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
  });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
