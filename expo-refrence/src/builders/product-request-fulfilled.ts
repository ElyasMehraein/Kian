import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

function serializeProductRequestFulfilled(messageId: string): string {
  return JSON.stringify({
    message_id: messageId,
  });
}

export function buildProductRequestFulfilled(messageId: string): UnsignedEvent {
  return {
    pubkey: '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.PRODUCT_REQUEST_FULFILLED,
    tags: [['e', messageId]],
    content: serializeProductRequestFulfilled(messageId),
  };
}
