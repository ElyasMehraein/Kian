import { useCallback, useEffect, useMemo, useState } from 'react';
import { FlatList, Pressable, ScrollView, Text, View } from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import { router, useFocusEffect } from 'expo-router';

import { initDatabase } from '@/db';
import { keysRepo, productCategoriesRepo, productsRepo, tokenDefinitionsRepo } from '@/db/repos';
import { messageEvents } from '@/services/message-events';
import { walletService } from '@/services';
import { TOKEN_UTXO_TAG_PREFIX } from '@/types';
import type { TokenDefinition, TokenUtxo } from '@/types';
import { BalanceRow, PendingRow, SelectorChip, UtxoRow } from '@/components/wallet-ui';

type BalanceItem = {
  assetRef: string;
  amount: number;
  description: string;
  images: string[];
  name: string;
  producer: string;
  categories: string[];
  unit: string;
};

type PendingItem = { eventId: string; utxoId: string; assetRef: string; amount: number; recipient: string; status: 'waiting_mint' | 'fulfilled' | 'rejected' | 'offline' };

const formatAssetRef = (value: string) => `${value.slice(0, 10)}...${value.slice(-6)}`;

const parseAssetRef = (assetRef: string) => {
  const [kind, producer, assetId] = assetRef.split(':', 3);
  return kind === TOKEN_UTXO_TAG_PREFIX && producer && assetId ? { producer, assetId } : null;
};

