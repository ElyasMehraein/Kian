import { Image, Pressable, Text, TextInput, View } from 'react-native';

import { MessageStatusIndicator } from './message-status-indicator';
import type { ChatMessage, ProductRequest, ProductSend, TokenDefinition, TokenTransfer } from '@/types';

function formatMessageTime(createdAt: number): string {
  return new Date(createdAt * 1000).toLocaleTimeString([], {
    hour: 'numeric',
    minute: '2-digit',
  });
}

function parseProductRequest(content: string): ProductRequest | null {
  try {
    const parsed = JSON.parse(content) as ProductRequest & { content?: string };

    if (typeof parsed.content === 'string') {
      const nested = JSON.parse(parsed.content) as ProductRequest;

      return typeof nested.product_id === 'string' && Number.isInteger(nested.quantity)
        ? nested
        : null;
    }

    return typeof parsed.product_id === 'string' && Number.isInteger(parsed.quantity)
      ? parsed
      : null;
  } catch {
    return null;
  }
}


function parseTokenTransfer(content: string): TokenTransfer | null {
  try {
    const parsed = JSON.parse(content) as TokenTransfer & { content?: string };

    if (typeof parsed.content === 'string') {
      const nested = JSON.parse(parsed.content) as TokenTransfer;

      return typeof nested.utxo_id === 'string'
        && typeof nested.asset_ref === 'string'
        && Number.isInteger(nested.amount)
        && typeof nested.sender === 'string'
        && typeof nested.recipient === 'string'
        ? nested
        : null;
    }

    return typeof parsed.utxo_id === 'string'
      && typeof parsed.asset_ref === 'string'
      && Number.isInteger(parsed.amount)
      && typeof parsed.sender === 'string'
      && typeof parsed.recipient === 'string'
      ? parsed
      : null;
  } catch {
    return null;
  }
}

function parseProductSend(content: string): ProductSend | null {
  try {
    const parsed = JSON.parse(content) as ProductSend & { content?: string };

    if (typeof parsed.content === 'string') {
      const nested = JSON.parse(parsed.content) as ProductSend;

      return typeof nested.product_id === 'string'
        && typeof nested.product_name === 'string'
        && typeof nested.product_description === 'string'
        && Array.isArray(nested.product_images)
        && Array.isArray(nested.product_categories)
        && Number.isInteger(nested.quantity)
        && Array.isArray(nested.utxo_ids)
        && typeof nested.asset_ref === 'string'
        ? nested
        : null;
    }

    return typeof parsed.product_id === 'string'
      && typeof parsed.product_name === 'string'
      && typeof parsed.product_description === 'string'
      && Array.isArray(parsed.product_images)
      && Array.isArray(parsed.product_categories)
      && Number.isInteger(parsed.quantity)
      && Array.isArray(parsed.utxo_ids)
      && typeof parsed.asset_ref === 'string'
      ? parsed
      : null;
  } catch {
    return null;
  }
}

