import { useCallback, useEffect, useState } from 'react';
import { Alert, Modal, Pressable, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';

import { buildMetadata } from '@/builders';
import { signEvent } from '@/crypto';
import { initDatabase } from '@/db';
import { keysRepo, profilesRepo } from '@/db/repos';
import { RelayPool } from '@/nostr';
import { subscriptionService } from '@/services';
import { resetInboxRelayState } from '@/services/dm-relays';
import { useSessionStore, useUIStore } from '@/stores';

import { AccountSwitcher, MenuLink, ProfileLink, type MetadataProfile } from './app-menu/sections';

const shadowStyle = {
  boxShadow: '0 8px 24px rgba(0, 0, 0, 0.15)',
} as const;

const relayPool = new RelayPool();
let isRelayConnected = false;

async function ensureConnected(): Promise<void> {
  if (!isRelayConnected) {
    await relayPool.connect();
    isRelayConnected = true;
  }
}

function getNextProfileTimestamp(existingCreatedAt?: number): number {
  const now = Math.floor(Date.now() / 1000);
  return Math.max(now, (existingCreatedAt ?? 0) + 1);
}

export function AppMenu() {
  const [isOpen, setIsOpen] = useState(false);
  const [profile, setProfile] = useState<MetadataProfile | null>(null);
  const [resolvedPubkey, setResolvedPubkey] = useState<string | null>(null);
  const [isModeResolved, setIsModeResolved] = useState(false);
  const sessionPubkey = useSessionStore((state) => state.pubkey);
  const accountMode = useUIStore((state) => state.accountMode);
  const setAccountMode = useUIStore((state) => state.setAccountMode);
  const bumpProfileVersion = useUIStore((state) => state.bumpProfileVersion);
  const resetSession = useSessionStore((state) => state.reset);
  const resetUI = useUIStore((state) => state.reset);

  const loadProfile = useCallback(async (): Promise<void> => {
    await initDatabase();
    const currentPubkey = sessionPubkey ?? (await keysRepo.getPublicKey());
    setResolvedPubkey(currentPubkey);

    if (!currentPubkey) {
      setProfile(null);
      setIsModeResolved(true);
      return;
    }

    const nextProfile = (await profilesRepo.get(currentPubkey)) as MetadataProfile | null;
    setProfile(nextProfile);
    setAccountMode(nextProfile?.is_trader ? 'merchant' : 'business');
    setIsModeResolved(true);
  }, [sessionPubkey, setAccountMode]);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  useFocusEffect(useCallback(() => {
    void loadProfile();
  }, [loadProfile]));

  async function handleLogout(): Promise<void> {
    setIsOpen(false);

    try {
      await keysRepo.clearSession();
      resetInboxRelayState();
      subscriptionService.resetSubscriptions();
      resetSession();
      resetUI();
      router.replace('/onboarding');
    } catch (error) {
      Alert.alert('Logout failed', error instanceof Error ? error.message : 'Unable to clear the current account.');
    }
  }

  async function handleAccountModeChange(mode: 'business' | 'merchant'): Promise<void> {
    if (mode === accountMode) {
      return;
    }

    if (!resolvedPubkey) {
      setAccountMode(mode);
      return;
    }

    const privkey = await keysRepo.getPrivateKey();
    if (!privkey) {
      Alert.alert('Mode change failed', 'Missing private key for profile update.');
      return;
    }

    const nextProfile: MetadataProfile = {
      ...(profile ?? { pubkey: resolvedPubkey }),
      pubkey: resolvedPubkey,
      created_at: getNextProfileTimestamp(profile?.created_at),
      is_trader: mode === 'merchant',
    };

    try {
      const signed = signEvent(buildMetadata(nextProfile), privkey);
      await profilesRepo.upsert(nextProfile);
      await ensureConnected();
      relayPool.publish(signed);
      setProfile(nextProfile);
      setAccountMode(mode);
      bumpProfileVersion();
    } catch (error) {
      Alert.alert('Mode change failed', error instanceof Error ? error.message : 'Unable to update merchant visibility.');
    }
  }

  return (
    <>
      <Pressable
        accessibilityLabel="Open application menu"
        className="bg-ink absolute right-5 top-[18px] h-12 w-12 items-center justify-center rounded-2xl"
        onPress={() => setIsOpen(true)}
        style={shadowStyle}
      >
        <Text className="text-3xl font-bold leading-7 text-white">≡</Text>
      </Pressable>

      <Modal animationType="fade" onRequestClose={() => setIsOpen(false)} transparent visible={isOpen}>
        <Pressable className="flex-1 bg-slate-900/40" onPress={() => setIsOpen(false)}>
          <View className="items-end px-4 pt-20">
            <Pressable className="border-line bg-canvas w-full max-w-80 rounded-3xl border p-5" onPress={() => {}}>
              <Text className="text-ink mb-2 text-xl font-bold">Application menu</Text>
              <ProfileLink
                profile={profile}
                resolvedPubkey={resolvedPubkey}
                onPress={() => {
                  if (!resolvedPubkey) return;
                  setIsOpen(false);
                  router.push(`/user/${resolvedPubkey}`);
                }}
              />
              <MenuLink label="Relay Management" onPress={() => { setIsOpen(false); router.push('/relay-status'); }} />
              <MenuLink label="Pending Events" onPress={() => { setIsOpen(false); router.push('/pending-events'); }} />
              <MenuLink label="Private Key Management" onPress={() => { setIsOpen(false); router.push('/private-key'); }} />
              <AccountSwitcher
                accountMode={accountMode}
                isModeResolved={isModeResolved}
                onSelect={(mode) => { void handleAccountModeChange(mode); }}
              />
              <MenuLink destructive label="Logout" onPress={() => { void handleLogout(); }} />
            </Pressable>
          </View>
        </Pressable>
      </Modal>
    </>
  );
}
