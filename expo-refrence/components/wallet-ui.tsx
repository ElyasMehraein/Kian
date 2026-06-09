import { Image, Pressable, Text, TextInput, View } from 'react-native';

import type { Product, TokenUtxo } from '@/types';

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

function formatCreatedAt(createdAt: number): string {
  return new Date(createdAt * 1000).toLocaleString([], {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function BalanceRow({
  amount,
  description,
  formatAssetRef,
  images,
  name,
  onPressProducer,
  producer,
  categories,
  unit,
}: BalanceItem & {
  formatAssetRef: (value: string) => string;
  onPressProducer: (pubkey: string) => void;
}) {
  return (
    <View className="border-line bg-panel rounded-xl border p-3.5">
      {images[0] ? (
        <Image className="bg-line mb-3 h-32 w-full rounded-xl" resizeMode="cover" source={{ uri: images[0] }} />
      ) : null}
      <Text className="text-ink text-[15px] font-semibold">{name}</Text>
      <Text className="mt-1 text-sm leading-5 text-slate-600">{description || 'No description'}</Text>
      {categories.length > 0 ? (
        <View className="mt-2 flex-row flex-wrap gap-2">
          {categories.map((category) => (
            <View className="rounded-full bg-slate-100 px-2.5 py-1" key={category}>
              <Text className="text-xs font-semibold text-slate-600">{category}</Text>
            </View>
          ))}
        </View>
      ) : null}
      <View className="mt-2 flex-row items-center gap-2">
        <Text className="text-[22px] font-bold text-slate-900">{amount}</Text>
        <Text className="text-sm font-semibold text-slate-500">{unit}</Text>
      </View>
      <Pressable className="mt-2 self-start rounded-full bg-sky-100 px-3 py-2" onPress={() => onPressProducer(producer)}>
        <Text className="font-semibold text-sky-800">Producer: {formatAssetRef(producer)}</Text>
      </Pressable>
    </View>
  );
}

export function UtxoRow({ item, label, formatAssetRef }: { item: TokenUtxo; label: string; formatAssetRef: (value: string) => string }) {
  return (
    <View className="border-line rounded-xl border p-3.5">
      <Text className="text-ink text-sm font-semibold">{label}</Text>
      <Text className="mt-1 text-sm text-slate-500">{item.amount} • {formatAssetRef(item.asset_ref)}</Text>
      <Text className="mt-1 text-xs text-slate-500">Issued {formatCreatedAt(item.created_at)}</Text>
    </View>
  );
}

export function PendingRow({ item, formatAssetRef }: { item: PendingItem; formatAssetRef: (value: string) => string }) {
  const tone = item.status === 'fulfilled'
    ? {
      container: 'rounded-xl border border-emerald-500 bg-emerald-50 p-3.5',
      meta: 'mt-1 text-xs text-emerald-800',
      label: 'Completed',
      detail: 'completed after issuer confirmation',
    }
    : item.status === 'rejected'
      ? {
        container: 'rounded-xl border border-rose-400 bg-rose-50 p-3.5',
        meta: 'mt-1 text-xs text-rose-800',
        label: 'Rejected',
        detail: 'rejected because another transfer was approved',
      }
      : item.status === 'offline'
        ? {
          container: 'rounded-xl border border-slate-400 bg-slate-50 p-3.5',
          meta: 'mt-1 text-xs text-slate-700',
          label: 'Queued offline',
          detail: 'queued until a relay connection is available',
        }
        : {
          container: 'rounded-xl border border-amber-500 bg-amber-50 p-3.5',
          meta: 'mt-1 text-xs text-amber-800',
          label: 'Waiting for issuer',
          detail: 'waiting for token issuer confirmation',
        };

  return (
    <View className={tone.container}>
      <Text className="text-ink text-sm font-semibold">{formatAssetRef(item.assetRef)}</Text>
      <Text className="mt-1 text-sm text-slate-500">{item.amount} {tone.detail}</Text>
      <Text className={tone.meta}>{tone.label}</Text>
      <Text className={tone.meta}>Recipient: {formatAssetRef(item.recipient)}</Text>
      <Text className={tone.meta}>Activity id: {formatAssetRef(item.eventId)}</Text>
    </View>
  );
}

export function MintCard({
  disabled,
  draft,
  item,
  onChange,
  onMint,
}: {
  disabled: boolean;
  draft: string;
  item: Product;
  onChange: (value: string) => void;
  onMint: () => void;
}) {
  return (
    <View className="border-line bg-panel rounded-xl border p-3.5">
      <Text className="text-ink text-[15px] font-semibold">{item.name}</Text>
      <Text className="mt-1 text-sm text-slate-500">{item.description || 'Mint product-backed tokens.'}</Text>
      <View className="mt-2.5 flex-row items-center gap-2.5">
        <TextInput className="border-line flex-1 rounded-xl border px-3 py-2.5" keyboardType="numeric" onChangeText={onChange} value={draft} />
        <Pressable className={disabled ? 'bg-ink rounded-xl px-3.5 py-2.5 opacity-50' : 'bg-ink rounded-xl px-3.5 py-2.5'} disabled={disabled} onPress={onMint}>
          <Text className="font-semibold text-white">{disabled ? 'Minting...' : 'Mint'}</Text>
        </Pressable>
      </View>
    </View>
  );
}

export function SelectorChip({ active, label, onPress }: { active: boolean; label: string; onPress: () => void }) {
  return (
    <Pressable className={active ? 'border-accent bg-accentSoft rounded-full border px-3 py-2' : 'border-line rounded-full border bg-white px-3 py-2'} onPress={onPress}>
      <Text className={active ? 'font-bold text-blue-700' : 'font-semibold text-slate-600'}>{label}</Text>
    </Pressable>
  );
}
