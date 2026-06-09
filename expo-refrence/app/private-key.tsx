import { useEffect, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';

import { keysRepo } from '@/db/repos';

type SecretFieldProps = {
  label: string;
  value: string | null;
};

function SecretField({ label, value }: SecretFieldProps) {
  const [isVisible, setIsVisible] = useState(false);

  return (
    <View className="border-line bg-panel gap-2.5 rounded-2xl border p-4">
      <View className="flex-row items-center justify-between gap-3">
        <Text className="text-ink text-[15px] font-semibold">{label}</Text>
        <Pressable
          className="bg-ink rounded-full px-3 py-2"
          onPress={() => setIsVisible((current) => !current)}
        >
          <Text className="text-xs font-semibold text-white">{isVisible ? 'Hide' : 'Reveal'}</Text>
        </Pressable>
      </View>
      <Text className="text-sm leading-6 text-slate-700" selectable>
        {isVisible ? value ?? 'Unavailable' : 'Hidden for safety'}
      </Text>
    </View>
  );
}

export default function PrivateKeyScreen() {
  const [pubkey, setPubkey] = useState<string | null>(null);
  const [privateKey, setPrivateKey] = useState<string | null>(null);
  const [mnemonic, setMnemonic] = useState<string | null>(null);

  useEffect(() => {
    async function loadKeys(): Promise<void> {
      const [nextPubkey, nextPrivateKey, nextMnemonic] = await Promise.all([
        keysRepo.getPublicKey(),
        keysRepo.getPrivateKey(),
        keysRepo.getMnemonic(),
      ]);

      setPubkey(nextPubkey);
      setPrivateKey(nextPrivateKey);
      setMnemonic(nextMnemonic);
    }

    void loadKeys();
  }, []);

  return (
    <ScrollView className="bg-canvas" contentContainerClassName="gap-3.5 px-5 pb-8 pt-14">
      <Text className="text-ink text-[28px] font-bold">Private Key Management</Text>
      <Text className="text-[15px] leading-6 text-slate-600">
        Reveal sensitive values only when needed and keep them off shared screens.
      </Text>

      <View className="border-line bg-panel gap-2.5 rounded-2xl border p-4">
        <Text className="text-ink text-[15px] font-semibold">Public key</Text>
        <Text className="text-sm leading-6 text-slate-900" selectable>
          {pubkey ?? 'Unavailable'}
        </Text>
      </View>

      <SecretField label="Private key" value={privateKey} />
      <SecretField label="Mnemonic" value={mnemonic} />
    </ScrollView>
  );
}
