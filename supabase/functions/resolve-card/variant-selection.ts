export type OverrideArtRow = {
  image_url: string;
};

export type OverrideCardWithImage = {
  imageUrl: string | null;
};

export function stableVariantIndex(catalogId: number, variantCount: number) {
  if (!Number.isInteger(variantCount) || variantCount <= 0) {
    return -1;
  }

  let hash = 2166136261;
  const key = String(catalogId);
  for (let index = 0; index < key.length; index++) {
    hash ^= key.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }

  return (hash >>> 0) % variantCount;
}

export function selectStableArtVariant(
  catalogId: number,
  variants: OverrideArtRow[],
) {
  const index = stableVariantIndex(catalogId, variants.length);
  return index >= 0 ? variants[index] : null;
}

export function applyStableOverrideArt<T extends OverrideCardWithImage>(
  card: T,
  catalogId: number,
  variants: OverrideArtRow[],
): T {
  const variant = selectStableArtVariant(catalogId, variants);
  if (!variant) {
    return card;
  }

  return {
    ...card,
    imageUrl: variant.image_url,
  };
}
