import { deflateRawSync } from 'node:zlib';
import { mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const ZIP_NAME = 'magiccontent-v2-screen-hover-0.0.3.zip';
const scriptDir = dirname(fileURLToPath(import.meta.url));
const frontendDir = resolve(scriptDir, '..');
const distDir = resolve(frontendDir, 'dist-v2-screen-hover');
const packageDir = resolve(frontendDir, 'twitch-packages');
const zipPath = resolve(packageDir, ZIP_NAME);

const forbiddenEnvPattern = /(^|\/)\.env($|[./-])/i;
const forbiddenBasePattern = /^(secret|token|apikey|credential|private)[^/]*$/i;
const requiredFiles = ['index.html', 'overlay.html', 'twitch.html', 'config.html'];

function fail(message) {
  console.error(`V2 package failed: ${message}`);
  process.exit(1);
}

function assertPathInside(parent, child) {
  const relativePath = relative(parent, child);
  if (relativePath.startsWith('..') || relativePath === '' || relativePath.includes(`..${sep}`)) {
    fail(`unexpected path outside package root: ${child}`);
  }
}

function walkFiles(root, current = root) {
  return readdirSync(current).flatMap((entry) => {
    const absolutePath = join(current, entry);
    const stats = statSync(absolutePath);
    if (stats.isDirectory()) {
      return walkFiles(root, absolutePath);
    }
    if (!stats.isFile()) {
      return [];
    }
    assertPathInside(root, absolutePath);
    return [{
      absolutePath,
      zipPath: relative(root, absolutePath).split(sep).join('/'),
      stats
    }];
  });
}

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function dosDateTime(date) {
  const year = Math.max(date.getFullYear(), 1980);
  const dosTime = (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2);
  const dosDate = ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
  return { dosDate, dosTime };
}

function u16(value) {
  const buffer = Buffer.alloc(2);
  buffer.writeUInt16LE(value);
  return buffer;
}

function u32(value) {
  const buffer = Buffer.alloc(4);
  buffer.writeUInt32LE(value >>> 0);
  return buffer;
}

function createZip(files, destination) {
  const localParts = [];
  const centralParts = [];
  let offset = 0;

  for (const file of files) {
    const name = Buffer.from(file.zipPath, 'utf8');
    const data = readFileSync(file.absolutePath);
    const compressed = deflateRawSync(data, { level: 9 });
    const checksum = crc32(data);
    const { dosDate, dosTime } = dosDateTime(file.stats.mtime);

    const localHeader = Buffer.concat([
      u32(0x04034b50),
      u16(20),
      u16(0x0800),
      u16(8),
      u16(dosTime),
      u16(dosDate),
      u32(checksum),
      u32(compressed.length),
      u32(data.length),
      u16(name.length),
      u16(0),
      name
    ]);

    localParts.push(localHeader, compressed);

    const centralHeader = Buffer.concat([
      u32(0x02014b50),
      u16(20),
      u16(20),
      u16(0x0800),
      u16(8),
      u16(dosTime),
      u16(dosDate),
      u32(checksum),
      u32(compressed.length),
      u32(data.length),
      u16(name.length),
      u16(0),
      u16(0),
      u16(0),
      u16(0),
      u32(0),
      u32(offset),
      name
    ]);

    centralParts.push(centralHeader);
    offset += localHeader.length + compressed.length;
  }

  const centralDirectory = Buffer.concat(centralParts);
  const endRecord = Buffer.concat([
    u32(0x06054b50),
    u16(0),
    u16(0),
    u16(files.length),
    u16(files.length),
    u32(centralDirectory.length),
    u32(offset),
    u16(0)
  ]);

  writeFileSync(destination, Buffer.concat([...localParts, centralDirectory, endRecord]));
}

const buildResult = spawnSync(process.execPath, [join(scriptDir, 'build-v2-screen-hover.mjs')], {
  cwd: frontendDir,
  stdio: 'inherit',
  shell: false
});

if (buildResult.error) {
  fail(buildResult.error.message);
}

if (buildResult.status !== 0) {
  fail(`V2 build exited with status ${buildResult.status}`);
}

let distStats;
try {
  distStats = statSync(distDir);
} catch {
  fail('frontend/dist-v2-screen-hover does not exist');
}

if (!distStats.isDirectory()) {
  fail('frontend/dist-v2-screen-hover is not a directory');
}

for (const requiredFile of requiredFiles) {
  try {
    if (!statSync(join(distDir, requiredFile)).isFile()) {
      fail(`missing required built file: ${requiredFile}`);
    }
  } catch {
    fail(`missing required built file: ${requiredFile}`);
  }
}

const files = walkFiles(distDir);

if (files.length === 0) {
  fail('V2 build output is empty');
}

for (const file of files) {
  const baseName = file.zipPath.split('/').pop() ?? '';
  if (forbiddenEnvPattern.test(file.zipPath) || forbiddenBasePattern.test(baseName)) {
    fail(`refusing to package secret-looking file: ${file.zipPath}`);
  }
}

const cssFiles = files.filter((file) => file.zipPath.endsWith('.css'));
if (cssFiles.length === 0) {
  fail('V2 build has no CSS bundle - something went wrong');
}

const css = cssFiles.map((file) => readFileSync(file.absolutePath, 'utf8')).join('\n');
if (!css.includes('extension-detection-layer')) {
  fail('V2 build CSS does not contain detection layer styles - VITE_ENABLE_SCREEN_DETECTIONS may not be active');
}

mkdirSync(packageDir, { recursive: true });
createZip(files, zipPath);

console.log(`Packaged ${files.length} files at ${relative(frontendDir, zipPath).split(sep).join('/')}`);
