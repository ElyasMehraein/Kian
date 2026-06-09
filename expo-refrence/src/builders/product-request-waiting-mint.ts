import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

function serializeProductRequestWaitingMint(messageId: string): string {
  return JSON.stringify({
    message_id: messageId,
  });
}

export function buildProductRequestWaitingMint(messageId: string): UnsignedEvent {
  return {
    pubkey: '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.PRODUCT_REQUEST_WAITING_MINT,
    tags: [['e', messageId]],
    content: serializeProductRequestWaitingMint(messageId),
  };
}
