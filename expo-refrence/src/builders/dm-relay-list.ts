import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

export function buildDmRelayList(pubkey: string, relayUrls: string[]): UnsignedEvent {
  return {
    pubkey,
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.DM_RELAY_LIST,
    tags: relayUrls.map((relayUrl) => ['relay', relayUrl]),
    content: '',
  };
}
