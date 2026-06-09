import { sha256 } from '@noble/hashes/sha2.js';
import { Point } from '@noble/secp256k1';

import type { NostrEvent, UnsignedEvent } from '@/types';

import { bytesToHex, hexToBytes, utf8ToBytes } from './encoding';

const FIELD_SIZE = Point.CURVE().p;
const GROUP_ORDER = Point.CURVE().n;

function bytesToNumber(bytes: Uint8Array): bigint {
  let value = 0n;

  for (const byte of bytes) {
    value = (value << 8n) | BigInt(byte);
  }

  return value;
}

function taggedHash(tag: string, ...messages: Uint8Array[]): Uint8Array {
  const tagHash = sha256(utf8ToBytes(tag));

  return sha256(new Uint8Array([...tagHash, ...tagHash, ...messages.flatMap((message) => [...message])]));
}

function verifySchnorr(signature: string, message: string, pubkey: string): boolean {
  try {
    const signatureBytes = hexToBytes(signature);
    const messageBytes = hexToBytes(message);
    const pubkeyBytes = hexToBytes(pubkey);

    if (signatureBytes.length !== 64 || messageBytes.length !== 32 || pubkeyBytes.length !== 32) {
      return false;
    }

    const r = bytesToNumber(signatureBytes.slice(0, 32));
    const s = bytesToNumber(signatureBytes.slice(32));

    if (r >= FIELD_SIZE || s >= GROUP_ORDER) {
      return false;
    }

    const challenge = bytesToNumber(
      taggedHash('BIP0340/challenge', signatureBytes.slice(0, 32), pubkeyBytes, messageBytes),
    ) % GROUP_ORDER;
    const publicPoint = Point.fromHex(`02${pubkey}`);
    const resultPoint = Point.BASE.multiply(s).add(publicPoint.multiply((GROUP_ORDER - challenge) % GROUP_ORDER));

    if (resultPoint.equals(Point.ZERO)) {
      return false;
    }

    const affine = resultPoint.toAffine();

    return affine.y % 2n === 0n && affine.x === r;
  } catch {
    return false;
  }
}

export function serializeEvent(event: UnsignedEvent): string {
  return JSON.stringify([0, event.pubkey, event.created_at, event.kind, event.tags, event.content]);
}

export function computeEventId(event: UnsignedEvent): string {
  return bytesToHex(sha256(utf8ToBytes(serializeEvent(event))));
}

export function validateEventId(event: NostrEvent): boolean {
  return computeEventId(event) === event.id;
}

export function validateEventSig(event: NostrEvent): boolean {
  if (!validateEventId(event)) {
    return false;
  }

  return verifySchnorr(event.sig, event.id, event.pubkey);
}
