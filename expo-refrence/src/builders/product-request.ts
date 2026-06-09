import { EVENT_KIND } from '@/types';
import type { UnsignedEvent } from '@/types';

function serializeProductRequest(productId: string, quantity: number): string {
  return JSON.stringify({
    content: JSON.stringify({
      product_id: productId,
      quantity,
    }),
    type: 'product_request',
  });
}

export function buildProductRequest(
  productId: string,
  quantity: number,
): UnsignedEvent {
  return {
    pubkey: '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.DIRECT_MESSAGE,
    tags: [],
    content: serializeProductRequest(productId, quantity),
  };
}
