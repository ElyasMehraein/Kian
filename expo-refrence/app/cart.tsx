import { useEffect, useState } from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';
import { router } from 'expo-router';

import { chatService } from '@/services';
import { useCartStore } from '@/stores';
import type { CartItem } from '@/types';

function CartRow({ item, onDecrease, onIncrease }: { item: CartItem; onDecrease: () => void; onIncrease: () => void }) {
  return (
    <View className="border-line bg-panel mt-2.5 rounded-xl border p-3.5">
      <Text className="text-ink text-base font-semibold">{item.product.name}</Text>
      <Text className="mt-1 text-xs text-slate-500">{item.product.pubkey}</Text>
      <View className="mt-3 flex-row items-center gap-3">
        <Pressable className="bg-ink h-8 w-8 items-center justify-center rounded-full" onPress={onDecrease}>
          <Text className="text-lg font-bold text-white">-</Text>
        </Pressable>
        <Text className="text-ink min-w-6 text-center text-base font-semibold">{item.quantity}</Text>
        <Pressable className="bg-ink h-8 w-8 items-center justify-center rounded-full" onPress={onIncrease}>
          <Text className="text-lg font-bold text-white">+</Text>
        </Pressable>
      </View>
    </View>
  );
}

type MerchantCartGroup = { merchantPubkey: string; items: CartItem[] };

function groupItemsByMerchant(items: CartItem[]): MerchantCartGroup[] {
  const groups = new Map<string, CartItem[]>();
  for (const item of items) {
    groups.set(item.product.pubkey, [...(groups.get(item.product.pubkey) ?? []), item]);
  }
  return [...groups.entries()].map(([merchantPubkey, merchantItems]) => ({ merchantPubkey, items: merchantItems }));
}

export default function CartScreen() {
  const items = useCartStore((state) => state.items);
  const hydrate = useCartStore((state) => state.hydrate);
  const setQuantity = useCartStore((state) => state.setQuantity);
  const [sendingMerchant, setSendingMerchant] = useState<string | null>(null);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  const merchantGroups = groupItemsByMerchant(items);

  async function handleSendRequest(group: MerchantCartGroup): Promise<void> {
    if (group.items.length === 0 || sendingMerchant) {
      return;
    }
    setSendingMerchant(group.merchantPubkey);
    try {
      for (const item of group.items) {
        await chatService.requestProduct(group.merchantPubkey, { product_id: item.product.id, product_name: item.product.name, quantity: item.quantity, producer_pubkey: group.merchantPubkey });
        setQuantity(item.product.id, 0);
      }
      router.push(`/chat/${group.merchantPubkey}`);
    } finally {
      setSendingMerchant(null);
    }
  }

  return (
    <View className="bg-canvas flex-1 px-5 pt-14">
      <Text className="text-ink mb-4 text-[28px] font-bold">Cart</Text>
      <Text className="mb-3 text-slate-500">Each merchant has a separate request flow.</Text>
      <FlatList
        contentContainerClassName="gap-2.5 pb-6"
        data={merchantGroups}
        keyExtractor={(item) => item.merchantPubkey}
        ListEmptyComponent={<Text className="text-[15px] text-slate-500">No selected products yet.</Text>}
        renderItem={({ item }) => (
          <View className="border-line rounded-xl border bg-white p-3.5">
            <Text className="text-[13px] font-bold text-slate-700">Merchant</Text>
            <Text className="mt-1 mb-2.5 text-xs text-slate-500">{item.merchantPubkey}</Text>
            {item.items.map((cartItem) => (
              <CartRow
                item={cartItem}
                key={`${cartItem.product.pubkey}-${cartItem.product.id}`}
                onDecrease={() => setQuantity(cartItem.product.id, cartItem.quantity - 1)}
                onIncrease={() => setQuantity(cartItem.product.id, cartItem.quantity + 1)}
              />
            ))}
            <Pressable className="bg-accent mt-3 items-center rounded-xl py-3" onPress={() => void handleSendRequest(item)}>
              <Text className="font-semibold text-white">{sendingMerchant === item.merchantPubkey ? 'Sending...' : 'Send to chat'}</Text>
            </Pressable>
          </View>
        )}
      />
    </View>
  );
}
