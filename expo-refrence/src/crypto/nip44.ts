import { chacha20 } from '@noble/ciphers/chacha.js';
import { expand, extract } from '@noble/hashes/hkdf.js';
import { hmac } from '@noble/hashes/hmac.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { Point, utils } from '@noble/secp256k1';
import { base64 } from '@scure/base';

import { bytesToUtf8, concatBytes, hexToBytes, utf8ToBytes } from '../utils/encoding';

const VERSION = 2;
const MIN_PLAINTEXT_SIZE = 1;
const MAX_PLAINTEXT_SIZE = 65535;
const CONVERSATION_SALT = utf8ToBytes('nip44-v2');

function bytesToNumber(bytes: Uint8Array): bigint {
  let value = 0n;

  for (const byte of bytes) {
    value = (value << 8n) | BigInt(byte);
  }

  return value;
}

function numberToBytes(value: number): Uint8Array {
  return Uint8Array.of((value >> 8) & 0xff, value & 0xff);
}

function equalBytes(left: Uint8Array, right: Uint8Array): boolean {
  if (left.length !== right.length) {
    return false;
  }

  let result = 0;

  for (let index = 0; index < left.length; index += 1) {
    result |= left[index] ^ right[index];
  }

  return result === 0;
}

function calcPaddedLength(unpaddedLength: number): number {
  if (unpaddedLength <= 32) {
    return 32;
  }

  const nextPower = 1 << (Math.floor(Math.log2(unpaddedLength - 1)) + 1);
  const chunk = nextPower <= 256 ? 32 : nextPower / 8;

  return chunk * (Math.floor((unpaddedLength - 1) / chunk) + 1);
}

function pad(plaintext: string): Uint8Array {
  const unpadded = utf8ToBytes(plaintext);
  const unpaddedLength = unpadded.length;

  if (unpaddedLength < MIN_PLAINTEXT_SIZE || unpaddedLength > MAX_PLAINTEXT_SIZE) {
    throw new Error('Invalid plaintext length');
  }

  return concatBytes(numberToBytes(unpaddedLength), unpadded, new Uint8Array(calcPaddedLength(unpaddedLength) - unpaddedLength));
}

function unpad(padded: Uint8Array): string {
  if (padded.length < 34) {
    throw new Error('Invalid padded payload');
  }

  const unpaddedLength = (padded[0] << 8) | padded[1];
  const plaintext = padded.slice(2, 2 + unpaddedLength);

  if (
    unpaddedLength < MIN_PLAINTEXT_SIZE ||
    plaintext.length !== unpaddedLength ||
    padded.length !== 2 + calcPaddedLength(unpaddedLength)
  ) {
    throw new Error('Invalid padding');
  }

  return bytesToUtf8(plaintext);
}

function getConversationKey(privkey: string, pubkey: string): Uint8Array {
  return extract(sha256, computeSharedSecret(privkey, pubkey), CONVERSATION_SALT);
}

function getMessageKeys(conversationKey: Uint8Array, nonce: Uint8Array) {
  if (conversationKey.length !== 32 || nonce.length !== 32) {
    throw new Error('Invalid key material');
  }

  const keys = expand(sha256, conversationKey, nonce, 76);

  return {
    chachaKey: keys.slice(0, 32),
    chachaNonce: keys.slice(32, 44),
    hmacKey: keys.slice(44, 76),
  };
}

function decodePayload(payload: string) {
  if (payload.length === 0 || payload.startsWith('#')) {
    throw new Error('Unsupported payload version');
  }

  if (payload.length < 132 || payload.length > 87472) {
    throw new Error('Invalid payload size');
  }

  const data = base64.decode(payload);

  if (data.length < 99 || data.length > 65603) {
    throw new Error('Invalid decoded payload size');
  }

  if (data[0] !== VERSION) {
    throw new Error('Unsupported payload version');
  }

  return {
    nonce: data.slice(1, 33),
    ciphertext: data.slice(33, data.length - 32),
    mac: data.slice(data.length - 32),
  };
}

export function computeSharedSecret(privkey: string, pubkey: string): Uint8Array {
  const privateKeyBytes = hexToBytes(privkey);

  if (!utils.isValidSecretKey(privateKeyBytes)) {
    throw new Error('Invalid private key');
  }

  const point = Point.fromHex(`02${pubkey}`);

  return point.multiply(bytesToNumber(privateKeyBytes)).toBytes(true).slice(1);
}

export function nip44Encrypt(
  plaintext: string,
  senderPrivkey: string,
  recipientPubkey: string,
): string {
  const conversationKey = getConversationKey(senderPrivkey, recipientPubkey);
  const nonce = utils.randomSecretKey();
  const { chachaKey, chachaNonce, hmacKey } = getMessageKeys(conversationKey, nonce);
  const ciphertext = chacha20(chachaKey, chachaNonce, pad(plaintext));
  const mac = hmac(sha256, hmacKey, concatBytes(nonce, ciphertext));

  return base64.encode(concatBytes(Uint8Array.of(VERSION), nonce, ciphertext, mac));
}

export function nip44Decrypt(
  ciphertext: string,
  recipientPrivkey: string,
  senderPubkey: string,
): string {
  const { nonce, ciphertext: encrypted, mac } = decodePayload(ciphertext);
  const conversationKey = getConversationKey(recipientPrivkey, senderPubkey);
  const { chachaKey, chachaNonce, hmacKey } = getMessageKeys(conversationKey, nonce);
  const calculatedMac = hmac(sha256, hmacKey, concatBytes(nonce, encrypted));

  if (!equalBytes(calculatedMac, mac)) {
    throw new Error('Invalid MAC');
  }

  return unpad(chacha20(chachaKey, chachaNonce, encrypted));
}
