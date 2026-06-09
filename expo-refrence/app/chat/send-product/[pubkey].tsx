import { useEffect, useMemo, useState } from 'react';
import { MaterialIcons } from '@expo/vector-icons';
import { Alert, Image, Pressable, ScrollView, Text, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';

import { initDatabase } from '@/db';
import { keysRepo, productCategoriesRepo, productsRepo, profilesRepo } from '@/db/repos';
import { chatService, walletService } from '@/services';
import type { Product, ProductCategory, Profile } from '@/types';

type SelectedItem = {
  product: Product;
  quantity: number;
};

function getChildren(categories: ProductCategory[], parentId?: string): ProductCategory[] {
  return categories
    .filter((category) => (category.parent_id ?? undefined) === parentId)
    .sort((left, right) => left.name.localeCompare(right.name));
}

function getBranchIds(categories: ProductCategory[], rootId: string): string[] {
  const ids = [rootId];

  for (const category of categories.filter((entry) => entry.parent_id === rootId)) {
    ids.push(...getBranchIds(categories, category.id));
  }

  return ids;
}

function ProductCard({
  item,
  quantity,
  onDecrease,
  onIncrease,
}: {
  item: Product;
  quantity: number;
  onDecrease: () => void;
  onIncrease: () => void;
}) {
  const active = quantity > 0;

  return (
    <View className={active ? 'rounded-3xl border border-sky-300 bg-white p-4 shadow-sm' : 'rounded-3xl border border-transparent bg-white p-4'}>
      <View className="flex-row items-center gap-4">
        {item.images[0] ? (
          <Image className="bg-line h-[90px] w-[90px] rounded-2xl" resizeMode="cover" source={{ uri: item.images[0] }} />
        ) : (
          <View className="bg-line h-[90px] w-[90px] items-center justify-center rounded-2xl border border-slate-200">
            <MaterialIcons color="#94a3b8" name="inventory-2" size={30} />
          </View>
        )}
        <View className="flex-1">
          <Text className="text-ink text-base font-semibold" numberOfLines={2}>{item.name}</Text>
          <View className="mt-1 flex-row items-center gap-2">
            <Text className="text-sm text-slate-500">Product</Text>
            <View className="rounded-md bg-slate-100 px-2 py-1">
              <Text className="text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500">{item.id.slice(-6)}</Text>
            </View>
          </View>
          <View className="mt-3 flex-row justify-end">
            <View className="flex-row items-center rounded-full bg-slate-100 p-1">
              <Pressable className="h-8 w-8 items-center justify-center rounded-full bg-white" onPress={onDecrease}>
                <MaterialIcons color="#0f172a" name="remove" size={16} />
              </Pressable>
              <Text className="text-ink min-w-8 text-center text-sm font-semibold">{quantity}</Text>
              <Pressable className="h-8 w-8 items-center justify-center rounded-full bg-white" onPress={onIncrease}>
                <MaterialIcons color="#0f172a" name="add" size={16} />
              </Pressable>
            </View>
          </View>
        </View>
      </View>
    </View>
  );
}

export default function ChatSendProductScreen() {
  const params = useLocalSearchParams<{ pubkey?: string | string[] }>();
  const peer = useMemo(() => {
    const value = params.pubkey;
    return Array.isArray(value) ? value[0] ?? '' : value ?? '';
  }, [params.pubkey]);
  const [peerProfile, setPeerProfile] = useState<Profile | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [selectedPath, setSelectedPath] = useState<ProductCategory[]>([]);
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [isSending, setIsSending] = useState(false);

  useEffect(() => {
    async function load(): Promise<void> {
      await initDatabase();
      const ownPubkey = await keysRepo.getPublicKey();
      const [nextPeerProfile, nextProducts, nextCategories] = await Promise.all([
        peer ? profilesRepo.get(peer) : Promise.resolve(null),
        ownPubkey ? productsRepo.getByProducer(ownPubkey) : Promise.resolve([]),
        ownPubkey ? productCategoriesRepo.listByPubkey(ownPubkey) : Promise.resolve([]),
      ]);

      setPeerProfile(nextPeerProfile);
      setProducts(nextProducts);
      setCategories(nextCategories);
    }

    void load();
  }, [peer]);

  const filteredProducts = useMemo(() => {
    const selectedLeaf = selectedPath[selectedPath.length - 1];

    if (!selectedLeaf) {
      return products;
    }

    const allowed = new Set(getBranchIds(categories, selectedLeaf.id));
    return products.filter((product) => product.categories.some((categoryId) => allowed.has(categoryId)));
  }, [categories, products, selectedPath]);

  const categoryTiers = useMemo(
    () => Array.from({ length: 5 }, (_, index) => {
      const parentId = index === 0 ? undefined : selectedPath[index - 1]?.id;
      return getChildren(categories, parentId);
    }).filter((level, index) => index === 0 || selectedPath[index - 1]),
    [categories, selectedPath],
  );

  const selectedItems = useMemo<SelectedItem[]>(() => products
    .map((product) => ({ product, quantity: quantities[product.id] ?? 0 }))
    .filter((item) => item.quantity > 0), [products, quantities]);
  const selectedCount = selectedItems.length;

  function updateQuantity(product: Product, delta: number): void {
    setQuantities((current) => {
      const next = Math.max(0, (current[product.id] ?? 0) + delta);
      return { ...current, [product.id]: next };
    });
  }

  async function handleSend(): Promise<void> {
    if (!peer) {
      return;
    }

    if (selectedItems.length === 0) {
      Alert.alert('Nothing selected', 'Choose at least one product before sending in chat.');
      return;
    }

    setIsSending(true);
    try {
      for (const item of selectedItems) {
        const result = await walletService.mintProductForChat(peer, item.product, item.quantity);
        await chatService.sendProduct(peer, result.minted);
      }
      router.replace(`/chat/${peer}`);
    } catch (error) {
      Alert.alert('Unable to send products', error instanceof Error ? error.message : 'Please try again later.');
    } finally {
      setIsSending(false);
    }
  }

  return (
    <View className="bg-canvas flex-1">
      <ScrollView className="flex-1" contentContainerClassName="px-4 pb-28 pt-14">
        <View className="mb-5 flex-row items-center justify-between gap-3 px-1">
          <View>
            <Text className="text-ink text-[28px] font-bold">Send products</Text>
            <Text className="mt-1 text-sm text-slate-500">Pick items and quantities to mint and send as tokens in this chat.</Text>
          </View>
          <View className="rounded-full bg-sky-100 px-4 py-2">
            <Text className="font-semibold text-sky-800">{selectedCount} {selectedCount === 1 ? 'item' : 'items'}</Text>
          </View>
        </View>

        <View className="mb-5 rounded-[28px] bg-white p-4 shadow-sm">
          <Text className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">Recipient</Text>
          <View className="mt-3 flex-row items-center gap-3">
            {peerProfile?.picture ? (
              <Image className="bg-line h-12 w-12 rounded-full" resizeMode="cover" source={{ uri: peerProfile.picture }} />
            ) : (
              <View className="bg-line h-12 w-12 items-center justify-center rounded-full">
                <Text className="text-ink text-lg font-bold">{(peerProfile?.display_name || peer || '?').slice(0, 1).toUpperCase()}</Text>
              </View>
            )}
            <View className="flex-1">
              <Text className="text-ink text-base font-semibold">{peerProfile?.display_name || 'Unknown user'}</Text>
              <Text className="mt-1 text-xs text-slate-500" numberOfLines={1}>{peer || 'Missing recipient pubkey'}</Text>
            </View>
            <Pressable className="h-10 w-10 items-center justify-center rounded-full bg-slate-100" onPress={() => router.back()}>
              <MaterialIcons color="#0f172a" name="close" size={20} />
            </Pressable>
          </View>
        </View>

        <View className="mb-5 rounded-[28px] bg-white p-4 shadow-sm">
          <Text className="mb-4 text-xs font-bold uppercase tracking-[0.18em] text-slate-500">Categories</Text>
          <View className="gap-3">
            {categoryTiers.length === 0 ? (
              <Text className="text-sm text-slate-500">No categories yet. All of your products are shown below.</Text>
            ) : (
              categoryTiers.map((tier, index) => (
                <ScrollView horizontal key={`tier-${index + 1}`} showsHorizontalScrollIndicator={false}>
                  <View className="flex-row gap-2">
                    {tier.map((category) => {
                      const active = selectedPath[index]?.id === category.id;
                      return (
                        <Pressable
                          className={active ? 'rounded-full bg-sky-700 px-4 py-2.5' : 'rounded-full border border-slate-200 bg-slate-50 px-4 py-2.5'}
                          key={category.id}
                          onPress={() => {
                            if (active) {
                              setSelectedPath((current) => current.slice(0, category.level - 1));
                              return;
                            }
                            setSelectedPath((current) => [...current.slice(0, category.level - 1), category]);
                          }}
                        >
                          <Text className={active ? 'font-semibold text-white' : 'font-semibold text-slate-700'}>{category.name}</Text>
                        </Pressable>
                      );
                    })}
                  </View>
                </ScrollView>
              ))
            )}
          </View>
        </View>

        <View className="gap-3">
          {filteredProducts.length === 0 ? (
            <View className="rounded-[24px] bg-white px-4 py-10 shadow-sm">
              <Text className="text-center text-sm text-slate-500">No products found in this category.</Text>
            </View>
          ) : (
            filteredProducts.map((item) => (
              <ProductCard
                item={item}
                key={`${item.pubkey}-${item.id}`}
                onDecrease={() => updateQuantity(item, -1)}
                onIncrease={() => updateQuantity(item, 1)}
                quantity={quantities[item.id] ?? 0}
              />
            ))
          )}
        </View>
      </ScrollView>

      <View className="border-line bg-canvas border-t px-4 pb-6 pt-4">
        <Pressable
          className={isSending ? 'items-center rounded-[20px] bg-sky-700 py-4 opacity-50' : 'items-center rounded-[20px] bg-sky-700 py-4'}
          disabled={isSending}
          onPress={() => void handleSend()}
        >
          <View className="flex-row items-center gap-2">
            <MaterialIcons color="#ffffff" name="arrow-forward" size={18} />
            <Text className="font-semibold text-white">{isSending ? 'Sending...' : 'Send in chat'}</Text>
          </View>
        </Pressable>
      </View>
    </View>
  );
}