export function Bubble({
  item,
  onDeclineProductRequest,
  onOpenProductRequest,
  onOpenProfile,
  peerPubkey,
  tokenDefinition,
}: {
  item: ChatMessage;
  onDeclineProductRequest?: (messageId: string, request: ProductRequest) => void;
  onOpenProductRequest?: (messageId: string, request: ProductRequest) => void;
  onOpenProfile?: (pubkey: string) => void;
  peerPubkey: string;
  tokenDefinition?: TokenDefinition | null;
}) {
  const isOwn = item.sender !== peerPubkey;
  const productRequest =
    item.message_type === 'product_request' ? parseProductRequest(item.content) : null;
  const productSend =
    item.message_type === 'product_send' ? parseProductSend(item.content) : null;
  const tokenTransfer =
    item.message_type === 'token_transfer' ? parseTokenTransfer(item.content) : null;
  const requestStatus = item.request_status ?? 'open';

  if (productRequest) {
    return (
      <View className={isOwn ? 'items-end' : 'items-start'}>
        <View
          className={isOwn
            ? 'max-w-[84%] rounded-[22px] rounded-br-md bg-accent px-4 py-3'
            : 'border-line max-w-[84%] rounded-[22px] rounded-bl-md border bg-white px-4 py-3'}
        >
          <Text className={isOwn ? 'text-[11px] font-bold uppercase tracking-[0.18em] text-white/80' : 'text-[11px] font-bold uppercase tracking-[0.18em] text-slate-500'}>
            Product request
          </Text>
          <Text className={isOwn ? 'mt-2 text-base font-semibold text-white' : 'text-ink mt-2 text-base font-semibold'}>
            {productRequest.product_name || 'Requested product'}
          </Text>
          <Text className={isOwn ? 'mt-1 text-sm leading-6 text-white/90' : 'mt-1 text-sm leading-6 text-slate-600'}>
            Quantity: {productRequest.quantity}
          </Text>
          {requestStatus === 'declined' ? (
            <View className="mt-3 self-start rounded-full bg-rose-100 px-3.5 py-2">
              <Text className="font-semibold text-rose-800">Declined</Text>
            </View>
          ) : null}
          {requestStatus === 'fulfilled' ? (
            <View className="mt-3 self-start rounded-full bg-emerald-100 px-3.5 py-2">
              <Text className="font-semibold text-emerald-800">Fulfilled</Text>
            </View>
          ) : null}
          {requestStatus === 'waiting_mint' ? (
            <View className="mt-3 self-start rounded-2xl bg-amber-100 px-3.5 py-2">
              <Text className="font-semibold text-amber-800">Waiting for token mint by the product producer</Text>
            </View>
          ) : null}
          {!isOwn && requestStatus === 'open' ? (
            <View className="mt-3 flex-row flex-wrap gap-2">
              <Pressable
                className="self-start rounded-full bg-emerald-100 px-3.5 py-2.5"
                onPress={() => onOpenProductRequest?.(item.id, productRequest)}
              >
                <Text className="font-semibold text-emerald-800">Fulfill</Text>
              </Pressable>
              <Pressable
                className="self-start rounded-full bg-rose-100 px-3.5 py-2.5"
                onPress={() => onDeclineProductRequest?.(item.id, productRequest)}
              >
                <Text className="font-semibold text-rose-800">Decline</Text>
              </Pressable>
            </View>
          ) : null}
          <View className={isOwn ? 'mt-2 flex-row items-center justify-end gap-2' : 'mt-2 flex-row items-center gap-2'}>
            <Text className={isOwn ? 'text-[11px] text-white/80' : 'text-[11px] text-slate-500'}>
              {formatMessageTime(item.created_at)}
            </Text>
            {isOwn ? <MessageStatusIndicator own status={item.status} /> : null}
          </View>
        </View>
      </View>
    );
  }

  if (tokenTransfer) {
    return (
      <View className={isOwn ? 'items-end' : 'items-start'}>
        <View
          className={isOwn
            ? 'max-w-[84%] rounded-[22px] rounded-br-md bg-accent px-4 py-3'
            : 'border-line max-w-[84%] rounded-[22px] rounded-bl-md border bg-white px-4 py-3'}
        >
          <Text className={isOwn ? 'text-[11px] font-bold uppercase tracking-[0.18em] text-white/80' : 'text-[11px] font-bold uppercase tracking-[0.18em] text-slate-500'}>
            Token transfer
          </Text>
          {tokenDefinition?.images[0] ? (
            <Image className="mt-3 h-36 w-full rounded-2xl" resizeMode="cover" source={{ uri: tokenDefinition.images[0] }} />
          ) : null}
          {tokenDefinition?.name ? (
            <Text className={isOwn ? 'mt-2 text-base font-semibold text-white' : 'text-ink mt-2 text-base font-semibold'}>
              {tokenDefinition.name}
            </Text>
          ) : null}
          {tokenDefinition?.description ? (
            <Text className={isOwn ? 'mt-1 text-sm leading-6 text-white/90' : 'mt-1 text-sm leading-6 text-slate-600'}>
              {tokenDefinition.description}
            </Text>
          ) : null}
          {tokenDefinition?.categories.length ? (
            <View className="mt-2 flex-row flex-wrap gap-2">
              {tokenDefinition.categories.map((category) => (
                <View className={isOwn ? 'rounded-full bg-white/15 px-2.5 py-1' : 'rounded-full bg-slate-100 px-2.5 py-1'} key={category}>
                  <Text className={isOwn ? 'text-xs font-semibold text-white/90' : 'text-xs font-semibold text-slate-600'}>
                    {category}
                  </Text>
                </View>
              ))}
            </View>
          ) : null}
          <Text className={isOwn ? 'mt-2 text-sm leading-6 text-white/90' : 'mt-2 text-sm leading-6 text-slate-600'}>
            Quantity: {tokenTransfer.amount} {tokenDefinition?.unit || 'unit'}
          </Text>
          {requestStatus === 'rejected' ? (
            <View className="mt-3 self-start rounded-full bg-rose-100 px-3.5 py-2">
              <Text className="font-semibold text-rose-800">Rejected by token issuer</Text>
            </View>
          ) : null}
          {requestStatus === 'fulfilled' ? (
            <View className="mt-3 self-start rounded-full bg-emerald-100 px-3.5 py-2">
              <Text className="font-semibold text-emerald-800">Transfer completed</Text>
            </View>
          ) : null}
          {requestStatus === 'waiting_mint' ? (
            <Pressable
              className="mt-3 self-start rounded-2xl bg-amber-100 px-3.5 py-2"
              disabled={!tokenDefinition?.pubkey}
              onPress={() => {
                if (tokenDefinition?.pubkey) {
                  onOpenProfile?.(tokenDefinition.pubkey);
                }
              }}
            >
              <Text className="font-semibold text-amber-800">Waiting for token issuer confirmation</Text>
            </Pressable>
          ) : null}
          <View className={isOwn ? 'mt-2 flex-row items-center justify-end gap-2' : 'mt-2 flex-row items-center gap-2'}>
            <Text className={isOwn ? 'text-[11px] text-white/80' : 'text-[11px] text-slate-500'}>
              {formatMessageTime(item.created_at)}
            </Text>
            {isOwn ? <MessageStatusIndicator own status={item.status} /> : null}
          </View>
        </View>
      </View>
    );
  }

  if (productSend) {
    return (
      <View className={isOwn ? 'items-end' : 'items-start'}>
        <View
          className={isOwn
            ? 'max-w-[84%] rounded-[22px] rounded-br-md bg-accent px-4 py-3'
            : 'border-line max-w-[84%] rounded-[22px] rounded-bl-md border bg-white px-4 py-3'}
        >
          <Text className={isOwn ? 'text-[11px] font-bold uppercase tracking-[0.18em] text-white/80' : 'text-[11px] font-bold uppercase tracking-[0.18em] text-slate-500'}>
            Product sent
          </Text>
          {productSend.product_images[0] ? (
            <Image className="mt-3 h-36 w-full rounded-2xl" resizeMode="cover" source={{ uri: productSend.product_images[0] }} />
          ) : null}
          <Text className={isOwn ? 'mt-2 text-base font-semibold text-white' : 'text-ink mt-2 text-base font-semibold'}>
            {productSend.product_name || 'Product'}
          </Text>
          <Text className={isOwn ? 'mt-1 text-sm leading-6 text-white/90' : 'mt-1 text-sm leading-6 text-slate-600'}>
            {productSend.product_description || 'No description'}
          </Text>
          {productSend.product_categories.length > 0 ? (
            <View className="mt-2 flex-row flex-wrap gap-2">
              {productSend.product_categories.map((category) => (
                <View className={isOwn ? 'rounded-full bg-white/15 px-2.5 py-1' : 'rounded-full bg-slate-100 px-2.5 py-1'} key={category}>
                  <Text className={isOwn ? 'text-xs font-semibold text-white/90' : 'text-xs font-semibold text-slate-600'}>{category}</Text>
                </View>
              ))}
            </View>
          ) : null}
          <Text className={isOwn ? 'mt-1 text-sm leading-6 text-white/90' : 'mt-1 text-sm leading-6 text-slate-600'}>
            Quantity: {productSend.quantity}
          </Text>
          <Text className={isOwn ? 'mt-1 text-sm leading-6 text-white/90' : 'mt-1 text-sm leading-6 text-slate-600'}>
            Minted and sent as product-backed token.
          </Text>
          <View className={isOwn ? 'mt-2 flex-row items-center justify-end gap-2' : 'mt-2 flex-row items-center gap-2'}>
            <Text className={isOwn ? 'text-[11px] text-white/80' : 'text-[11px] text-slate-500'}>
              {formatMessageTime(item.created_at)}
            </Text>
            {isOwn ? <MessageStatusIndicator own status={item.status} /> : null}
          </View>
        </View>
      </View>
    );
  }

  return (
    <View className={isOwn ? 'items-end' : 'items-start'}>
      <View
        className={isOwn
          ? 'max-w-[84%] rounded-[22px] rounded-br-md bg-accent px-4 py-3'
          : 'border-line max-w-[84%] rounded-[22px] rounded-bl-md border bg-white px-4 py-3'}
      >
        <Text className={isOwn ? 'text-sm leading-6 text-white' : 'text-ink text-sm leading-6'}>
          {item.content}
        </Text>
        <View className={isOwn ? 'mt-2 flex-row items-center justify-end gap-2' : 'mt-2 flex-row items-center gap-2'}>
          <Text className={isOwn ? 'text-[11px] text-white/80' : 'text-[11px] text-slate-500'}>
            {formatMessageTime(item.created_at)}
          </Text>
          {isOwn ? <MessageStatusIndicator own status={item.status} /> : null}
        </View>
      </View>
    </View>
  );
}

