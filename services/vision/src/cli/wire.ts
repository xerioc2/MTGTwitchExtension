import { readFile, writeFile } from 'node:fs/promises';
import { extname, resolve } from 'node:path';
import { GeminiVisionProvider } from '../gemini.js';
import { loadLocalEnv } from '../env.js';
import { publishRegions } from '../relay.js';
import { mapToDetectionRegions } from '../regions.js';
import { normalizeCardName, resolveCardImages } from '../scryfall.js';
import { renderDebugHtml } from '../debug.js';
import { extractTwitchFrame } from '../twitch.js';
import type { FrameInput } from '../types.js';

loadLocalEnv();

const args = process.argv.slice(2);
const frameSource = parseFrameSource(args);
const loopSeconds = parseLoopSeconds(args);
const debugPath = parseDebugPath(args);
const channelId = process.env.RELAY_CHANNEL_ID?.trim() ?? '';
const supabaseUrl = process.env.SUPABASE_URL?.trim() ?? '';
const serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY?.trim() ?? '';
const geminiApiKey = process.env.GEMINI_API_KEY?.trim() ?? '';

assertSafeChannelId(channelId);

if (!supabaseUrl || !serviceRoleKey) {
  console.error('error: SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required for wire publishing');
  process.exit(1);
}

if (!geminiApiKey) {
  console.error('error: GEMINI_API_KEY is required for wire publishing');
  process.exit(1);
}

const { frame, sourceLabel } = await loadFrame(frameSource);
console.log(`frameSource=${sourceLabel}`);
const provider = new GeminiVisionProvider({
  apiKey: geminiApiKey,
  model: process.env.GEMINI_MODEL
});

await publishOnce();

if (loopSeconds !== null) {
  setInterval(() => {
    void publishOnce();
  }, loopSeconds * 1000);
}

async function publishOnce(): Promise<void> {
  const detected = await provider.detect(frame);
  const names = Array.from(new Set(detected.cards.map((card) => normalizeCardName(card.name))));
  const resolved = await resolveCardImages(detected.cards.map((card) => card.name));
  const resolvedCount = names.filter((name) => resolved.get(name)?.imageUrl).length;
  await writeDebugFile(detected.cards, resolved);
  const regions = mapToDetectionRegions(detected.cards, resolved, { channelId });
  const didPublish = await publishRegions(regions, { supabaseUrl, serviceRoleKey, channelId });

  console.log([
    `detected=${detected.cards.length}`,
    `resolvedImages=${resolvedCount}`,
    `publishedRegions=${didPublish ? regions.length : 0}`,
    `channel=${channelId}`
  ].join(' '));

  if (!didPublish) {
    console.error('warning: Supabase relay publish failed');
  }
}

async function writeDebugFile(cards: Awaited<ReturnType<GeminiVisionProvider['detect']>>['cards'], resolved: Awaited<ReturnType<typeof resolveCardImages>>): Promise<void> {
  if (!debugPath) {
    return;
  }

  const debugCards = cards.map((card) => ({
    name: resolved.get(normalizeCardName(card.name))?.name ?? card.name,
    bbox: card.bbox
  }));
  const outputPath = resolve(debugPath);

  await writeFile(outputPath, renderDebugHtml(frame, debugCards), 'utf8');
  console.log(`debugHtml=${outputPath}`);
}

function assertSafeChannelId(value: string): void {
  if (!value) {
    console.error('error: RELAY_CHANNEL_ID is required and must be a dedicated dev channel');
    process.exit(1);
  }

  if (value === 'xerioc2') {
    console.error('error: refusing to publish to production channel RELAY_CHANNEL_ID=xerioc2');
    process.exit(1);
  }
}

function parseLoopSeconds(values: string[]): number | null {
  const loopIndex = values.indexOf('--loop');
  if (loopIndex < 0) {
    return null;
  }

  const seconds = Number(values[loopIndex + 1]);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    console.error('error: --loop requires a positive number of seconds');
    process.exit(1);
  }

  return seconds;
}

function parseDebugPath(values: string[]): string | null {
  const debugIndex = values.indexOf('--debug');
  if (debugIndex < 0) {
    return null;
  }

  const nextValue = values[debugIndex + 1];
  if (!nextValue || nextValue.startsWith('--')) {
    return './wire-debug.html';
  }

  return nextValue;
}

type FrameSource =
  | { kind: 'file'; path: string }
  | { kind: 'twitch'; value: string };

function parseFrameSource(values: string[]): FrameSource {
  const channel = valueAfterFlag(values, '--channel');
  const url = valueAfterFlag(values, '--url');
  const imagePaths = values.filter((arg, index) => (
    !arg.startsWith('--')
    && values[index - 1] !== '--loop'
    && values[index - 1] !== '--debug'
    && values[index - 1] !== '--channel'
    && values[index - 1] !== '--url'
  ));
  const sourceCount = imagePaths.length + (channel ? 1 : 0) + (url ? 1 : 0);

  if (sourceCount !== 1) {
    console.error('usage: npm run wire -- <imagePath> [--loop <seconds>] [--debug [path]] OR npm run wire -- --channel <name> [--debug] OR npm run wire -- --url <twitchUrl> [--debug]');
    console.error('error: provide exactly one frame source: image path, --channel, or --url');
    process.exit(1);
  }

  if (channel) {
    return { kind: 'twitch', value: channel };
  }

  if (url) {
    return { kind: 'twitch', value: url };
  }

  return { kind: 'file', path: imagePaths[0] };
}

async function loadFrame(source: FrameSource): Promise<{ frame: FrameInput; sourceLabel: string }> {
  if (source.kind === 'twitch') {
    console.log(`extractingTwitchFrame=${source.value}`);
    return {
      frame: await extractTwitchFrame(source.value),
      sourceLabel: `twitch:${source.value}`
    };
  }

  return {
    frame: {
      dataBase64: (await readFile(source.path)).toString('base64'),
      mimeType: mimeTypeForPath(source.path)
    },
    sourceLabel: `file:${source.path}`
  };
}

function valueAfterFlag(values: string[], flag: string): string | null {
  const flagIndex = values.indexOf(flag);
  if (flagIndex < 0) {
    return null;
  }

  const value = values[flagIndex + 1];
  if (!value || value.startsWith('--')) {
    console.error(`error: ${flag} requires a value`);
    process.exit(1);
  }

  return value;
}

function mimeTypeForPath(path: string): string {
  switch (extname(path).toLowerCase()) {
    case '.png':
      return 'image/png';
    case '.webp':
      return 'image/webp';
    case '.jpg':
    case '.jpeg':
    default:
      return 'image/jpeg';
  }
}
