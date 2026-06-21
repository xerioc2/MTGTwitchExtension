import { useMemo } from 'react';

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

  return {
    ...region,
    confidence: clamp01(region.confidence),
    bbox: {
      x: clamp01(bbox.x),
      y: clamp01(bbox.y),
      w: clamp01(bbox.w),
      h: clamp01(bbox.h)
    }
  };
}

function clamp01(value) {
  if (!Number.isFinite(value)) {
    return 0;
  }

  return Math.max(0, Math.min(1, value));
}