export function QuantityPicker({ onDecrease, onIncrease, value }: { onDecrease: () => void; onIncrease: () => void; value: number }) {
  return (
    <View className="mt-3 flex-row items-center gap-3">
      <Pressable className="bg-ink h-8 w-8 items-center justify-center rounded-full" onPress={onDecrease}>
        <Text className="text-lg font-bold text-white">-</Text>
      </Pressable>
      <Text className="text-ink min-w-6 text-center text-base font-semibold">{value}</Text>
      <Pressable className="bg-ink h-8 w-8 items-center justify-center rounded-full" onPress={onIncrease}>
        <Text className="text-lg font-bold text-white">+</Text>
      </Pressable>
    </View>
  );
}

export function Composer({
  accessoryBefore,
  accessoryAfter,
  disabled,
  text,
  onChangeText,
  onSend,
}: {
  accessoryBefore?: React.ReactNode;
  accessoryAfter?: React.ReactNode;
  disabled?: boolean;
  text: string;
  onChangeText: (value: string) => void;
  onSend: () => void;
}) {
  return (
    <View className="border-line bg-canvas border-t px-4 pb-5 pt-3">
      <View className="flex-row items-end gap-3">
        <View className="flex-1">
          <TextInput
            className="border-line text-ink max-h-32 min-h-[50px] flex-1 rounded-[24px] border bg-white px-4 py-3 pl-24 pr-4"
            editable={!disabled}
            multiline
            onChangeText={onChangeText}
            onKeyPress={(event) => {
              const nativeEvent = event.nativeEvent as typeof event.nativeEvent & { shiftKey?: boolean };

              if (nativeEvent.key === 'Enter' && !nativeEvent.shiftKey) {
                event.preventDefault?.();
                onSend();
              }
            }}
            placeholder="Write a message"
            placeholderTextColor="#94a3b8"
            style={{ textAlignVertical: 'center' }}
            value={text}
          />
          {accessoryBefore ? (
            <View className="absolute bottom-0 left-3 top-0 flex-row items-center gap-1">
              {accessoryBefore}
            </View>
          ) : null}
          {accessoryAfter ? (
            <View className="absolute bottom-0 right-3 top-0 flex-row items-center gap-2">
              {accessoryAfter}
            </View>
          ) : null}
        </View>
        <Pressable
          className={disabled ? 'bg-ink h-[50px] min-w-[92px] items-center justify-center rounded-[20px] px-4 opacity-50' : 'bg-ink h-[50px] min-w-[92px] items-center justify-center rounded-[20px] px-4'}
          disabled={disabled}
          onPress={onSend}
        >
          <Text className="font-semibold text-white">Send</Text>
        </Pressable>
      </View>
    </View>
  );
}

