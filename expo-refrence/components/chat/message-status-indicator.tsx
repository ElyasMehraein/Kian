import { Text, View } from 'react-native';

import type { MessageStatus } from '@/types';

const STATUS_META: Record<MessageStatus, { label: string; color: string }> = {
  sending: { label: '● sending', color: '#9ca3af' },
  sent: { label: '✓ sent', color: '#2563eb' },
  delivered: { label: '✓✓ delivered', color: '#7c3aed' },
  read: { label: '✓✓ read', color: '#16a34a' },
} as const;

export function MessageStatusIndicator({ own = false, status }: { own?: boolean; status: MessageStatus }) {
  const meta = STATUS_META[status];

  return (
    <View>
      <Text className="text-[11px] font-semibold" style={{ color: own ? '#ffffffcc' : meta.color }}>
        {meta.label}
      </Text>
    </View>
  );
}