export default function WalletScreen() {
  const [balances, setBalances] = useState<BalanceItem[]>([]);
  const [utxos, setUtxos] = useState<TokenUtxo[]>([]);
  const [pending, setPending] = useState<PendingItem[]>([]);
  const [activityFilter, setActivityFilter] = useState<'all' | PendingItem['status']>('all');

  const loadWallet = useCallback(async (): Promise<void> => {
    await initDatabase();
    await keysRepo.getPublicKey();
    const [nextBalances, nextUtxos, nextPending] = await Promise.all([
      walletService.getBalanceByAsset(),
      walletService.getUTXOs(),
      walletService.getPendingConfirmations(),
    ]);
    const definitionsByAssetRef = new Map<string, TokenDefinition>();

    await Promise.all(
      Object.keys(nextBalances).map(async (assetRef) => {
        const parsed = parseAssetRef(assetRef);

        if (!parsed) {
          return;
        }

        const definition = await tokenDefinitionsRepo.get(parsed.assetId, parsed.producer);

        if (definition) {
          definitionsByAssetRef.set(assetRef, definition);
          return;
        }

        const product = await productsRepo.get(parsed.assetId, parsed.producer);

        if (!product) {
          return;
        }

        const categoryNames = product.categories.length > 0
          ? (await productCategoriesRepo.listByPubkey(parsed.producer))
            .filter((category) => product.categories.includes(category.id))
            .map((category) => category.name)
          : [];

        definitionsByAssetRef.set(assetRef, {
          asset_id: parsed.assetId,
          pubkey: parsed.producer,
          product_id: product.id,
          name: product.name,
          description: product.description,
          images: product.images,
          categories: categoryNames,
          unit: 'unit',
          created_at: product.created_at,
        });
      }),
    );

    setBalances(
      Object.entries(nextBalances).map(([assetRef, amount]) => {
        const parsed = parseAssetRef(assetRef);
        const definition = definitionsByAssetRef.get(assetRef) ?? null;

        return {
          assetRef,
          amount,
          description: definition?.description || '',
          images: definition?.images || [],
          name: definition?.name || formatAssetRef(assetRef),
          producer: parsed?.producer || '',
          categories: definition?.categories || [],
          unit: definition?.unit || 'unit',
        };
      }),
    );
    setUtxos(nextUtxos);
    setPending(nextPending.map((item) => ({ eventId: item.event_id, utxoId: item.utxo_id, assetRef: item.asset_ref, amount: item.amount, recipient: item.recipient, status: item.status })));
  }, []);

  useEffect(() => { void loadWallet(); }, [loadWallet]);
  useFocusEffect(
    useCallback(() => {
      void loadWallet();
    }, [loadWallet]),
  );
  useEffect(() => {
    const unsubscribe = messageEvents.subscribe(() => {
      void loadWallet();
    });

    return unsubscribe;
  }, [loadWallet]);


  const filteredPending = useMemo(() => (
    activityFilter === 'all'
      ? pending
      : pending.filter((item) => item.status === activityFilter)
  ), [activityFilter, pending]);

  function handleOpenProducer(pubkey: string): void {
    if (!pubkey) {
      return;
    }

    router.push(`/user/${pubkey}`);
  }

  function getUtxoLabel(utxo: TokenUtxo): string {
    const parsed = parseAssetRef(utxo.asset_ref);
    if (!parsed) return formatAssetRef(utxo.utxo_id);
    const definition = balances.find((item) => item.assetRef === utxo.asset_ref);
    return definition?.name || formatAssetRef(utxo.utxo_id);
  }

  return (
    <View className="bg-canvas flex-1">
      <ScrollView className="flex-1" contentContainerClassName="px-5 pb-24 pt-16">
        <Text className="text-ink mb-5 text-[28px] font-bold">Wallet</Text>
        <Text className="text-ink mb-3 text-lg font-semibold">Token balances</Text>
        <FlatList contentContainerClassName="gap-2.5 pb-6" data={balances} keyExtractor={(item) => item.assetRef} ListEmptyComponent={<Text className="text-[15px] text-slate-500">No token balances yet.</Text>} renderItem={({ item }) => <BalanceRow amount={item.amount} assetRef={item.assetRef} categories={item.categories} description={item.description} formatAssetRef={formatAssetRef} images={item.images} name={item.name} onPressProducer={handleOpenProducer} producer={item.producer} unit={item.unit} />} scrollEnabled={false} />
        <Text className="text-ink mb-3 text-lg font-semibold">Token transfer activity</Text>
        <View className="mb-3 flex-row flex-wrap gap-2">
          <SelectorChip active={activityFilter === 'all'} label="All" onPress={() => setActivityFilter('all')} />
          <SelectorChip active={activityFilter === 'waiting_mint'} label="Waiting" onPress={() => setActivityFilter('waiting_mint')} />
          <SelectorChip active={activityFilter === 'fulfilled'} label="Completed" onPress={() => setActivityFilter('fulfilled')} />
          <SelectorChip active={activityFilter === 'rejected'} label="Rejected" onPress={() => setActivityFilter('rejected')} />
          <SelectorChip active={activityFilter === 'offline'} label="Offline" onPress={() => setActivityFilter('offline')} />
        </View>
        <FlatList contentContainerClassName="gap-2.5 pb-6" data={filteredPending} keyExtractor={(item) => item.eventId} ListEmptyComponent={<Text className="text-[15px] text-slate-500">No token transfer activity for this filter yet.</Text>} renderItem={({ item }) => <PendingRow formatAssetRef={formatAssetRef} item={item} />} scrollEnabled={false} />
        <Text className="text-ink mb-3 text-lg font-semibold">Spendable token entries</Text>
        <FlatList contentContainerClassName="gap-2.5 pb-6" data={utxos} keyExtractor={(item) => item.utxo_id} ListEmptyComponent={<Text className="text-[15px] text-slate-500">No spendable token entries yet.</Text>} renderItem={({ item }) => <UtxoRow formatAssetRef={formatAssetRef} item={item} label={getUtxoLabel(item)} />} scrollEnabled={false} />
      </ScrollView>
      <Pressable className="bg-ink absolute bottom-6 right-5 h-14 min-w-[56px] flex-row items-center justify-center gap-2 rounded-full px-4" onPress={() => router.push('/tokens/send')}>
        <MaterialIcons color="#ffffff" name="send" size={20} />
        <Text className="font-semibold text-white">Send Token</Text>
      </Pressable>
    </View>
  );
}