export function OfflineCard({
  disabled,
  offlinePayload,
  onSendSms,
  pendingText,
  sending,
}: {
  disabled: boolean;
  offlinePayload: string | null;
  onSendSms: () => void;
  pendingText: string | null;
  sending: boolean;
}) {
  return (
    <View className="mb-4 rounded-2xl border border-amber-300 bg-amber-50 p-4">
      <Text className="text-sm font-bold text-amber-800">Relay offline</Text>
      <Text className="mt-1 text-[13px] leading-5 text-amber-900">
        Messages stay local until a relay reconnects or you hand them off manually.
      </Text>
      {pendingText ? (
        <>
          <Text className="mt-3 text-xs font-bold uppercase tracking-[0.18em] text-amber-800">
            Pending message
          </Text>
          <Text className="mt-1 text-[13px] leading-5 text-amber-900">{pendingText}</Text>
          <Text className="mt-3 text-xs font-bold uppercase tracking-[0.18em] text-amber-800">
            SMS payload
          </Text>
          <Text className="mt-1 text-xs leading-5 text-amber-950" selectable>
            {offlinePayload ?? 'Preparing offline payload...'}
          </Text>
          <Pressable
            className={disabled ? 'mt-3 self-start rounded-full bg-amber-800 px-4 py-2.5 opacity-50' : 'mt-3 self-start rounded-full bg-amber-800 px-4 py-2.5'}
            disabled={disabled}
            onPress={onSendSms}
          >
            <Text className="text-xs font-semibold text-white">
              {sending ? 'Opening SMS...' : 'Send via SMS'}
            </Text>
          </Pressable>
        </>
      ) : (
        <Text className="mt-2 text-[13px] text-amber-900">No pending outgoing messages right now.</Text>
      )}
    </View>
  );
}

export function ProductCard({ children, description, title }: { children: React.ReactNode; description: string; title: string }) {
  return (
    <View className="border-line bg-panel mt-2.5 rounded-2xl border p-3.5">
      <Text className="text-ink text-[15px] font-semibold">{title}</Text>
      <Text className="mt-1 text-[13px] leading-5 text-slate-600">{description}</Text>
      {children}
    </View>
  );
}
