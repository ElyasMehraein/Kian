import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

export function buildDeleteEvent(eventIds: string[], pubkey: string): UnsignedEvent {
  return {
    pubkey,
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.DELETE,
    tags: eventIds.map((eventId) => ['e', eventId]),
    content: '',
  };
}
