import { useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, Text, View } from 'react-native';

import { initDatabase } from '@/db';
import { getDatabase } from '@/db/init';
import type { RelayState } from '@/nostr/relayPool';
import { chatService, subscriptionService } from '@/services';
import { useUIStore } from '@/stores';

type QueuedItem = {
  eventId: string;
  payload: string | null;
};

function RelayRow({ item }: { item: RelayState }) {
  return (
    <View className="border-line bg-panel mb-2.5 rounded-xl border p-3.5">
      <Text className="text-ink text-sm">{item.url}</Text>
      <Text className="text-accent mt-1.5 font-semibold">{item.status}</Text>
      {item.lastError ? <Text className="mt-1.5 text-[13px] text-slate-500">Last error: {item.lastError}</Text> : null}
      {item.reconnectAt ? <Text className="mt-1.5 text-[13px] text-slate-500">Reconnecting at {new Date(item.reconnectAt).toLocaleTimeString()}</Text> : null}
    </View>
  );
}

export default function RelayStatusScreen() {
  const isRelayConnected = useUIStore((state) => state.isRelayConnected);
  const errorMessage = useUIStore((state) => state.errorMessage);
  const [relayStates, setRelayStates] = useState<RelayState[]>([]);
  const [queuedItems, setQueuedItems] = useState<QueuedItem[]>([]);
  const [sendingEventId, setSendingEventId] = useState<string | null>(null);

  useEffect(() => {
    async function loadScreen(): Promise<void> {
      await initDatabase();
      setRelayStates(subscriptionService.getRelayStates());
      const result = await getDatabase().execute('SELECT event_id FROM offline_queue ORDER BY created_at DESC');
      const rows = result.rows as Record<string, unknown>[];
      setQueuedItems(await Promise.all(rows.map(async (row) => ({ eventId: String(row.event_id), payload: await chatService.encodeOfflinePayload(String(row.event_id)) }))));
    }

    void loadScreen();
  }, [isRelayConnected, errorMessage]);

  async function handleSendQueuedItem(eventId: string): Promise<void> {
    if (sendingEventId) {
      return;
    }

    setSendingEventId(eventId);
    try {
      const result = await chatService.sendOfflinePayloadViaSms(eventId);
      Alert.alert(result === 'cancelled' ? 'SMS cancelled' : 'SMS handoff opened', result === 'cancelled' ? 'The offline handoff was cancelled before sending.' : 'The queued payload was handed off to the SMS composer.');
    } catch (error) {
      Alert.alert('SMS unavailable', error instanceof Error ? error.message : 'Unable to open SMS handoff.');
    } finally {
      setSendingEventId(null);
    }
  }

  return (
    <ScrollView className="bg-canvas" contentContainerClassName="px-5 pb-8 pt-14">
      <Text className="text-ink text-[28px] font-bold">Relay status</Text>
      <Text className="text-ink mt-2.5 text-lg font-semibold">{isRelayConnected ? 'Connected' : 'Not connected'}</Text>
      {errorMessage ? <Text className="mt-2 text-sm text-red-600">{errorMessage}</Text> : null}

      <Pressable
        className="bg-ink mt-3.5 self-start rounded-xl px-3.5 py-2.5"
        onPress={() => void subscriptionService.connectRelays().then(() => setRelayStates(subscriptionService.getRelayStates()))}
      >
        <Text className="font-semibold text-white">Reconnect relays</Text>
      </Pressable>

      <Text className="text-ink mb-3 mt-6 text-lg font-semibold">Per-relay status</Text>
      {relayStates.map((item) => <RelayRow item={item} key={item.url} />)}

      <Text className="text-ink mb-3 mt-6 text-lg font-semibold">Queued offline events</Text>
      {queuedItems.length === 0 ? <Text className="text-[15px] text-slate-500">No queued offline events.</Text> : queuedItems.map((item) => (
        <View className="border-line bg-panel mb-2.5 rounded-xl border p-3.5" key={item.eventId}>
          <Text className="text-ink text-sm">{item.eventId}</Text>
          <Text className="mt-1.5 text-[13px] text-slate-500">Use this payload for SMS/manual transfer:</Text>
          <Text className="mt-2 text-xs text-amber-800" selectable>{item.payload ?? 'Payload unavailable'}</Text>
          <Pressable
            className={!item.payload || sendingEventId === item.eventId ? 'mt-2.5 self-start rounded-full bg-amber-800 px-3 py-2 opacity-50' : 'mt-2.5 self-start rounded-full bg-amber-800 px-3 py-2'}
            disabled={!item.payload || sendingEventId === item.eventId}
            onPress={() => void handleSendQueuedItem(item.eventId)}
          >
            <Text className="text-xs font-semibold text-white">{sendingEventId === item.eventId ? 'Opening SMS...' : 'Send via SMS'}</Text>
          </Pressable>
        </View>
      ))}
    </ScrollView>
  );
}
