import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

function serializeTokenTransferWaitingIssuer(messageId: string): string {
  return JSON.stringify({
    message_id: messageId,
  });
}

export function buildTokenTransferWaitingIssuer(messageId: string): UnsignedEvent {
  return {
    pubkey: '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.TOKEN_TRANSFER_WAITING_ISSUER,
    tags: [['e', messageId]],
    content: serializeTokenTransferWaitingIssuer(messageId),
  };
}
