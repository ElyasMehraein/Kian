import type { NostrEvent, UnsignedEvent } from '@/types';
import { EVENT_KIND } from '@/types';
import { computeEventId } from '@/utils';

import { generateKeyPair } from './keys';
import { nip44Decrypt, nip44Encrypt } from './nip44';
import { signEvent, verifySignature } from './sign';

const RANDOMIZATION_WINDOW_SECONDS = 48 * 60 * 60;

function randomizedTimestamp(): number {
  const now = Math.floor(Date.now() / 1000);
  const offset = Math.floor(Math.random() * (RANDOMIZATION_WINDOW_SECONDS * 2 + 1));

  return now - RANDOMIZATION_WINDOW_SECONDS + offset;
}

export interface GiftWrapResult {
  wrap: NostrEvent;
  ephemeralPrivkey: string;
}

export interface UnwrapResult {
  event: UnsignedEvent;
  eventId: string;
  senderPubkey: string;
}

function isNostrEvent(value: unknown): value is NostrEvent {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const event = value as Record<string, unknown>;

  return (
    typeof event.id === 'string' &&
    typeof event.pubkey === 'string' &&
    typeof event.created_at === 'number' &&
    typeof event.kind === 'number' &&
    Array.isArray(event.tags) &&
    typeof event.content === 'string' &&
    typeof event.sig === 'string'
  );
}

function isUnsignedEvent(value: unknown): value is UnsignedEvent {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const event = value as Record<string, unknown>;

  return (
    typeof event.pubkey === 'string' &&
    typeof event.created_at === 'number' &&
    typeof event.kind === 'number' &&
    Array.isArray(event.tags) &&
    typeof event.content === 'string'
  );
}

export function giftWrap(
  innerEvent: UnsignedEvent,
  senderPrivkey: string,
  recipientPubkey: string,
): GiftWrapResult {
  const ephemeral = generateKeyPair();
  const seal = signEvent(
    {
      pubkey: innerEvent.pubkey,
      created_at: randomizedTimestamp(),
      kind: EVENT_KIND.GIFT_SEAL,
      tags: [],
      content: nip44Encrypt(JSON.stringify(innerEvent), senderPrivkey, recipientPubkey),
    },
    senderPrivkey,
  );
  const wrap = signEvent(
    {
      pubkey: ephemeral.pubkey,
      created_at: randomizedTimestamp(),
      kind: EVENT_KIND.GIFT_WRAP,
      tags: [['p', recipientPubkey]],
      content: nip44Encrypt(JSON.stringify(seal), ephemeral.privkey, recipientPubkey),
    },
    ephemeral.privkey,
  );

  return { wrap, ephemeralPrivkey: ephemeral.privkey };
}

export function giftUnwrap(
  wrap: NostrEvent,
  recipientPrivkey: string,
): UnwrapResult | null {
  try {
    if (wrap.kind !== EVENT_KIND.GIFT_WRAP || !verifySignature(wrap)) {
      return null;
    }

    const sealJson = nip44Decrypt(wrap.content, recipientPrivkey, wrap.pubkey);
    const seal = JSON.parse(sealJson);

    if (!isNostrEvent(seal) || seal.kind !== EVENT_KIND.GIFT_SEAL || !verifySignature(seal)) {
      return null;
    }

    const innerJson = nip44Decrypt(seal.content, recipientPrivkey, seal.pubkey);
    const event = JSON.parse(innerJson);

    if (
      !isUnsignedEvent(event) ||
      seal.pubkey !== event.pubkey
    ) {
      return null;
    }

    return {
      event,
      eventId: computeEventId(event),
      senderPubkey: seal.pubkey,
    };
  } catch {
    return null;
  }
}
