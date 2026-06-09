import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, Text, View } from 'react-native';

import { initDatabase } from '@/db';
import { pendingEventsService } from '@/services';
import type { PendingEventItem } from '@/services/pending-events';
import { useUIStore } from '@/stores';

const formatEventId = (value: string) => `${value.slice(0, 10)}...${value.slice(-6)}`;

function formatAge(createdAt: number): string {
  const seconds = Math.max(0, Math.floor(Date.now() / 1000) - createdAt);
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

function kindTone(kind: number): string {
  if ([4, 14].includes(kind)) return 'bg-emerald-100 text-emerald-800';
  if ([1050, 35001, 35002].includes(kind)) return 'bg-amber-100 text-amber-800';
  if ([20001, 20002, 20003, 20004, 20005, 20006, 20007, 20008].includes(kind)) return 'bg-sky-100 text-sky-800';
  return 'bg-slate-100 text-slate-700';
}

export default function PendingEventsScreen() {
  const isRelayConnected = useUIStore((state) => state.isRelayConnected);
  const [items, setItems] = useState<PendingEventItem[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [sendingAll, setSendingAll] = useState(false);

  const load = useCallback(async () => {
    await initDatabase();
    setItems(await pendingEventsService.listPendingEvents());
  }, []);

  useEffect(() => {
    void load();
  }, [load, isRelayConnected]);

  const grouped = useMemo(() => {
    const next = new Map<string, PendingEventItem[]>();
    for (const item of items) {
      next.set(item.category, [...(next.get(item.category) ?? []), item]);
    }
    return [...next.entries()];
  }, [items]);

  async function handleRetryOne(eventId: string): Promise<void> {
    if (busyId || sendingAll) return;
    setBusyId(eventId);
    try {
      const sent = await pendingEventsService.retryPendingEvent(eventId);
      await load();
      Alert.alert(
        sent ? 'Event sent' : 'Still queued',
        sent ? 'The event was broadcasted successfully.' : 'The event is still waiting for a relay connection.',
      );
    } catch (error) {
      Alert.alert('Unable to send event', error instanceof Error ? error.message : 'Please try again later.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleRetryAll(): Promise<void> {
    if (sendingAll || busyId) return;
    setSendingAll(true);
    try {
      const result = await pendingEventsService.retryAllPendingEvents();
      await load();
      Alert.alert(
        result.sent === result.total ? 'All pending events broadcasted' : 'Some events remain queued',
        result.total === 0 ? 'There were no pending events to send.' : `${result.sent} of ${result.total} queued events were sent.`,
      );
    } catch (error) {
      Alert.alert('Unable to send all events', error instanceof Error ? error.message : 'Please try again later.');
    } finally {
      setSendingAll(false);
    }
  }

  async function handleDelete(eventId: string): Promise<void> {
    if (busyId || sendingAll) return;
    setBusyId(eventId);
    try {
      await pendingEventsService.deletePendingEvent(eventId);
      if (expandedId === eventId) {
        setExpandedId(null);
      }
      await load();
    } catch (error) {
      Alert.alert('Unable to remove event', error instanceof Error ? error.message : 'Please try again later.');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <ScrollView className="bg-canvas flex-1" contentContainerClassName="px-5 pb-10 pt-14">
      <View className="flex-row items-center justify-between gap-3">
        <View className="flex-1">
          <Text className="text-ink text-[28px] font-bold">Pending events</Text>
          <Text className="mt-2 text-sm leading-6 text-slate-500">
            Events that were not sent and still remain in the local queue.
          </Text>
        </View>
        <View className="rounded-full bg-rose-100 px-3 py-2">
          <Text className="text-xs font-bold uppercase tracking-[0.16em] text-rose-700">
            {isRelayConnected ? 'Relay online' : 'Relay offline'}
          </Text>
        </View>
      </View>

      <Pressable
        className={sendingAll || items.length === 0 ? 'mt-5 self-start rounded-xl bg-sky-700 px-4 py-3 opacity-50' : 'mt-5 self-start rounded-xl bg-sky-700 px-4 py-3'}
        disabled={sendingAll || items.length === 0}
        onPress={() => void handleRetryAll()}
      >
        <Text className="font-semibold text-white">{sendingAll ? 'Sending all...' : 'Send all pending events'}</Text>
      </Pressable>

      {items.length === 0 ? (
        <View className="mt-8 rounded-[28px] border border-dashed border-slate-300 bg-white px-5 py-10">
          <Text className="text-center text-lg font-semibold text-slate-700">Queue is empty</Text>
          <Text className="mt-2 text-center text-sm leading-6 text-slate-500">
            There are no pending events waiting to be broadcasted.
          </Text>
        </View>
      ) : null}

      <View className="mt-8 gap-6">
        {grouped.map(([category, categoryItems]) => (
          <View key={category}>
            <View className="mb-3 flex-row items-center gap-3 border-b border-slate-200 pb-2">
              <Text className="text-ink text-lg font-semibold">{category}</Text>
              <View className="rounded-full bg-slate-200 px-2.5 py-1">
                <Text className="text-xs font-bold text-slate-700">{categoryItems.length}</Text>
              </View>
            </View>

            <View className="gap-3">
              {categoryItems.map((item) => {
                const expanded = expandedId === item.eventId;
                return (
                  <Pressable
                    className="rounded-[24px] border border-slate-200 bg-white p-4"
                    key={item.eventId}
                    onPress={() => setExpandedId(expanded ? null : item.eventId)}
                  >
                    <View className="flex-row items-start gap-3">
                      <View className="flex-1">
                        <View className="flex-row items-center gap-2">
                          <View className={`rounded-full px-2.5 py-1 ${kindTone(item.kind)}`}>
                            <Text className="text-xs font-bold">Kind {item.kind}</Text>
                          </View>
                          <Text className="text-xs font-semibold text-slate-500">{formatAge(item.createdAt)}</Text>
                        </View>
                        <Text className="text-ink mt-2 text-base font-semibold">{item.preview}</Text>
                        <Text className="mt-1 text-xs text-slate-500">Event ID: {formatEventId(item.eventId)}</Text>
                      </View>
                    </View>

                    {expanded ? (
                      <View className="mt-4 border-t border-slate-200 pt-4">
                        <Text className="text-xs font-bold uppercase tracking-[0.16em] text-slate-500">Raw event</Text>
                        <Text className="mt-2 rounded-2xl bg-slate-950 px-4 py-3 font-mono text-xs leading-5 text-slate-100">
                          {item.rawJson}
                        </Text>
                        <View className="mt-4 flex-row flex-wrap gap-2">
                          <Pressable
                            className={busyId === item.eventId ? 'rounded-full bg-sky-700 px-4 py-2.5 opacity-50' : 'rounded-full bg-sky-700 px-4 py-2.5'}
                            disabled={busyId === item.eventId}
                            onPress={() => void handleRetryOne(item.eventId)}
                          >
                            <Text className="text-xs font-semibold text-white">{busyId === item.eventId ? 'Sending...' : 'Send now'}</Text>
                          </Pressable>
                          <Pressable
                            className={busyId === item.eventId ? 'rounded-full bg-rose-100 px-4 py-2.5 opacity-50' : 'rounded-full bg-rose-100 px-4 py-2.5'}
                            disabled={busyId === item.eventId}
                            onPress={() => void handleDelete(item.eventId)}
                          >
                            <Text className="text-xs font-semibold text-rose-700">Remove from queue</Text>
                          </Pressable>
                        </View>
                      </View>
                    ) : null}
                  </Pressable>
                );
              })}
            </View>
          </View>
        ))}
      </View>
    </ScrollView>
  );
}
