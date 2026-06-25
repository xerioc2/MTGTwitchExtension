import type { DetectionRegion } from './regions.js';

export type PublishRegionsOptions = {
  supabaseUrl: string;
  serviceRoleKey: string;
  channelId: string;
};

export async function publishRegions(regions: DetectionRegion[], opts: PublishRegionsOptions): Promise<boolean> {
  try {
    const response = await fetch(`${opts.supabaseUrl.replace(/\/+$/, '')}/realtime/v1/api/broadcast`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: opts.serviceRoleKey,
        Authorization: `Bearer ${opts.serviceRoleKey}`
      },
      body: JSON.stringify({
        messages: [{
          topic: `game-state:${opts.channelId}`,
          event: 'game-state',
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
