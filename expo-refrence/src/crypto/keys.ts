import { generateMnemonic, mnemonicToSeedSync, validateMnemonic } from '@scure/bip39';
import { wordlist } from '@scure/bip39/wordlists/english.js';
import * as secp from '@noble/secp256k1';

import type { KeyPair } from '@/types';
import { privkeyToNsec, pubkeyToNpub } from '@/utils';

import { bytesToHex, hexToBytes } from '../utils/encoding';

function keyPairFromMnemonic(mnemonic: string): KeyPair {
  if (!validateMnemonic(mnemonic, wordlist)) {
    throw new Error('Invalid mnemonic');
  }

  const seed = mnemonicToSeedSync(mnemonic);
  const privateKeyBytes = seed.slice(0, 32);

  if (!secp.utils.isValidSecretKey(privateKeyBytes)) {
    throw new Error('Derived private key is invalid');
  }

  const privkey = bytesToHex(privateKeyBytes);
  const pubkey = pubkeyFromPrivkey(privkey);

  return {
    pubkey,
    privkey,
    mnemonic,
    npub: pubkeyToNpub(pubkey),
    nsec: privkeyToNsec(privkey),
  };
}

export function generateKeyPair(): KeyPair {
  return keyPairFromMnemonic(generateMnemonic(wordlist, 128));
}

export function restoreFromMnemonic(mnemonic: string): KeyPair {
  return keyPairFromMnemonic(mnemonic);
}

export function pubkeyFromPrivkey(privkey: string): string {
  const publicKey = secp.getPublicKey(hexToBytes(privkey));

  return bytesToHex(publicKey.slice(1));
}
