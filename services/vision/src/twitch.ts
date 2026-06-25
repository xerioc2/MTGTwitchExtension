import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import type { FrameInput } from './types.js';

const execFileAsync = promisify(execFile);
const DEFAULT_MAX_WIDTH = 1280;

export async function extractTwitchFrame(channelOrUrl: string, opts: { maxWidth?: number } = {}): Promise<FrameInput> {
  const url = normalizeChannelUrl(channelOrUrl);
  const manifestUrl = await getManifestUrl(url);
  const jpeg = await getFrameJpeg(manifestUrl, opts.maxWidth ?? DEFAULT_MAX_WIDTH);

  return {
    dataBase64: jpeg.toString('base64'),
    mimeType: 'image/jpeg'
  };
}

export function normalizeChannelUrl(input: string): string {
  const trimmed = input.trim();

  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed;
  }

  return `https://twitch.tv/${trimmed}`;
}

export function buildFfmpegArgs(manifestUrl: string, maxWidth = DEFAULT_MAX_WIDTH): string[] {
  return [
    '-i',
    manifestUrl,
    '-frames:v',
    '1',
    '-f',
    'image2pipe',
    '-vcodec',
    'mjpeg',
    '-vf',
    `scale='min(${maxWidth},iw)':-2`,
    'pipe:1'
  ];
}

async function getManifestUrl(url: string): Promise<string> {
  try {
    const { stdout } = await execFileAsync('yt-dlp', ['-f', 'best', '-g', url], {
      encoding: 'utf8',
      maxBuffer: 1024 * 1024
    });
    const manifestUrl = stdout.split(/\r?\n/).map((line) => line.trim()).find(Boolean);

    if (!manifestUrl) {
      throw new Error('channel not live or unreachable');
    }

    return manifestUrl;
  } catch (error) {
    if (isMissingBinaryError(error)) {
      throw new Error('yt-dlp is required and must be installed on PATH');
    }

    throw new Error('channel not live or unreachable');
  }
}

async function getFrameJpeg(manifestUrl: string, maxWidth: number): Promise<Buffer> {
  try {
    const { stdout } = await execFileAsync('ffmpeg', buildFfmpegArgs(manifestUrl, maxWidth), {
      encoding: 'buffer',
      maxBuffer: 16 * 1024 * 1024
    });

    if (!Buffer.isBuffer(stdout) || stdout.length === 0) {
      throw new Error('ffmpeg did not return a frame');
    }

    return stdout;
  } catch (error) {
    if (isMissingBinaryError(error)) {
      throw new Error('ffmpeg is required and must be installed on PATH');
    }

    throw new Error(error instanceof Error ? error.message : 'ffmpeg failed to extract a frame');
  }
}

function isMissingBinaryError(error: unknown): boolean {
  return typeof error === 'object'
    && error !== null
    && 'code' in error
    && (error as { code?: unknown }).code === 'ENOENT';
}
