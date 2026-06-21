export type DetectionZone = 'HAND' | 'BATTLEFIELD' | 'GRAVEYARD' | 'EXILE' | 'STACK' | 'UNKNOWN' | string;

export type DetectionSource = 'MOCK' | 'MANUAL' | 'SCREEN_SCANNER' | 'OBS_PLUGIN' | string;

export type DetectionBbox = {
  x: number;
  y: number;
  w: number;
  h: number;
};

export type DetectionRegion = {
  id: string;
  channelId: string;
  cardId: string | null;
  catalogId: number | null;
  cardName: string;
  zone: DetectionZone;
  imageUrl: string | null;
  confidence: number;
  bbox: DetectionBbox;
  source: DetectionSource;
  frameWidth: number | null;
  frameHeight: number | null;
  observedAt: string;
  expiresAt: string;
};
