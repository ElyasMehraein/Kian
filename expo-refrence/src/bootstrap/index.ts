import { initDatabase } from '@/db';
import { conversationsRepo, keysRepo } from '@/db/repos';
import { hydrateInboxRelayUrls, publishOwnInboxRelayList, resetInboxRelayState } from '@/services/dm-relays';
import { subscriptionService } from '@/services';
import { useSessionStore, useUIStore } from '@/stores';

async function loadKeys(): Promise<string | null> {
  return keysRepo.getPublicKey();
}

async function connectRelays(): Promise<void> {
  await subscriptionService.connectRelays();
}

async function startSubscriptions(pubkey: string): Promise<void> {
  subscriptionService.startSubscriptions(pubkey);

  const conversations = await conversationsRepo.list();

  for (const conversation of conversations) {
    if (conversation.peer_pubkey && conversation.peer_pubkey !== pubkey) {
      subscriptionService.watchDmRelayList(conversation.peer_pubkey);
    }
  }
}

export async function bootstrap(): Promise<void> {
  useUIStore.getState().setBootstrapping(true);
  useUIStore.getState().setErrorMessage(null);

  try {
    resetInboxRelayState();
    subscriptionService.resetSubscriptions();
    await initDatabase();
    await hydrateInboxRelayUrls();

    const pubkey = await loadKeys();

    useSessionStore.getState().setPubkey(pubkey);

    await connectRelays();

    if (pubkey) {
      await publishOwnInboxRelayList();
      await startSubscriptions(pubkey);
    }

    useSessionStore.getState().setReady(true);
  } catch (error) {
    useUIStore.getState().setErrorMessage(
      error instanceof Error ? error.message : 'Bootstrap failed',
    );
    useSessionStore.getState().setReady(false);
    throw error;
  } finally {
    useUIStore.getState().setBootstrapping(false);
  }
}
