import { readFile } from 'node:fs/promises';
import { extname } from 'node:path';
import { GeminiVisionProvider } from '../gemini.js';

const imagePath = process.argv[2];
const apiKey = process.env.GEMINI_API_KEY;

if (!imagePath) {
  console.error('usage: npm run scan -- <imagePath>');
  process.exitCode = 1;
} else if (!apiKey) {
  console.log('skipped: no GEMINI_API_KEY');
} else {
  const image = await readFile(imagePath);
  const provider = new GeminiVisionProvider({
    apiKey,
    model: process.env.GEMINI_MODEL
  });

  const result = await provider.detect({
    dataBase64: image.toString('base64'),
    mimeType: mimeTypeForPath(imagePath)
  });

  console.log(JSON.stringify(result, null, 2));
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
