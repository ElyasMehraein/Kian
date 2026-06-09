import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

function serializeProductRequestDeclined(messageId: string): string {
  return JSON.stringify({
    message_id: messageId,
  });
}

export function buildProductRequestDeclined(messageId: string): UnsignedEvent {
  return {
    pubkey: '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.PRODUCT_REQUEST_DECLINED,
    tags: [['e', messageId]],
    content: serializeProductRequestDeclined(messageId),
  };
}
