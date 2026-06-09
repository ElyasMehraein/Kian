import { setInboxRelayUrls } from '@/services/dm-relays';
import type { NostrEvent } from '@/types';

function parseInboxRelayTags(tags: string[][]): string[] {
  return tags
    .filter(([name, value]) => name === 'relay' && typeof value === 'string' && value.length > 0)
    .map(([, value]) => value);
}

export async function handleDmRelayList(event: NostrEvent): Promise<void> {
  await setInboxRelayUrls(event.pubkey, parseInboxRelayTags(event.tags));
}
