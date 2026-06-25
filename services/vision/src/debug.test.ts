import assert from 'node:assert/strict';
import test from 'node:test';
import { renderDebugHtml } from './debug.js';

test('renderDebugHtml includes frame data URI and positioned labeled boxes', () => {
  const html = renderDebugHtml(
    { dataBase64: 'abc123', mimeType: 'image/jpeg' },
    [
      { name: 'Lightning Bolt', bbox: { x: 0.1, y: 0.2, w: 0.3, h: 0.4 } },
      { name: 'Brazen Borrower <Petty Theft>', bbox: { x: 0.5, y: 0.6, w: 0.2, h: 0.1 } }
    ]
  );

  assert.match(html, /<!doctype html>/);
  assert.match(html, /src="data:image\/jpeg;base64,abc123"/);
  assert.match(html, /style="left:10%;top:20%;width:30%;height:40%;"/);
  assert.match(html, /style="left:50%;top:60%;width:20%;height:10%;"/);
  assert.match(html, />Lightning Bolt</);
  assert.match(html, />Brazen Borrower &lt;Petty Theft&gt;</);
});

test('renderDebugHtml returns valid standalone html with no boxes for empty cards', () => {
  const html = renderDebugHtml({ dataBase64: 'empty', mimeType: 'image/png' }, []);

  assert.match(html, /<html lang="en">/);
  assert.match(html, /src="data:image\/png;base64,empty"/);
  assert.doesNotMatch(html, /class="box"/);
});
