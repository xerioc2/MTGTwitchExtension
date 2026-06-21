import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const viteBin = resolve(scriptDir, '..', 'node_modules', 'vite', 'bin', 'vite.js');

const env = {
  ...process.env,
  VITE_ENABLE_SCREEN_DETECTIONS: 'true'
};

const result = spawnSync(
  process.execPath,
  [viteBin, 'build', '--outDir', 'dist-v2-screen-hover', '--emptyOutDir'],
  {
    env,
    stdio: 'inherit',
    shell: false
  }
);

if (result.error) {
  console.error(result.error.message);
}

process.exit(result.status ?? 1);
