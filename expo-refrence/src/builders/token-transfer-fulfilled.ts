import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

function serializeTokenTransferFulfilled(messageId: string): string {
  return JSON.stringify({
    message_id: messageId,
  });
}

export function buildTokenTransferFulfilled(messageId: string): UnsignedEvent {
  return {
    pubkey: '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.TOKEN_TRANSFER_FULFILLED,
    tags: [['e', messageId]],
    content: serializeTokenTransferFulfilled(messageId),
  };
}
