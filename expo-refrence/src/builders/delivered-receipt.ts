import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

function serializeDeliveredReceipt(msgId: string): string {
  return JSON.stringify({
    message_id: msgId,
  });
}

export function buildDeliveredReceipt(msgId: string): UnsignedEvent {
  return {
    pubkey: '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.RECEIPT_DELIVERED,
    tags: [['e', msgId]],
    content: serializeDeliveredReceipt(msgId),
  };
}
