import { useEffect, useState } from 'react';
import { Alert, Pressable, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';

import { generateKeyPair, restoreFromMnemonic } from '@/crypto';
import { initDatabase } from '@/db';
import { keysRepo } from '@/db/repos';
import { useSessionStore } from '@/stores';
import type { KeyPair } from '@/types';

function Card({ children }: { children: React.ReactNode }) {
  return <View className="border-line bg-panel gap-2 rounded-xl border p-4">{children}</View>;
}

export default function OnboardingScreen() {
  const [generatedKeys, setGeneratedKeys] = useState<KeyPair | null>(null);
  const [mnemonicInput, setMnemonicInput] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [savedKeys, setSavedKeys] = useState<KeyPair | null>(null);
  const setPubkey = useSessionStore((state) => state.setPubkey);
  const setReady = useSessionStore((state) => state.setReady);

  useEffect(() => {
    void initDatabase().then(async () => {
      setSavedKeys(await keysRepo.getSavedKeyPair());
    });
  }, []);

  function handleGenerate(): void {
    setGeneratedKeys(generateKeyPair());
    setMnemonicInput('');
  }

  async function persistKeyPair(keyPair: KeyPair, message: string): Promise<void> {
    if (isSaving) {
      return;
    }

    setIsSaving(true);
    try {
      await initDatabase();
      await keysRepo.saveKeyPair(keyPair);
      setPubkey(keyPair.pubkey);
      setReady(true);
      Alert.alert('Saved', message);
      router.replace('/');
    } finally {
      setIsSaving(false);
    }
  }

  async function handleRestore(): Promise<void> {
    const trimmedMnemonic = mnemonicInput.trim().replace(/\s+/g, ' ');
    if (!trimmedMnemonic || isSaving) {
      return;
    }

    try {
      const restoredKeys = restoreFromMnemonic(trimmedMnemonic);
      setGeneratedKeys(restoredKeys);
      await persistKeyPair(restoredKeys, 'Your wallet was restored securely.');
    } catch (error) {
      Alert.alert('Restore failed', error instanceof Error ? error.message : 'Invalid mnemonic');
    }
  }

  async function handleLogBackIn(): Promise<void> {
    if (!savedKeys || isSaving) {
      return;
    }

    await persistKeyPair(savedKeys, 'Logged back in with your saved keypair.');
  }

  return (
    <View className="bg-canvas flex-1 justify-center gap-4 px-6">
      <Text className="text-ink text-[28px] font-bold">Create your Kian wallet</Text>
      <Text className="text-base leading-6 text-slate-600">
        Generate a wallet, back up the mnemonic, or restore from an existing one.
      </Text>

      {savedKeys ? (
        <Card>
          <Text className="text-sm font-semibold text-slate-700">Saved keypair found</Text>
          <Text className="text-sm leading-5 text-slate-600">
            Your private key is still stored on this device, so you can log back in without re-entering anything.
          </Text>
          <Pressable
            className={isSaving ? 'bg-accent rounded-xl py-3.5 opacity-50' : 'bg-accent rounded-xl py-3.5'}
            disabled={isSaving}
            onPress={() => void handleLogBackIn()}
          >
            <Text className="text-center text-base font-semibold text-white">
              {isSaving ? 'Saving...' : 'Log back in'}
            </Text>
          </Pressable>
        </Card>
      ) : null}

      <Pressable className="bg-ink items-center rounded-xl py-3.5" onPress={handleGenerate}>
        <Text className="text-base font-semibold text-white">Generate keypair</Text>
      </Pressable>

      <Card>
        <Text className="text-sm font-semibold text-slate-700">Mnemonic</Text>
        <Text className="text-base leading-6 text-slate-900">
          {generatedKeys?.mnemonic ?? 'Tap generate to create your wallet keys.'}
        </Text>
      </Card>

      <Card>
        <Text className="text-sm font-semibold text-slate-700">Recovery</Text>
        <Text className="text-sm leading-5 text-slate-600">
          Paste a previously backed up mnemonic to restore this wallet.
        </Text>
        <TextInput
          autoCapitalize="none"
          autoCorrect={false}
          className="border-line min-h-[88px] rounded-xl border bg-white px-3 py-2.5"
          multiline
          onChangeText={setMnemonicInput}
          placeholder="Enter mnemonic phrase"
          style={{ textAlignVertical: 'top' }}
          value={mnemonicInput}
        />
        <Pressable
          className={!mnemonicInput.trim() || isSaving ? 'bg-accent rounded-xl py-3.5 opacity-50' : 'bg-accent rounded-xl py-3.5'}
          disabled={!mnemonicInput.trim() || isSaving}
          onPress={() => void handleRestore()}
        >
          <Text className="text-center text-base font-semibold text-white">
            {isSaving ? 'Saving...' : 'Restore wallet'}
          </Text>
        </Pressable>
      </Card>

      <Pressable
        className={!generatedKeys || isSaving ? 'bg-accent rounded-xl py-3.5 opacity-50' : 'bg-accent rounded-xl py-3.5'}
        disabled={!generatedKeys || isSaving}
        onPress={() => void persistKeyPair(generatedKeys as KeyPair, 'Your keys were stored securely.')}
      >
        <Text className="text-center text-base font-semibold text-white">
          {isSaving ? 'Saving...' : 'Save keys'}
        </Text>
      </Pressable>
    </View>
  );
}
