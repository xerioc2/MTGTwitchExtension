import { randomUUID } from 'node:crypto';
import type { Bbox, VisionCard } from './types.js';
import { normalizeCardName, type ResolvedCardImage } from './scryfall.js';

export type DetectionRegion = {
  id: string;
  channelId: string;
  cardId: null;
  catalogId: null;
  cardName: string;
  zone: 'UNKNOWN';
  imageUrl: string | null;
  confidence: number;
  bbox: Bbox;
  source: 'LLM';
  frameWidth: null;
  frameHeight: null;
  observedAt: string;
  expiresAt: string;
};

export type MapToDetectionRegionsOptions = {
  channelId: string;
  ttlMs?: number;
  now?: Date;
};

export type SplitResolvedVisionCardsResult = {
  resolved: VisionCard[];
  unresolved: VisionCard[];
};

export function mapToDetectionRegions(
  visionCards: VisionCard[],
  resolved: Map<string, ResolvedCardImage>,
  opts: MapToDetectionRegionsOptions
): DetectionRegion[] {
  const now = opts.now ?? new Date();
  const ttlMs = opts.ttlMs ?? 30000;
  const observedAt = now.toISOString();
  const expiresAt = new Date(now.getTime() + ttlMs).toISOString();

  return visionCards.map((card) => {
    const resolvedCard = resolved.get(normalizeCardName(card.name));

    return {
      id: randomUUID(),
      channelId: opts.channelId,
      cardId: null,
      catalogId: null,
      cardName: resolvedCard?.name ?? card.name,
      zone: 'UNKNOWN',
      imageUrl: resolvedCard?.imageUrl ?? null,
      confidence: 0.5,
      bbox: card.bbox,
      source: 'LLM',
      frameWidth: null,
      frameHeight: null,
      observedAt,
      expiresAt
    };
  });
}

export function splitResolvedVisionCards(
  visionCards: VisionCard[],
  resolved: Map<string, ResolvedCardImage>
): SplitResolvedVisionCardsResult {
  const partitions: SplitResolvedVisionCardsResult = {
    resolved: [],
    unresolved: []
  };

  for (const card of visionCards) {
    const resolvedCard = resolved.get(normalizeCardName(card.name));
    if (resolvedCard?.imageUrl) {
      partitions.resolved.push(card);
    } else {
      partitions.unresolved.push(card);
    }
  }

  return partitions;
}
