import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

function serializeProductSend(utxos: string[], productName: string): string {
  return JSON.stringify({
    content: JSON.stringify({
      utxo_ids: utxos,
      product_name: productName,
    }),
    type: 'product_send',
  });
}

export function buildProductSend(
  utxos: string[],
  productName: string,
): UnsignedEvent {
  return {
    pubkey: '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.DIRECT_MESSAGE,
    tags: [],
    content: serializeProductSend(utxos, productName),
  };
}
