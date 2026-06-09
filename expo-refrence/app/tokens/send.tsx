import { useCallback, useEffect, useMemo, useState } from 'react';
import { MaterialIcons } from '@expo/vector-icons';
import { Alert, Image, Platform, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';

import { initDatabase } from '@/db';
import { keysRepo, productCategoriesRepo, productsRepo, tokenDefinitionsRepo } from '@/db/repos';
import { walletService } from '@/services';
import type { TokenDefinition, TokenUtxo } from '@/types';

type TokenCardItem = {
  utxo: TokenUtxo;
  definition: TokenDefinition | null;
};

type TokenCategoryNode = {
  id: string;
  level: number;
  name: string;
  parentId?: string;
};

function parseAssetRef(assetRef: string): { producer: string; assetId: string } | null {
  const [kind, producer, assetId] = assetRef.split(':', 3);
  return kind === '35001' && producer && assetId ? { producer, assetId } : null;
}

function buildCategoryTree(definitions: TokenDefinition[]): TokenCategoryNode[] {
  const nodes = new Map<string, TokenCategoryNode>();

  for (const definition of definitions) {
    let parentId: string | undefined;

    for (const [index, name] of definition.categories.entries()) {
      const normalized = name.trim();

      if (!normalized) {
        continue;
      }

      const level = index + 1;
      const nodeId = parentId ? `${parentId}>${normalized}` : normalized;

      if (!nodes.has(nodeId)) {
        nodes.set(nodeId, {
          id: nodeId,
          level,
          name: normalized,
          parentId,
        });
      }

      parentId = nodeId;
    }
  }

  return [...nodes.values()];
}

function getChildren(nodes: TokenCategoryNode[], parentId?: string): TokenCategoryNode[] {
  return nodes
    .filter((node) => node.parentId === parentId)
    .sort((left, right) => left.name.localeCompare(right.name));
}

function getBranchIds(nodes: TokenCategoryNode[], rootId: string): string[] {
  const ids = [rootId];

  for (const node of nodes.filter((entry) => entry.parentId === rootId)) {
    ids.push(...getBranchIds(nodes, node.id));
  }

  return ids;
}

function showSendTokenAlert(title: string, message: string): void {
  if (Platform.OS === 'web') {
    window.alert(`${title}\n\n${message}`);
    return;
  }

  Alert.alert(title, message);
}

function formatCreatedAt(createdAt: number): string {
  return new Date(createdAt * 1000).toLocaleString([], {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function TokenCard({
  item,
  quantity,
  onDecrease,
  onIncrease,
}: {
  item: TokenCardItem;
  quantity: number;
  onDecrease: () => void;
  onIncrease: () => void;
}) {
  const active = quantity > 0;
  const maxAmount = item.utxo.amount;

  return (
    <View className={active ? 'rounded-3xl border border-sky-300 bg-white p-4 shadow-sm' : 'rounded-3xl border border-transparent bg-white p-4'}>
      <View className="flex-row items-center gap-4">
        {item.definition?.images[0] ? (
          <Image className="bg-line h-[90px] w-[90px] rounded-2xl" resizeMode="cover" source={{ uri: item.definition.images[0] }} />
        ) : (
          <View className="bg-line h-[90px] w-[90px] items-center justify-center rounded-2xl border border-slate-200">
            <MaterialIcons color="#94a3b8" name="account-balance-wallet" size={30} />
          </View>
        )}
        <View className="flex-1">
          <Text className="text-ink text-base font-semibold" numberOfLines={2}>{item.definition?.name || item.utxo.utxo_id.slice(-8)}</Text>
          <Text className="mt-1 text-sm leading-5 text-slate-500" numberOfLines={2}>{item.definition?.description || 'Token entry ready to transfer.'}</Text>
          {item.definition?.categories.length ? (
            <View className="mt-2 flex-row flex-wrap gap-2">
              {item.definition.categories.map((category) => (
                <View className="rounded-full bg-slate-100 px-2.5 py-1" key={category}>
                  <Text className="text-xs font-semibold text-slate-600">{category}</Text>
                </View>
              ))}
            </View>
          ) : null}
          <Text className="mt-2 text-xs text-slate-500">
            Issued {formatCreatedAt(item.utxo.created_at)}
          </Text>
          <View className="mt-3 flex-row items-center justify-between gap-3">
            <Text className="text-sm font-semibold text-slate-500">Available: {maxAmount} {item.definition?.unit || 'unit'}</Text>
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

export default function SendTokensScreen() {
  const params = useLocalSearchParams<{ recipient?: string | string[] }>();
  const initialRecipient = Array.isArray(params.recipient) ? (params.recipient[0] ?? '') : (params.recipient ?? '');
  const [recipient, setRecipient] = useState(initialRecipient);
  const [items, setItems] = useState<TokenCardItem[]>([]);
  const [categoryNodes, setCategoryNodes] = useState<TokenCategoryNode[]>([]);
  const [selectedPath, setSelectedPath] = useState<TokenCategoryNode[]>([]);
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [isSending, setIsSending] = useState(false);

  const load = useCallback(async (): Promise<void> => {
    await initDatabase();
    const pubkey = await keysRepo.getPublicKey();

    if (!pubkey) {
      setItems([]);
      setCategoryNodes([]);
      return;
    }

    const utxos = await walletService.getUTXOs();
    const uniqueDefinitions = new Map<string, TokenDefinition>();

    await Promise.all(utxos.map(async (utxo) => {
      const parsed = parseAssetRef(utxo.asset_ref);

      if (!parsed) {
        return;
      }

      const cacheKey = `${parsed.producer}:${parsed.assetId}`;
      if (uniqueDefinitions.has(cacheKey)) {
        return;
      }

      const definition = await tokenDefinitionsRepo.get(parsed.assetId, parsed.producer);
      if (definition) {
        uniqueDefinitions.set(cacheKey, definition);
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

      uniqueDefinitions.set(cacheKey, {
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
    }));

    const nextItems = utxos.map((utxo) => {
      const parsed = parseAssetRef(utxo.asset_ref);
      const definition = parsed ? uniqueDefinitions.get(`${parsed.producer}:${parsed.assetId}`) ?? null : null;
      return { utxo, definition };
    });

    setItems(nextItems);
    setCategoryNodes(buildCategoryTree([...uniqueDefinitions.values()]));
  }, []);

  useEffect(() => {
    setRecipient(initialRecipient);
  }, [initialRecipient]);

  useEffect(() => {
    void load();
  }, [load]);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  const filteredItems = useMemo(() => {
    const selectedLeaf = selectedPath[selectedPath.length - 1];

    if (!selectedLeaf) {
      return items;
    }

    const allowed = new Set(getBranchIds(categoryNodes, selectedLeaf.id));
    return items.filter((item) => {
      const categories = item.definition?.categories ?? [];
      let parentId: string | undefined;

      for (const name of categories) {
        const nodeId = parentId ? `${parentId}>${name}` : name;
        if (allowed.has(nodeId)) {
          return true;
        }
        parentId = nodeId;
      }

      return false;
    });
  }, [categoryNodes, items, selectedPath]);

  const tiers = useMemo(
    () => Array.from({ length: 5 }, (_, index) => {
      const parentId = index === 0 ? undefined : selectedPath[index - 1]?.id;
      return getChildren(categoryNodes, parentId);
    }).filter((level, index) => index === 0 || selectedPath[index - 1]),
    [categoryNodes, selectedPath],
  );

  const selectedItems = useMemo(
    () => items.filter((item) => (quantities[item.utxo.utxo_id] ?? 0) > 0),
    [items, quantities],
  );

  function updateQuantity(item: TokenCardItem, delta: number): void {
    setQuantities((current) => {
      const next = Math.max(0, Math.min(item.utxo.amount, (current[item.utxo.utxo_id] ?? 0) + delta));
      return { ...current, [item.utxo.utxo_id]: next };
    });
  }

  async function handleSend(): Promise<void> {
    const trimmedRecipient = recipient.trim();

    if (!trimmedRecipient) {
      showSendTokenAlert('Recipient required', 'Enter the recipient public key first.');
      return;
    }

    if (selectedItems.length === 0) {
      showSendTokenAlert('Nothing selected', 'Choose at least one token entry before sending.');
      return;
    }

    setIsSending(true);
    try {
      for (const item of selectedItems) {
        await walletService.sendTokenTransfer(item.utxo.utxo_id, quantities[item.utxo.utxo_id] ?? 0, trimmedRecipient);
      }
      router.back();
    } catch (error) {
      showSendTokenAlert('Unable to send tokens', error instanceof Error ? error.message : 'Please try again later.');
    } finally {
      setIsSending(false);
    }
  }

  return (
    <View className="bg-canvas flex-1">
      <ScrollView className="flex-1" contentContainerClassName="px-4 pb-28 pt-14">
        <View className="mb-5 flex-row items-center justify-between gap-3 px-1">
          <View>
            <Text className="text-ink text-[28px] font-bold">Send tokens</Text>
            <Text className="mt-1 text-sm text-slate-500">Choose token entries and transfer them to a recipient.</Text>
          </View>
          <Pressable className="h-10 w-10 items-center justify-center rounded-full bg-slate-100" onPress={() => router.back()}>
            <MaterialIcons color="#0f172a" name="close" size={20} />
          </Pressable>
        </View>

        <View className="mb-5 rounded-[28px] bg-white p-4 shadow-sm">
          <Text className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">Recipient</Text>
          <TextInput
            autoCapitalize="none"
            autoCorrect={false}
            className="border-line text-ink mt-3 rounded-2xl border px-4 py-3"
            onChangeText={setRecipient}
            placeholder="Recipient public key"
            placeholderTextColor="#94a3b8"
            value={recipient}
          />
        </View>

        <View className="mb-5 rounded-[28px] bg-white p-4 shadow-sm">
          <Text className="mb-4 text-xs font-bold uppercase tracking-[0.18em] text-slate-500">Categories</Text>
          <View className="gap-3">
            {tiers.length === 0 ? (
              <Text className="text-sm text-slate-500">No token categories yet. All spendable token entries are shown below.</Text>
            ) : (
              tiers.map((tier, index) => (
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
          {filteredItems.length === 0 ? (
            <View className="rounded-[24px] bg-white px-4 py-10 shadow-sm">
              <Text className="text-center text-sm text-slate-500">No spendable token entries found for this category.</Text>
            </View>
          ) : (
            filteredItems.map((item) => (
              <TokenCard
                item={item}
                key={item.utxo.utxo_id}
                onDecrease={() => updateQuantity(item, -1)}
                onIncrease={() => updateQuantity(item, 1)}
                quantity={quantities[item.utxo.utxo_id] ?? 0}
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
            <MaterialIcons color="#ffffff" name="send" size={18} />
            <Text className="font-semibold text-white">{isSending ? 'Sending...' : 'Send token'}</Text>
          </View>
        </Pressable>
      </View>
    </View>
  );
}
