import { bech32 } from '@scure/base';

import { bytesToHex, hexToBytes } from './encoding';

const BECH32_LIMIT = 5000;

function encodeBech32(prefix: string, hex: string): string {
  const words = bech32.toWords(hexToBytes(hex));

  return bech32.encode(prefix, words, BECH32_LIMIT);
}

function decodeBech32(value: string, prefix: string): string {
  const decoded = bech32.decode(value as `${string}1${string}`, BECH32_LIMIT);

  if (decoded.prefix !== prefix) {
    throw new Error(`Invalid prefix: expected ${prefix}`);
  }

  return bytesToHex(new Uint8Array(bech32.fromWords(decoded.words)));
}

export function pubkeyToNpub(hex: string): string {
  return encodeBech32('npub', hex);
}

export function privkeyToNsec(hex: string): string {
  return encodeBech32('nsec', hex);
}

export function npubToPubkey(npub: string): string {
  return decodeBech32(npub, 'npub');
}

export function nsecToPrivkey(nsec: string): string {
  return decodeBech32(nsec, 'nsec');
}

export function noteIdToNote(hex: string): string {
  return encodeBech32('note', hex);
}
