import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

function serializeReadReceipt(msgId: string): string {
  return JSON.stringify({
    message_id: msgId,
  });
}

export function buildReadReceipt(msgId: string): UnsignedEvent {
  return {
    pubkey: '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.RECEIPT_READ,
    tags: [['e', msgId]],
    content: serializeReadReceipt(msgId),
  };
}
