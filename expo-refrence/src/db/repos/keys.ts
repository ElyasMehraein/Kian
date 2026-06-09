import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import { pubkeyFromPrivkey } from '@/crypto';
import type { KeyPair } from '@/types';
import { privkeyToNsec, pubkeyToNpub } from '@/utils';

import { getDatabase } from '../init';

const PRIVATE_KEY_STORAGE_KEY = 'keys_privkey';
const MNEMONIC_STORAGE_KEY = 'keys_mnemonic';

async function setSecret(key: string, value: string): Promise<void> {
  if (Platform.OS === 'web') {
    localStorage.setItem(key, value);
    return;
  }

  await SecureStore.setItemAsync(key, value);
}

async function getSecret(key: string): Promise<string | null> {
  if (Platform.OS === 'web') {
    return localStorage.getItem(key);
  }

  return SecureStore.getItemAsync(key);
}

async function deleteSecret(key: string): Promise<void> {
  if (Platform.OS === 'web') {
    localStorage.removeItem(key);
    return;
  }

  await SecureStore.deleteItemAsync(key);
}

export const keysRepo = {
  async saveKeyPair(kp: KeyPair): Promise<void> {
    const db = getDatabase();

    await db.transaction(async (tx) => {
      await tx.execute('DELETE FROM keys');
      await tx.execute(
        `
          INSERT INTO keys (pubkey, npub, created_at)
          VALUES (?, ?, ?)
        `,
        [kp.pubkey, kp.npub, Math.floor(Date.now() / 1000)],
      );
    });

    await Promise.all([
      setSecret(PRIVATE_KEY_STORAGE_KEY, kp.privkey),
      setSecret(MNEMONIC_STORAGE_KEY, kp.mnemonic),
    ]);
  },

  async getPublicKey(): Promise<string | null> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT pubkey
        FROM keys
        LIMIT 1
      `,
    );

    return (result.rows[0]?.pubkey as string | undefined) ?? null;
  },

  async getPrivateKey(): Promise<string | null> {
    return getSecret(PRIVATE_KEY_STORAGE_KEY);
  },

  async getMnemonic(): Promise<string | null> {
    return getSecret(MNEMONIC_STORAGE_KEY);
  },

  async hasKeys(): Promise<boolean> {
    const [pubkey, privkey] = await Promise.all([
      this.getPublicKey(),
      this.getPrivateKey(),
    ]);

    return Boolean(pubkey && privkey);
  },

  async getSavedKeyPair(): Promise<KeyPair | null> {
    const [privkey, mnemonic] = await Promise.all([
      this.getPrivateKey(),
      this.getMnemonic(),
    ]);

    if (!privkey) {
      return null;
    }

    const pubkey = pubkeyFromPrivkey(privkey);

    return {
      pubkey,
      privkey,
      mnemonic: mnemonic ?? '',
      npub: pubkeyToNpub(pubkey),
      nsec: privkeyToNsec(privkey),
    };
  },

  async clearSession(): Promise<void> {
    const db = getDatabase();
    await db.execute('DELETE FROM keys');
  },

  async deleteAll(): Promise<void> {
    const db = getDatabase();

    await db.execute('DELETE FROM keys');
    await Promise.all([
      deleteSecret(PRIVATE_KEY_STORAGE_KEY),
      deleteSecret(MNEMONIC_STORAGE_KEY),
    ]);
  },
};
