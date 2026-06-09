import { Image, Pressable, Text, View } from 'react-native';

import type { Profile } from '@/types';

type MetadataProfile = Profile & {
  is_trader?: boolean;
};

type MenuLinkProps = {
  destructive?: boolean;
  label: string;
  onPress: () => void;
};

export function MenuLink({ destructive, label, onPress }: MenuLinkProps) {
  return (
    <Pressable className="border-line border-b py-3.5" onPress={onPress}>
      <Text className={destructive ? 'text-danger text-base font-semibold' : 'text-ink text-base font-semibold'}>
        {label}
      </Text>
    </Pressable>
  );
}

export function ProfileLink({
  profile,
  resolvedPubkey,
  onPress,
}: {
  profile: MetadataProfile | null;
  resolvedPubkey: string | null;
  onPress: () => void;
}) {
  return (
    <Pressable
      className="border-line mb-1 flex-row items-center gap-3 border-b pb-4"
      disabled={!resolvedPubkey}
      onPress={onPress}
    >
      {profile?.picture ? (
        <Image className="bg-line h-12 w-12 rounded-full" resizeMode="cover" source={{ uri: profile.picture }} />
      ) : (
        <View className="bg-panel border-line h-12 w-12 items-center justify-center rounded-full border">
          <Text className="text-ink text-lg font-bold">
            {(profile?.display_name || resolvedPubkey || '?').slice(0, 1).toUpperCase()}
          </Text>
        </View>
      )}
      <View className="flex-1">
        <Text className="text-ink text-base font-semibold">{profile?.display_name || 'Your profile'}</Text>
        <Text className="text-muted mt-1 text-xs" numberOfLines={1}>
          {resolvedPubkey || 'Missing pubkey'}
        </Text>
      </View>
    </Pressable>
  );
}

export function AccountSwitcher({
  accountMode,
  isModeResolved,
  onSelect,
}: {
  accountMode: 'business' | 'merchant';
  isModeResolved: boolean;
  onSelect: (mode: 'business' | 'merchant') => void;
}) {
  return (
    <View className="border-line gap-2.5 border-b py-3.5">
      <Text className="text-ink text-base font-semibold">Account switcher</Text>
      <Text className="text-muted text-sm">
        Active mode: {!isModeResolved ? 'Loading...' : accountMode === 'business' ? 'Business' : 'Merchant'}
      </Text>

      <View className="flex-row gap-2.5">
        {(['business', 'merchant'] as const).map((mode) => {
          const isActive = accountMode === mode;

          return (
            <Pressable
              key={mode}
              className={isActive
                ? 'border-accent bg-accentSoft flex-1 rounded-full border py-2.5'
                : 'border-line bg-panel flex-1 rounded-full border py-2.5'}
              onPress={() => onSelect(mode)}
            >
              <Text className={isActive ? 'text-center font-semibold text-blue-700' : 'text-muted text-center font-semibold'}>
                {mode === 'business' ? 'Business' : 'Merchant'}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

export type { MetadataProfile };
