import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, FlatList, Image, Platform, Pressable, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';

import { initDatabase } from '@/db';
import { profilesRepo } from '@/db/repos';
import { rankMerchants } from '@/services';
import { useUIStore } from '@/stores';
import type { MerchantInfo } from '@/types';

type SortMode = 'score' | 'mutual' | 'distance' | 'social';
const WEB_DB_STORAGE_KEY = 'kian:web-db';

function formatDistance(distanceKm?: number): string {
  return distanceKm == null ? 'Distance unknown' : `${distanceKm.toFixed(1)} km away`;
}

function sortMerchants(merchants: MerchantInfo[], sortMode: SortMode): MerchantInfo[] {
  const sorted = [...merchants];
  sorted.sort((left, right) => {
    switch (sortMode) {
      case 'mutual':
        return right.mutual_follows - left.mutual_follows || right.score - left.score;
      case 'distance': {
        const leftDistance = left.distance_km ?? Number.POSITIVE_INFINITY;
        const rightDistance = right.distance_km ?? Number.POSITIVE_INFINITY;
        return leftDistance - rightDistance || right.score - left.score;
      }
      case 'social':
        return (right.social_rating ?? 0) - (left.social_rating ?? 0) || right.score - left.score;
      default:
        return right.score - left.score;
    }
  });
  return sorted;
}

function SortChip({ active, label, onPress }: { active: boolean; label: string; onPress: () => void }) {
  return (
    <Pressable
      className={active ? 'border-accent rounded-full border bg-white px-3 py-2' : 'border-line rounded-full border bg-white px-3 py-2'}
      onPress={onPress}
    >
      <Text className={active ? 'text-accent font-bold' : 'font-semibold text-slate-600'}>{label}</Text>
    </Pressable>
  );
}

function MerchantRow({ item }: { item: MerchantInfo }) {
  const truncatedPubkey = `${item.pubkey.slice(0, 8)}...${item.pubkey.slice(-8)}`;

  return (
    <Pressable className="border-line bg-panel rounded-xl border p-3.5" onPress={() => router.push(`/user/${item.pubkey}`)}>
      <View className="flex-row gap-3">
        {item.profile.picture ? (
          <Image className="bg-line h-14 w-14 rounded-full" resizeMode="cover" source={{ uri: item.profile.picture }} />
        ) : (
          <View className="bg-line h-14 w-14 items-center justify-center rounded-full">
            <Text className="text-ink text-lg font-bold">
              {(item.profile.display_name || item.pubkey).slice(0, 1).toUpperCase()}
            </Text>
          </View>
        )}
        <View className="flex-1">
          <Text className="text-ink text-base font-semibold">{item.profile.display_name || 'Unnamed merchant'}</Text>
          <Text className="mt-1 text-xs text-slate-500">{truncatedPubkey}</Text>
          <Text className="mt-1 text-sm leading-5 text-slate-600">{item.profile.about || 'No bio yet.'}</Text>
          <Text className="mt-1 text-sm text-slate-600">{item.title}</Text>
          <Text className="mt-1 text-sm text-slate-600">{formatDistance(item.distance_km)}</Text>
          <Text className="mt-1 text-sm text-slate-600">Mutual follows: {item.mutual_follows}</Text>
          <Text className="mt-1 text-sm text-slate-600">Social rating: {(item.social_rating ?? 0).toFixed(1)}</Text>
          <Text className="mt-1 text-sm text-slate-600">Score: {item.score.toFixed(1)}</Text>
        </View>
      </View>
    </Pressable>
  );
}

export default function MerchantDiscoveryScreen() {
  const [merchants, setMerchants] = useState<MerchantInfo[]>([]);
  const [sortMode, setSortMode] = useState<SortMode>('score');
  const profileVersion = useUIStore((state) => state.profileVersion);

  const loadMerchants = useCallback(async (): Promise<void> => {
    await initDatabase();
    const merchantProfiles = await profilesRepo.listMerchants();
    setMerchants(await rankMerchants(merchantProfiles.map((profile) => profile.pubkey)));
  }, []);

  useEffect(() => {
    void loadMerchants();
  }, [loadMerchants, profileVersion]);

  useFocusEffect(
    useCallback(() => {
      void loadMerchants();
    }, [loadMerchants]),
  );

  const sortedMerchants = useMemo(() => sortMerchants(merchants, sortMode), [sortMode, merchants]);

  function handleClearWebDb(): void {
    if (Platform.OS !== 'web' || typeof localStorage === 'undefined' || typeof window === 'undefined') {
      Alert.alert('Unavailable', 'DB cleaner is only available on web.');
      return;
    }

    localStorage.removeItem(WEB_DB_STORAGE_KEY);
    window.location.reload();
  }

  return (
    <View className="bg-canvas flex-1 px-5 pt-14">
      <View className="mb-4 flex-row items-center justify-between gap-3">
        <Text className="text-ink text-[28px] font-bold">Merchants</Text>
        <Pressable className="rounded-full border border-red-200 bg-red-50 px-3.5 py-2" onPress={handleClearWebDb}>
          <Text className="text-sm font-semibold text-red-700">Clear DB</Text>
        </Pressable>
      </View>
      <View className="mb-3 flex-row flex-wrap gap-2">
        <SortChip active={sortMode === 'score'} label="Ranked" onPress={() => setSortMode('score')} />
        <SortChip active={sortMode === 'mutual'} label="Mutual" onPress={() => setSortMode('mutual')} />
        <SortChip active={sortMode === 'distance'} label="Distance" onPress={() => setSortMode('distance')} />
        <SortChip active={sortMode === 'social'} label="Rating" onPress={() => setSortMode('social')} />
      </View>
      <FlatList
        contentContainerClassName="gap-2.5 pb-6"
        data={sortedMerchants}
        keyExtractor={(item) => item.pubkey}
        ListEmptyComponent={<Text className="text-[15px] text-slate-500">No merchants discovered yet.</Text>}
        renderItem={({ item }) => <MerchantRow item={item} />}
      />
    </View>
  );
}
