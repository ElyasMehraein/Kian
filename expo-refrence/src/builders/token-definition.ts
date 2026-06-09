import { EVENT_KIND } from '@/types';
import type { TokenDefinition, UnsignedEvent } from '@/types';

function serializeTokenDefinition(def: TokenDefinition): string {
  return JSON.stringify({
    product_id: def.product_id,
    name: def.name,
    description: def.description,
    images: def.images,
    categories: def.categories,
    unit: def.unit,
  });
}

export function buildTokenDefinition(def: TokenDefinition): UnsignedEvent {
  return {
    pubkey: def.pubkey,
    created_at: def.created_at,
    kind: EVENT_KIND.TOKEN_MINT,
    tags: [['d', def.asset_id]],
    content: serializeTokenDefinition(def),
  };
}
