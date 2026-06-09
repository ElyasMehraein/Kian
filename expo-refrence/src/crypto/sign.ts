import { sha256 } from '@noble/hashes/sha2.js';
import { Point, utils } from '@noble/secp256k1';

import type { NostrEvent, UnsignedEvent } from '@/types';
import { computeEventId, hexToBytes, validateEventSig } from '@/utils';

import { pubkeyFromPrivkey } from './keys';
import { bytesToHex, utf8ToBytes } from '../utils/encoding';

const GROUP_ORDER = Point.CURVE().n;

function bytesToNumber(bytes: Uint8Array): bigint {
  let value = 0n;

  for (const byte of bytes) {
    value = (value << 8n) | BigInt(byte);
  }

  return value;
}

function numberToBytes(value: bigint): Uint8Array {
  const bytes = new Uint8Array(32);
  let remaining = value;

  for (let index = 31; index >= 0; index -= 1) {
    bytes[index] = Number(remaining & 0xffn);
    remaining >>= 8n;
  }

  return bytes;
}

function xorBytes(left: Uint8Array, right: Uint8Array): Uint8Array {
  const result = new Uint8Array(left.length);

  for (let index = 0; index < left.length; index += 1) {
    result[index] = left[index] ^ right[index];
  }

  return result;
}

function taggedHash(tag: string, ...messages: Uint8Array[]): Uint8Array {
  const tagHash = sha256(utf8ToBytes(tag));
  const totalLength = messages.reduce((sum, message) => sum + message.length, tagHash.length * 2);
  const input = new Uint8Array(totalLength);

  input.set(tagHash, 0);
  input.set(tagHash, tagHash.length);

  let offset = tagHash.length * 2;

  for (const message of messages) {
    input.set(message, offset);
    offset += message.length;
  }

  return sha256(input);
}

function schnorrSign(messageHex: string, privkey: string): string {
  const message = hexToBytes(messageHex);

  if (message.length !== 32) {
    throw new Error('Message must be a 32-byte hex string');
  }

  const privateKeyBytes = hexToBytes(privkey);

  if (!utils.isValidSecretKey(privateKeyBytes)) {
    throw new Error('Invalid private key');
  }

  const secretScalar = bytesToNumber(privateKeyBytes);
  const publicPoint = Point.BASE.multiply(secretScalar);
  const adjustedSecret = publicPoint.toAffine().y % 2n === 0n ? secretScalar : GROUP_ORDER - secretScalar;
  const publicKeyBytes = numberToBytes(publicPoint.toAffine().x);
  const auxRand = utils.randomSecretKey();
  const nonceSeed = xorBytes(numberToBytes(adjustedSecret), taggedHash('BIP0340/aux', auxRand));
  const nonceScalar =
    bytesToNumber(taggedHash('BIP0340/nonce', nonceSeed, publicKeyBytes, message)) % GROUP_ORDER;

  if (nonceScalar === 0n) {
    throw new Error('Failed to derive nonce');
  }

  const noncePoint = Point.BASE.multiply(nonceScalar);
  const adjustedNonce = noncePoint.toAffine().y % 2n === 0n ? nonceScalar : GROUP_ORDER - nonceScalar;
  const nonceBytes = numberToBytes(noncePoint.toAffine().x);
  const challenge =
    bytesToNumber(taggedHash('BIP0340/challenge', nonceBytes, publicKeyBytes, message)) % GROUP_ORDER;
  const signature = bytesToHex(
    new Uint8Array([
      ...nonceBytes,
      ...numberToBytes((adjustedNonce + challenge * adjustedSecret) % GROUP_ORDER),
    ]),
  );

  return signature;
}

export function signEvent(unsigned: UnsignedEvent, privkey: string): NostrEvent {
  const derivedPubkey = pubkeyFromPrivkey(privkey);

  if (derivedPubkey !== unsigned.pubkey) {
    throw new Error('Event pubkey does not match private key');
  }

  const id = computeEventId(unsigned);
  const event: NostrEvent = {
    ...unsigned,
    id,
    sig: schnorrSign(id, privkey),
  };

  if (!validateEventSig(event)) {
    throw new Error('Generated signature is invalid');
  }

  return event;
}

export function verifySignature(event: NostrEvent): boolean {
  return validateEventSig(event);
}
