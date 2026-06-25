import assert from 'node:assert/strict';
import test from 'node:test';
import { buildFfmpegArgs, normalizeChannelUrl } from './twitch.js';

test('normalizeChannelUrl converts bare handles to Twitch URLs', () => {
  assert.equal(normalizeChannelUrl('beefygg'), 'https://twitch.tv/beefygg');
  assert.equal(normalizeChannelUrl('  xerioc2  '), 'https://twitch.tv/xerioc2');
});

test('normalizeChannelUrl passes through full http and https URLs', () => {
  assert.equal(normalizeChannelUrl('https://twitch.tv/beefygg'), 'https://twitch.tv/beefygg');
  assert.equal(normalizeChannelUrl('http://twitch.tv/beefygg'), 'http://twitch.tv/beefygg');
});

test('buildFfmpegArgs captures one mjpeg frame to stdout with scaling', () => {
  assert.deepEqual(buildFfmpegArgs('https://example.test/live.m3u8', 960), [
    '-i',
    'https://example.test/live.m3u8',
    '-frames:v',
    '1',
    '-f',
    'image2pipe',
    '-vcodec',
    'mjpeg',
    '-vf',
    "scale='min(960,iw)':-2",
    'pipe:1'
  ]);
});
