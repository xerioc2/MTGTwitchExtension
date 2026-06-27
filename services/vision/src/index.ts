export type {
  Bbox,
  DetectOptions,
  FrameInput,
  VisionCard,
  VisionProvider
} from './types.js';

export {
  GeminiVisionProvider,
  buildPrompt,
  geminiBoxToBbox,
  parseGeminiCards
} from './gemini.js';

export {
  resolveCardImages,
  extractScryfallImageUrl,
  normalizeCardName
} from './scryfall.js';

export {
  mapToDetectionRegions,
  splitResolvedVisionCards
} from './regions.js';

export type {
  DetectionRegion
} from './regions.js';

export {
  publishRegions
} from './relay.js';

export {
  renderDebugHtml
} from './debug.js';

export {
  extractTwitchFrame,
  normalizeChannelUrl,
  buildFfmpegArgs
} from './twitch.js';
