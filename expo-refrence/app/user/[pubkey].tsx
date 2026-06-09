import { MaterialIcons } from '@expo/vector-icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { FlatList, Image, Pressable, Text, View } from 'react-native';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';

import { initDatabase } from '@/db';
import { keysRepo, productsRepo, profilesRepo, reviewsRepo } from '@/db/repos';
import { productEvents, subscriptionService } from '@/services';
import { useCartStore, useSessionStore } from '@/stores';
import type { CartItem, Product, Profile, ReviewEntry } from '@/types';

function ProductCard({
  isSelected,
  item,
  onToggleCart,
}: {
  isSelected: boolean;
  item: Product;
  onToggleCart: (product: Product) => void;
}) {
  return (
    <View className="border-line bg-panel mb-2.5 rounded-xl border p-3.5">
      {item.images[0] ? <Image className="bg-line mb-3 h-[180px] w-full rounded-xl" resizeMode="cover" source={{ uri: item.images[0] }} /> : null}
      <Text className="text-ink text-base font-semibold">{item.name}</Text>
      <Text className="mt-1.5 text-sm leading-5 text-slate-600">{item.description || 'No description'}</Text>
      <Pressable
        className={`mt-3 self-start rounded-xl px-3.5 py-2.5 ${isSelected ? 'bg-emerald-100' : 'bg-ink'}`}
        onPress={() => onToggleCart(item)}
      >
        <View className="flex-row items-center gap-2">
          <MaterialIcons color={isSelected ? '#047857' : '#ffffff'} name={isSelected ? 'check-circle' : 'add-shopping-cart'} size={18} />
          <Text className={`font-semibold ${isSelected ? 'text-emerald-800' : 'text-white'}`}>{isSelected ? 'Added to cart' : 'Add to cart'}</Text>
        </View>
      </Pressable>
    </View>
  );
}

function ReviewCard({ item }: { item: ReviewEntry }) {
  return (
    <View className="border-line bg-panel mb-2.5 rounded-xl border p-3.5">
      <Text className="text-ink text-base font-semibold">Rating: {item.rating}/5</Text>
      <Text className="mt-1.5 text-sm leading-5 text-slate-600">{item.comment || 'No review text'}</Text>
    </View>
  );
}

export default function UserProfileScreen() {
  const params = useLocalSearchParams<{ pubkey?: string | string[] }>();
  const pubkey = useMemo(() => {
    const value = params.pubkey;
    return Array.isArray(value) ? value[0] ?? '' : value ?? '';
  }, [params.pubkey]);
  const addItem = useCartStore((state) => state.addItem);
  const removeItem = useCartStore((state) => state.removeItem);
  const cartItems = useCartStore((state) => state.items);
  const hydrateCart = useCartStore((state) => state.hydrate);
  const sessionPubkey = useSessionStore((state) => state.pubkey);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [products, setProducts] = useState<Product[]>([]);
  const [reviews, setReviews] = useState<ReviewEntry[]>([]);
  const [resolvedOwnPubkey, setResolvedOwnPubkey] = useState<string | null>(sessionPubkey);
  const isOwnProfile = resolvedOwnPubkey === pubkey;
  const hasCartItems = cartItems.length > 0;
  const selectedProductIds = useMemo(() => new Set(cartItems.map((item: CartItem) => item.product.id)), [cartItems]);

  const loadProfile = useCallback(async (): Promise<void> => {
    if (!pubkey) {
      return;
    }

    await initDatabase();
    const [currentPubkey, nextProfile, nextProducts, nextReviews] = await Promise.all([
      sessionPubkey ? Promise.resolve(sessionPubkey) : keysRepo.getPublicKey(),
      profilesRepo.get(pubkey),
      productsRepo.getByProducer(pubkey),
      reviewsRepo.getForTarget(pubkey),
    ]);
    setResolvedOwnPubkey(currentPubkey);
    setProfile(nextProfile);
    setProducts(nextProducts);
    setReviews(nextReviews);
  }, [pubkey, sessionPubkey]);

  useEffect(() => {
    hydrateCart();
  }, [hydrateCart]);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  useEffect(() => {
    if (!pubkey) {
      return;
    }

    subscriptionService.subscribeProducts([pubkey]);
    const unsubscribe = productEvents.subscribe(() => {
      void loadProfile();
    });

    return unsubscribe;
  }, [loadProfile, pubkey]);

  useFocusEffect(
    useCallback(() => {
      hydrateCart();
      void loadProfile();
    }, [hydrateCart, loadProfile]),
  );

  return (
    <FlatList
      className="bg-canvas flex-1"
      contentContainerClassName="px-5 pb-8 pt-14"
      data={reviews}
      keyExtractor={(item) => `${item.pubkey}-${item.created_at}`}
      ListHeaderComponent={
        <View>
          {profile?.picture ? <Image className="bg-line mb-3 h-[88px] w-[88px] rounded-full" resizeMode="cover" source={{ uri: profile.picture }} /> : null}
          <Text className="text-ink text-[28px] font-bold">{profile?.display_name || 'User Profile'}</Text>
          <Text className="mt-1.5 text-[13px] text-slate-500">{pubkey || 'Missing pubkey'}</Text>
          <Text className="mt-3 text-[15px] leading-6 text-slate-700">{profile?.about || 'No bio yet.'}</Text>
          <View className="mt-4 flex-row flex-wrap gap-2.5">
            {!isOwnProfile ? (
              <Pressable className="bg-ink self-start rounded-xl px-3.5 py-2.5" onPress={() => router.push(`/chat/${pubkey}`)}>
                <Text className="font-semibold text-white">Start messaging</Text>
              </Pressable>
            ) : null}
            {!isOwnProfile ? (
              <Pressable className="bg-ink self-start rounded-xl px-3.5 py-2.5" onPress={() => router.push(`/review/${pubkey}`)}>
                <Text className="font-semibold text-white">Write review</Text>
              </Pressable>
            ) : null}
            {isOwnProfile ? (
              <Pressable className="self-start rounded-xl bg-sky-100 px-3.5 py-2.5" onPress={() => router.push('/profile/manage')}>
                <Text className="font-semibold text-sky-800">Edit profile</Text>
              </Pressable>
            ) : null}
          </View>
          <View className="mb-3 mt-6 flex-row items-center justify-between">
            <Text className="text-ink text-lg font-semibold">Products</Text>
            {hasCartItems ? (
              <Pressable className="flex-row items-center gap-1.5 rounded-full bg-emerald-100 px-3 py-2" onPress={() => router.push('/cart')}>
                <MaterialIcons color="#047857" name="shopping-cart" size={18} />
                <Text className="font-semibold text-emerald-800">{cartItems.length}</Text>
              </Pressable>
            ) : null}
          </View>
          {products.length === 0 ? (
            <Text className="mb-2 text-[15px] text-slate-500">No products yet.</Text>
          ) : (
            products.map((item) => (
              <ProductCard
                isSelected={selectedProductIds.has(item.id)}
                item={item}
                key={`${item.pubkey}-${item.id}`}
                onToggleCart={(product) => {
                  if (selectedProductIds.has(product.id)) {
                    removeItem(product.id);
                    return;
                  }
                  if (!selectedProductIds.has(product.id)) {
                    addItem(product, 1);
                  }
                }}
              />
            ))
          )}
          <Text className="text-ink mb-3 mt-6 text-lg font-semibold">Reviews</Text>
          {reviews.length === 0 ? <Text className="mb-2 text-[15px] text-slate-500">No reviews yet.</Text> : null}
        </View>
      }
      renderItem={({ item }) => <ReviewCard item={item} />}
    />
  );
}
