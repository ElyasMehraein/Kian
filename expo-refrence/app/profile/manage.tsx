import { useCallback, useEffect, useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';

import { buildMetadata } from '@/builders';
import { signEvent } from '@/crypto';
import { initDatabase } from '@/db';
import { keysRepo, profilesRepo } from '@/db/repos';
import { RelayPool } from '@/nostr';
import type { Profile } from '@/types';

type Draft = {
  display_name: string;
  about: string;
  picture: string;
  nip05: string;
  lud16: string;
  geohash: string;
};

type StoredProfile = Profile & {
  is_trader?: boolean;
  tags?: string[][];
};

const emptyDraft: Draft = {
  display_name: '',
  about: '',
  picture: '',
  nip05: '',
  lud16: '',
  geohash: '',
};

const relayPool = new RelayPool();
let isConnected = false;

async function ensureConnected(): Promise<void> {
  if (!isConnected) {
    await relayPool.connect();
    isConnected = true;
  }
}

function normalizeValue(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed || undefined;
}

function getNextProfileTimestamp(existingCreatedAt?: number): number {
  const now = Math.floor(Date.now() / 1000);
  return Math.max(now, (existingCreatedAt ?? 0) + 1);
}

export default function ProfileManagerScreen() {
  const [draft, setDraft] = useState<Draft>(emptyDraft);
  const [pubkey, setPubkey] = useState<string | null>(null);
  const [existingProfile, setExistingProfile] = useState<StoredProfile | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const loadProfile = useCallback(async (): Promise<void> => {
    await initDatabase();
    const currentPubkey = await keysRepo.getPublicKey();
    setPubkey(currentPubkey);

    if (!currentPubkey) {
      setExistingProfile(null);
      setDraft(emptyDraft);
      return;
    }

    const profile = (await profilesRepo.get(currentPubkey)) as StoredProfile | null;
    setExistingProfile(profile);
    setDraft({
      display_name: profile?.display_name ?? '',
      about: profile?.about ?? '',
      picture: profile?.picture ?? '',
      nip05: profile?.nip05 ?? '',
      lud16: profile?.lud16 ?? '',
      geohash: profile?.geohash ?? '',
    });
  }, []);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  useFocusEffect(
    useCallback(() => {
      void loadProfile();
    }, [loadProfile]),
  );

  function updateDraft<K extends keyof Draft>(key: K, value: Draft[K]): void {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  async function handleSave(): Promise<void> {
    if (!pubkey || isSaving) {
      return;
    }

    const privkey = await keysRepo.getPrivateKey();
    if (!privkey) {
      return;
    }

    const profile: Profile = {
      ...existingProfile,
      pubkey,
      created_at: getNextProfileTimestamp(existingProfile?.created_at),
      display_name: normalizeValue(draft.display_name),
      about: normalizeValue(draft.about),
      picture: normalizeValue(draft.picture),
      nip05: normalizeValue(draft.nip05),
      lud16: normalizeValue(draft.lud16),
      geohash: normalizeValue(draft.geohash),
    };

    setIsSaving(true);

    try {
      const signed = signEvent(buildMetadata(profile), privkey);
      await profilesRepo.upsert(profile);
      await ensureConnected();
      relayPool.publish(signed);
      router.replace(`/user/${pubkey}`);
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <ScrollView className="bg-canvas flex-1" contentContainerClassName="px-5 pb-8 pt-14">
      <Text className="text-ink text-[28px] font-bold">Edit profile</Text>
      <Text className="mt-1.5 mb-4 text-slate-500">{pubkey ? 'Update your public profile metadata.' : 'Create keys first.'}</Text>

      <TextInput className="border-line mb-2.5 rounded-xl border px-3 py-2.5" onChangeText={(value) => updateDraft('display_name', value)} placeholder="Display name" value={draft.display_name} />
      <TextInput className="border-line mb-2.5 min-h-[120px] rounded-xl border px-3 py-2.5" multiline onChangeText={(value) => updateDraft('about', value)} placeholder="Bio" style={{ textAlignVertical: 'top' }} value={draft.about} />
      <TextInput autoCapitalize="none" autoCorrect={false} className="border-line mb-2.5 rounded-xl border px-3 py-2.5" onChangeText={(value) => updateDraft('picture', value)} placeholder="Avatar URL" value={draft.picture} />
      <TextInput autoCapitalize="none" autoCorrect={false} className="border-line mb-2.5 rounded-xl border px-3 py-2.5" onChangeText={(value) => updateDraft('nip05', value)} placeholder="NIP-05" value={draft.nip05} />
      <TextInput autoCapitalize="none" autoCorrect={false} className="border-line mb-2.5 rounded-xl border px-3 py-2.5" onChangeText={(value) => updateDraft('lud16', value)} placeholder="Lightning address" value={draft.lud16} />
      <TextInput autoCapitalize="none" autoCorrect={false} className="border-line mb-4 rounded-xl border px-3 py-2.5" onChangeText={(value) => updateDraft('geohash', value)} placeholder="Geohash" value={draft.geohash} />

      <View className="flex-row gap-2.5">
        <Pressable className="border-line flex-1 items-center rounded-xl border py-3" onPress={() => router.back()}>
          <Text className="text-ink font-semibold">Cancel</Text>
        </Pressable>
        <Pressable className={isSaving ? 'bg-ink flex-1 items-center rounded-xl py-3 opacity-50' : 'bg-ink flex-1 items-center rounded-xl py-3'} disabled={!pubkey || isSaving} onPress={() => void handleSave()}>
          <Text className="font-semibold text-white">{isSaving ? 'Saving...' : 'Save profile'}</Text>
        </Pressable>
      </View>
    </ScrollView>
  );
}
