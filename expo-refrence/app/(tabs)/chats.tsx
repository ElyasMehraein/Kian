import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Image, Pressable, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';

import { initDatabase } from '@/db';
import { conversationsRepo, profilesRepo } from '@/db/repos';
import { chatService } from '@/services';
import { messageEvents } from '@/services/message-events';
import type { Conversation, Profile } from '@/types';

function formatPeer(pubkey: string): string {
  return `${pubkey.slice(0, 8)}...${pubkey.slice(-8)}`;
}

function ConversationRow({
  confirming,
  deleting,
  item,
  onCancelDelete,
  onDelete,
  profile,
}: {
  confirming: boolean;
  deleting: boolean;
  item: Conversation;
  onCancelDelete: () => void;
  onDelete: (peer: string) => void;
  profile: Profile | null;
}) {
  const displayName = profile?.display_name || formatPeer(item.peer_pubkey);

  return (
    <View className="border-line bg-panel rounded-xl border p-4">
      <View className="flex-row items-center">
        <Pressable className="mr-3 flex-1 flex-row items-center gap-3" onPress={() => router.push(`/chat/${item.peer_pubkey}`)}>
          {profile?.picture ? (
            <Image
              className="bg-line h-12 w-12 rounded-full"
              resizeMode="cover"
              source={{ uri: profile.picture }}
            />
          ) : (
            <View className="bg-line h-12 w-12 items-center justify-center rounded-full">
              <Text className="text-ink text-base font-bold">
                {displayName.slice(0, 1).toUpperCase()}
              </Text>
            </View>
          )}
          <View className="flex-1 gap-1">
            <Text className="text-ink text-base font-semibold" numberOfLines={1}>
              {displayName}
            </Text>
            <Text className="text-xs text-slate-500" numberOfLines={1}>
              {formatPeer(item.peer_pubkey)}
            </Text>
            <Text className="text-sm text-slate-500" numberOfLines={1}>
            {item.last_message ?? 'No messages yet'}
            </Text>
          </View>
        </Pressable>
        {item.unread_count > 0 ? (
          <View className="bg-accent mr-2 h-7 min-w-7 items-center justify-center rounded-full px-2">
            <Text className="text-sm font-bold text-white">new</Text>
          </View>
        ) : null}
        <Pressable
          className={deleting
            ? 'rounded-full border border-red-200 bg-red-50 px-3 py-2 opacity-70'
            : 'rounded-full border border-red-200 bg-red-50 px-3 py-2'}
          disabled={deleting}
          onPress={() => onDelete(item.peer_pubkey)}
        >
          {deleting ? (
            <ActivityIndicator color="#b91c1c" size="small" />
          ) : (
            <Text className="text-xs font-semibold text-red-700">
              {confirming ? 'Confirm' : 'Delete'}
            </Text>
          )}
        </Pressable>
      </View>
      {confirming ? (
        <View className="mt-3 flex-row items-center justify-between rounded-2xl border border-red-100 bg-red-50 px-3 py-2.5">
          <Text className="mr-3 flex-1 text-xs leading-5 text-red-700">
            Delete this local conversation and its messages?
          </Text>
          <Pressable className="rounded-full border border-slate-200 bg-white px-3 py-2" onPress={onCancelDelete}>
            <Text className="text-xs font-semibold text-slate-600">Cancel</Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

export default function ChatsScreen() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [profilesByPubkey, setProfilesByPubkey] = useState<Record<string, Profile>>({});
  const [confirmingPeer, setConfirmingPeer] = useState<string | null>(null);
  const [deletingPeer, setDeletingPeer] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const loadConversations = useCallback(async (): Promise<void> => {
    await initDatabase();
    const nextConversations = await conversationsRepo.list();
    const profiles = await profilesRepo.getMany(nextConversations.map((item) => item.peer_pubkey));

    setConversations(nextConversations);
    setProfilesByPubkey(
      Object.fromEntries(profiles.map((profile) => [profile.pubkey, profile])),
    );
  }, []);

  useEffect(() => {
    void loadConversations();
    const unsubscribe = messageEvents.subscribe(() => {
      void loadConversations();
    });

    return unsubscribe;
  }, [loadConversations]);

  useFocusEffect(
    useCallback(() => {
      void loadConversations();
    }, [loadConversations]),
  );

  const deleteConversation = useCallback(async (peer: string) => {
    if (deletingPeer) {
      return;
    }

    setDeletingPeer(peer);
    setDeleteError(null);

    try {
      await initDatabase();
      await chatService.deleteConversation(peer);
      setConfirmingPeer((current) => (current === peer ? null : current));
      setConversations((current) => current.filter((item) => item.peer_pubkey !== peer));
    } catch (error) {
      setDeleteError(
        error instanceof Error ? error.message : 'Unable to delete this conversation.',
      );
    } finally {
      setDeletingPeer((current) => (current === peer ? null : current));
    }
  }, [deletingPeer]);

  const handleDeleteConversation = useCallback((peer: string) => {
    setDeleteError(null);
    if (confirmingPeer === peer) {
      void deleteConversation(peer);
      return;
    }

    setConfirmingPeer(peer);
  }, [confirmingPeer, deleteConversation]);

  return (
    <View className="bg-canvas flex-1 px-5 pt-[72px]">
      <Text className="text-ink mb-4 text-[28px] font-bold">Chats</Text>
      {deleteError ? (
        <View className="mb-3 rounded-2xl border border-red-200 bg-red-50 px-4 py-3">
          <Text className="text-sm text-red-700">{deleteError}</Text>
        </View>
      ) : null}
      <FlatList
        contentContainerClassName="gap-3 pb-6"
        data={conversations}
        keyExtractor={(item) => item.peer_pubkey}
        ListEmptyComponent={<Text className="mt-8 text-center text-base text-slate-500">No conversations yet.</Text>}
        renderItem={({ item }) => (
          <ConversationRow
            confirming={confirmingPeer === item.peer_pubkey}
            deleting={deletingPeer === item.peer_pubkey}
            item={item}
            onCancelDelete={() => setConfirmingPeer((current) => (current === item.peer_pubkey ? null : current))}
            onDelete={handleDeleteConversation}
            profile={profilesByPubkey[item.peer_pubkey] ?? null}
          />
        )}
      />
    </View>
  );
}
