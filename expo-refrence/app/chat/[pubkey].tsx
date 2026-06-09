import { useEffect, useMemo, useRef, useState } from 'react';
import { MaterialIcons } from '@expo/vector-icons';
import { Alert, FlatList, Image, Pressable, Text, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';

import { Bubble, Composer, OfflineCard } from '@/components/chat/chat-ui';
import { initDatabase } from '@/db';
import { productCategoriesRepo, productsRepo, profilesRepo, tokenDefinitionsRepo } from '@/db/repos';
import { useChatConversation } from '@/hooks/use-chat-conversation';
import { chatService, subscriptionService, walletService } from '@/services';
import { useCartStore, useSessionStore, useUIStore } from '@/stores';
import type { ProductRequest, Profile, TokenDefinition } from '@/types';

function parseAssetRef(assetRef: string): { producer: string; assetId: string } | null {
  const [kind, producer, assetId] = assetRef.split(':', 3);
  return kind === '35001' && producer && assetId ? { producer, assetId } : null;
}

function getTokenTransferAssetRef(content: string): string | null {
  try {
    const parsed = JSON.parse(content) as { asset_ref?: string; content?: string };
    if (typeof parsed.content === 'string') {
      const nested = JSON.parse(parsed.content) as { asset_ref?: string };
      return nested.asset_ref ?? null;
    }

    return parsed.asset_ref ?? null;
  } catch {
    return null;
  }
}

export default function ChatScreen() {
  const params = useLocalSearchParams<{ pubkey?: string | string[] }>();
  const peer = useMemo(() => {
    const value = params.pubkey;
    return Array.isArray(value) ? value[0] ?? '' : value ?? '';
  }, [params.pubkey]);
  const cartItems = useCartStore((state) => state.items);
  const hydrateCart = useCartStore((state) => state.hydrate);
  const ownPubkey = useSessionStore((state) => state.pubkey);
  const isRelayConnected = useUIStore((state) => state.isRelayConnected);
  const messages = useChatConversation(peer);
  const [peerProfile, setPeerProfile] = useState<Profile | null>(null);
  const [tokenDefinitionsByAssetRef, setTokenDefinitionsByAssetRef] = useState<Record<string, TokenDefinition>>({});
  const [text, setText] = useState('');
  const [offlinePayload, setOfflinePayload] = useState<string | null>(null);
  const [isSendingOfflineSms, setIsSendingOfflineSms] = useState(false);
  const messagesListRef = useRef<FlatList<(typeof messages)[number]>>(null);
  const hasCartItems = cartItems.length > 0;
  const pendingMessage = useMemo(() => [...messages].reverse().find((message) => message.sender === ownPubkey && message.status === 'sending') ?? null, [messages, ownPubkey]);

  useEffect(() => {
    hydrateCart();
  }, [hydrateCart]);

  useEffect(() => {
    if (!peer) {
      setPeerProfile(null);
      return;
    }

    async function loadPeerProfile(): Promise<void> {
      await initDatabase();
      setPeerProfile(await profilesRepo.get(peer));
    }

    void loadPeerProfile();
  }, [peer]);

  useEffect(() => {
    if (!peer) {
      return;
    }

    subscriptionService.watchDmRelayList(peer);
  }, [peer]);

  useEffect(() => {
    let isMounted = true;

    async function loadTokenDefinitions(): Promise<void> {
      const transferAssetRefs = Array.from(
        new Set(
          messages
            .filter((message) => message.message_type === 'token_transfer')
            .map((message) => getTokenTransferAssetRef(message.content))
            .filter((assetRef): assetRef is string => Boolean(assetRef)),
        ),
      );

      if (transferAssetRefs.length === 0) {
        if (isMounted) {
          setTokenDefinitionsByAssetRef({});
        }
        return;
      }

      const definitions = await Promise.all(
        transferAssetRefs.map(async (assetRef) => {
          const parsed = parseAssetRef(assetRef);
          if (!parsed) {
            return null;
          }

          const definition = await tokenDefinitionsRepo.get(parsed.assetId, parsed.producer);

          if (definition) {
            return [assetRef, definition] as const;
          }

          const product = await productsRepo.get(parsed.assetId, parsed.producer);

          if (!product) {
            return null;
          }

          const categoryNames = product.categories.length > 0
            ? (await productCategoriesRepo.listByPubkey(parsed.producer))
              .filter((category) => product.categories.includes(category.id))
              .map((category) => category.name)
            : [];

          return [
            assetRef,
            {
              asset_id: parsed.assetId,
              pubkey: parsed.producer,
              product_id: product.id,
              name: product.name,
              description: product.description,
              images: product.images,
              categories: categoryNames,
              unit: 'unit',
              created_at: product.created_at,
            } satisfies TokenDefinition,
          ] as const;
        }),
      );
      const resolvedDefinitions = definitions.filter(
        (entry): entry is readonly [string, TokenDefinition] => entry !== null,
      );

      if (isMounted) {
        setTokenDefinitionsByAssetRef(Object.fromEntries(resolvedDefinitions));
      }
    }

    void loadTokenDefinitions();

    return () => {
      isMounted = false;
    };
  }, [messages]);

  useEffect(() => { if (peer && messages.length > 0) void chatService.markConversationRead(peer, messages); }, [messages, peer]);
  useEffect(() => {
    if (messages.length === 0) {
      return;
    }

    const timer = setTimeout(() => {
      messagesListRef.current?.scrollToEnd({ animated: true });
    }, 0);

    return () => clearTimeout(timer);
  }, [messages]);
  useEffect(() => { if (isRelayConnected || !pendingMessage) { setOfflinePayload(null); return; } void chatService.encodeOfflinePayload(pendingMessage.id).then(setOfflinePayload); }, [isRelayConnected, pendingMessage]);
  const handleSendText = async () => { const value = text.trim(); if (!peer || !value) return; await chatService.sendText(peer, value); setText(''); };

  async function handleSendOfflineSms(): Promise<void> {
    if (!pendingMessage || isSendingOfflineSms) return;
    setIsSendingOfflineSms(true);
    try {
      const result = await chatService.sendOfflinePayloadViaSms(pendingMessage.id);
      Alert.alert(result === 'cancelled' ? 'SMS cancelled' : 'SMS handoff opened', result === 'cancelled' ? 'The offline handoff was cancelled before sending.' : 'The offline payload was handed off to the SMS composer.');
    } catch (error) {
      Alert.alert('SMS unavailable', error instanceof Error ? error.message : 'Unable to open SMS handoff.');
    } finally { setIsSendingOfflineSms(false); }
  }

  async function handleOpenProductRequest(messageId: string, request: ProductRequest): Promise<void> {
    if (!peer) {
      return;
    }

    try {
      const result = await walletService.fulfillProductRequest(peer, request);

      if (result.status === 'fulfilled') {
        await chatService.fulfillProductRequest(peer, messageId);
        return;
      }

      await chatService.markProductRequestWaitingForMint(peer, messageId, result.transferUtxoId);
    } catch (error) {
      Alert.alert(
        'Unable to fulfill request',
        error instanceof Error ? error.message : 'Please try again later.',
      );
    }
  }

  async function handleDeclineProductRequest(messageId: string, _request: ProductRequest): Promise<void> {
    if (!peer) {
      return;
    }

    await chatService.declineProductRequest(peer, messageId);
  }

  return (
    <View className="bg-canvas flex-1">
      <View className="border-line flex-row items-center gap-3 border-b bg-white px-4 pb-4 pt-14">
        <Pressable
          className="flex-1 flex-row items-center gap-3"
          disabled={!peer}
          onPress={() => {
            if (peer) {
              router.push(`/user/${peer}`);
            }
          }}
        >
          {peerProfile?.picture ? (
            <Image className="bg-line h-12 w-12 rounded-full" resizeMode="cover" source={{ uri: peerProfile.picture }} />
          ) : (
            <View className="bg-line h-12 w-12 items-center justify-center rounded-full">
              <Text className="text-ink text-lg font-bold">
                {(peerProfile?.display_name || peer || '?').slice(0, 1).toUpperCase()}
              </Text>
            </View>
          )}
          <View className="flex-1">
            <Text className="text-ink text-xl font-bold">
              {peerProfile?.display_name || 'Unknown user'}
            </Text>
            <Text className="mt-1 text-sm text-slate-500" numberOfLines={1}>
              View profile
            </Text>
          </View>
        </Pressable>

        <Pressable
          className="h-10 w-10 items-center justify-center rounded-full"
          disabled={!hasCartItems}
          onPress={() => {
            if (hasCartItems) {
              router.push('/cart');
            }
          }}
        >
          <MaterialIcons color={hasCartItems ? '#0f766e' : '#64748b'} name="shopping-cart" size={22} />
        </Pressable>
      </View>

      <FlatList
        ref={messagesListRef}
        className="flex-1"
        contentContainerClassName="px-4 pb-6 pt-4"
        data={messages}
        keyExtractor={(item) => item.id}
        ListEmptyComponent={
          <View className="items-center rounded-3xl bg-white px-5 py-10">
            <Text className="text-ink text-lg font-semibold">No messages yet</Text>
            <Text className="mt-2 text-center text-sm leading-6 text-slate-500">
              Start the conversation here. Messages appear as a private thread once sent.
            </Text>
          </View>
        }
        renderItem={({ item }) => (
          <Bubble
            item={item}
            onDeclineProductRequest={peer ? (messageId, request) => { void handleDeclineProductRequest(messageId, request); } : undefined}
            onOpenProductRequest={peer ? (messageId, request) => { void handleOpenProductRequest(messageId, request); } : undefined}
            onOpenProfile={(pubkey) => {
              router.push(`/user/${pubkey}`);
            }}
            peerPubkey={peer}
            tokenDefinition={tokenDefinitionsByAssetRef[getTokenTransferAssetRef(item.content) ?? ''] ?? null}
          />
        )}
        scrollEnabled
        showsVerticalScrollIndicator={false}
        ItemSeparatorComponent={() => <View className="h-2.5" />}
        onContentSizeChange={() => {
          messagesListRef.current?.scrollToEnd({ animated: true });
        }}
      />

      {!isRelayConnected && pendingMessage ? (
        <View className="border-line border-t bg-white px-4 pt-3">
          <OfflineCard
            disabled={!offlinePayload || isSendingOfflineSms}
            offlinePayload={offlinePayload}
            onSendSms={() => void handleSendOfflineSms()}
            pendingText={pendingMessage?.content ?? null}
            sending={isSendingOfflineSms}
          />
        </View>
      ) : null}

      <Composer
        accessoryBefore={(
          <>
            <Pressable
              className="h-9 w-9 items-center justify-center"
              onPress={() => {
                if (peer) {
                  router.push({ pathname: '/tokens/send', params: { recipient: peer } });
                }
              }}
            >
              <MaterialIcons color={peer ? '#64748b' : '#cbd5e1'} name="account-balance-wallet" size={20} />
            </Pressable>
            <Pressable
              className="h-9 w-9 items-center justify-center"
              onPress={() => {
                if (peer) {
                  router.push(`/chat/send-product/${peer}`);
                }
              }}
            >
              <MaterialIcons color={peer ? '#64748b' : '#cbd5e1'} name="local-shipping" size={20} />
            </Pressable>
          </>
        )}
        disabled={!peer}
        onChangeText={setText}
        onSend={() => void handleSendText()}
        text={text}
      />
    </View>
  );
}
