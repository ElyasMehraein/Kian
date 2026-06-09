import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

function serializeTransferRequest(
  utxoId: string,
  amount: number,
  to: string,
): string {
  return JSON.stringify({
    utxo_id: utxoId,
    amount,
    to,
  });
}

export function buildTransferRequest(
  pubkey: string,
  utxoId: string,
  amount: number,
  to: string,
  producer: string,
): UnsignedEvent {
  return {
    pubkey,
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.TRANSFER_REQUEST,
    tags: [
      ['e', utxoId],
      ['p', to],
      ['p', producer],
    ],
    content: serializeTransferRequest(utxoId, amount, to),
  };
}
