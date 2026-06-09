import { EVENT_KIND, TOKEN_UTXO_TAG_PREFIX } from '@/types';
import type { UnsignedEvent } from '@/types';

function getProducerPubkey(assetRef: string): string {
  const [kind, producer] = assetRef.split(':', 3);

  if (kind !== TOKEN_UTXO_TAG_PREFIX || !producer) {
    throw new Error('Invalid asset reference');
  }

  return producer;
}

function serializeTokenUtxo(
  owner: string,
  amount: number,
  assetRef: string,
  prev?: string,
): string {
  return JSON.stringify({
    asset_ref: assetRef,
    owner,
    amount,
    prev_utxo_id: prev,
  });
}

export function buildTokenUTXO(
  owner: string,
  amount: number,
  assetRef: string,
  prev?: string,
): UnsignedEvent {
  const tags = [
    ['a', assetRef],
    ['p', owner],
  ];

  if (prev) {
    tags.push(['e', prev]);
  }

  return {
    pubkey: getProducerPubkey(assetRef),
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.TOKEN_UTXO,
    tags,
    content: serializeTokenUtxo(owner, amount, assetRef, prev),
  };
}
