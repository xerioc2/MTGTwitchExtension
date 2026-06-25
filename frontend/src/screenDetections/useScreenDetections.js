import { useMemo } from 'react';

const HITBOX_PADDING = 0.02;

export function useScreenDetections({ enabled, gameState }) {
  return useMemo(() => {
    if (!enabled) {
      return [];
    }

    return (gameState.detectionRegions ?? [])
      .filter(isActiveRegion)
      .map(normalizeRegion);
  }, [enabled, gameState]);
}

function isActiveRegion(region) {
  if (!region?.bbox || !region.id || !region.cardName || !region.expiresAt) {
    return false;
  }

  const expiresAt = new Date(region.expiresAt);
  return Number.isFinite(expiresAt.getTime()) && expiresAt > new Date();
}

function normalizeRegion(region) {
  const bbox = region.bbox;
  const normalizedBbox = {
    x: clamp01(bbox.x),
    y: clamp01(bbox.y),
    w: clamp01(bbox.w),
    h: clamp01(bbox.h)
  };

  return {
    ...region,
    confidence: clamp01(region.confidence),
    bbox: normalizedBbox,
    hitbox: expandBbox(normalizedBbox, HITBOX_PADDING)
  };
}

export function expandBbox(bbox, padding = HITBOX_PADDING) {
  const x1 = clamp01(bbox.x - padding);
  const y1 = clamp01(bbox.y - padding);
  const x2 = clamp01(bbox.x + bbox.w + padding);
  const y2 = clamp01(bbox.y + bbox.h + padding);

  return {
    x: x1,
    y: y1,
    w: Math.max(0, x2 - x1),
    h: Math.max(0, y2 - y1)
  };
}

function clamp01(value) {
  if (!Number.isFinite(value)) {
    return 0;
  }

  return Math.max(0, Math.min(1, value));
}
