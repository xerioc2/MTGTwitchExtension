import type { DetectionRegion } from './regions.js';

export type PublishRegionsOptions = {
  bridgeUrl?: string;
  supabaseUrl: string;
  serviceRoleKey: string;
  channelId: string;
  fetchImpl?: typeof fetch;
};

export async function publishRegions(regions: DetectionRegion[], opts: PublishRegionsOptions): Promise<boolean> {
  const fetchImpl = opts.fetchImpl ?? fetch;
  const bridgeUrl = opts.bridgeUrl?.trim();
  if (bridgeUrl) {
    try {
      const response = await fetchImpl(`${bridgeUrl.replace(/\/+$/, '')}/api/detection-regions`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          channelId: opts.channelId,
          regions
        })
      });

      return response.ok;
    } catch {
      return false;
    }
  }

  try {
    const response = await fetchImpl(`${opts.supabaseUrl.replace(/\/+$/, '')}/realtime/v1/api/broadcast`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: opts.serviceRoleKey,
        Authorization: `Bearer ${opts.serviceRoleKey}`
      },
      body: JSON.stringify({
        messages: [{
          topic: `game-state:${opts.channelId}`,
          event: 'detection-regions',
          payload: {
            detectionRegions: regions
          }
        }]
      })
    });

    return response.ok;
  } catch {
    return false;
  }
}
