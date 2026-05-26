import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  base: './',
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        index: resolve(projectRoot, 'index.html'),
        twitch: resolve(projectRoot, 'twitch.html'),
        overlay: resolve(projectRoot, 'overlay.html')
      }
    }
  },
  server: {
    port: 5173,
    strictPort: true,
    allowedHosts: [
      'localhost',
      '127.0.0.1'
    ],
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  },
  preview: {
    port: 4173,
    strictPort: true,
    allowedHosts: [
      'localhost',
      '127.0.0.1'
    ],
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
});
