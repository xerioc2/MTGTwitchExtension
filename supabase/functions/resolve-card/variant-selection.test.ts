import {
  applyStableOverrideArt,
  selectStableArtVariant,
  stableVariantIndex,
} from "./variant-selection.ts";

function assert(condition: unknown, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

function assertEquals<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, got ${actual}`);
  }
}

Deno.test("stableVariantIndex is deterministic and bounded", () => {
  const first = stableVariantIndex(139517, 8);
  const second = stableVariantIndex(139517, 8);

  assertEquals(first, second, "same catalog id should pick the same index");
  assert(first >= 0 && first < 8, "index should stay within variant count");
  assert(
    stableVariantIndex(139517, 0) === -1,
    "zero variants should return sentinel index",
  );
});

Deno.test("selectStableArtVariant is a pure function of catalog id and variant count", () => {
  const variants = [
    { image_url: "https://example.test/a.jpg" },
    { image_url: "https://example.test/b.jpg" },
    { image_url: "https://example.test/c.jpg" },
  ];

  const first = selectStableArtVariant(125873, variants);
  const second = selectStableArtVariant(125873, variants);

  assertEquals(
    first?.image_url,
    second?.image_url,
    "same catalog id should pick same variant",
  );
  assertEquals(
    selectStableArtVariant(125873, [])?.image_url,
    undefined,
    "empty variants should not pick art",
  );
});

Deno.test("applyStableOverrideArt preserves fallback image when no variants exist", () => {
  const card = {
    imageUrl: "https://example.test/fallback.jpg",
    name: "Cat Token",
  };

  const result = applyStableOverrideArt(card, 125873, []);

  assertEquals(
    result.imageUrl,
    "https://example.test/fallback.jpg",
    "fallback image should remain unchanged",
  );
});
