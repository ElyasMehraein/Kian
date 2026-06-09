import { useEffect } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { router } from 'expo-router';

import { bootstrap } from '@/bootstrap';
import { useSessionStore, useUIStore } from '@/stores';

export default function HomeScreen() {
  const pubkey = useSessionStore((state) => state.pubkey);
  const isReady = useSessionStore((state) => state.isReady);
  const errorMessage = useUIStore((state) => state.errorMessage);

  useEffect(() => {
    void bootstrap().catch(() => undefined);
  }, []);

  useEffect(() => {
    if (isReady) {
      router.replace(pubkey ? '/(tabs)/home' : '/onboarding');
    }
  }, [isReady, pubkey]);

  return (
    <View className="bg-canvas flex-1 items-center justify-center px-6">
      <ActivityIndicator color="#2563eb" size="large" />
      <Text className="text-ink mt-3 text-lg font-semibold">Starting Kian...</Text>
      {errorMessage ? <Text className="text-center text-sm text-red-600 mt-2.5">{errorMessage}</Text> : null}
    </View>
  );
}
