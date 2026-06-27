import type { Bbox, FrameInput } from './types.js';

export type DebugCard = {
  name: string;
  bbox: Bbox;
  resolved?: boolean;
};

export function renderDebugHtml(frame: FrameInput, cards: DebugCard[]): string {
  const boxes = cards.map((card, index) => renderBox(card, index)).join('\n');

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>MTG Twitch Vision Debug</title>
  <style>
    body {
      margin: 0;
      padding: 16px;
      background: #111;
      color: #f4efe7;
      font-family: Arial, sans-serif;
    }
    .frame {
      position: relative;
      width: min(100%, 1280px);
      margin: 0 auto;
      border: 1px solid #444;
      background: #000;
    }
    .frame img {
      display: block;
      width: 100%;
    }
    .box {
      position: absolute;
      border: 2px solid #ffdf5d;
      box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.72);
      pointer-events: none;
    }
    .box.unresolved {
      border-color: #ff5d5d;
      border-style: dashed;
    }
    .label {
      position: absolute;
      left: 0;
      top: 0;
      max-width: 100%;
      padding: 2px 5px;
      background: rgba(0, 0, 0, 0.82);
      color: #ffef9a;
      font-size: 12px;
      font-weight: 700;
      line-height: 1.25;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .legend {
      width: min(100%, 1280px);
      margin: 0 auto 10px;
      display: flex;
      gap: 12px;
      font-size: 13px;
    }
    .legend span {
      display: inline-flex;
      align-items: center;
      gap: 5px;
    }
    .legend i {
      width: 16px;
      height: 10px;
      border: 2px solid #ffdf5d;
      display: inline-block;
    }
    .legend .unresolved-key i {
      border-color: #ff5d5d;
      border-style: dashed;
    }
  </style>
</head>
<body>
  <div class="legend">
    <span class="resolved-key"><i></i>Resolved Scryfall card</span>
    <span class="unresolved-key"><i></i>Unresolved / not published</span>
  </div>
  <div class="frame">
    <img src="data:${escapeAttribute(frame.mimeType)};base64,${escapeAttribute(frame.dataBase64)}" alt="Analyzed frame">
${boxes}
  </div>
</body>
</html>
`;
}

function renderBox(card: DebugCard, index: number): string {
  const bbox = card.bbox;
  const left = percent(bbox.x);
  const top = percent(bbox.y);
  const width = percent(bbox.w);
  const height = percent(bbox.h);
  const className = card.resolved === false ? 'box unresolved' : 'box resolved';

  return `    <div class="${className}" data-index="${index}" style="left:${left};top:${top};width:${width};height:${height};">
      <div class="label">${escapeHtml(card.name)}</div>
    </div>`;
}

function percent(value: number): string {
  return `${clamp01(value) * 100}%`;
}

function clamp01(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }

  return Math.max(0, Math.min(1, value));
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function escapeAttribute(value: string): string {
  return escapeHtml(value);
}
