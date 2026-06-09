import { buildDmRelayList } from '@/builders';
import { signEvent } from '@/crypto';
import { dmInboxRelaysRepo, keysRepo } from '@/db/repos';
import { publishOrQueue } from '@/services/chat-transport';
import { DEFAULT_RELAYS } from '@/types';

function normalizeRelayUrl(url: string): string | null {
  const trimmed = url.trim();

  if (!trimmed.startsWith('ws://') && !trimmed.startsWith('wss://')) {
    return null;
  }

  return trimmed;
}

type RelayListener = (relayUrls: string[]) => void;

const inboxRelays = new Map<string, string[]>();
const listeners = new Map<string, Set<RelayListener>>();

function dedupeRelayUrls(relayUrls: string[]): string[] {
  const normalized = relayUrls
    .map(normalizeRelayUrl)
    .filter((url): url is string => Boolean(url));

  return [...new Set(normalized)];
}

function areRelayListsEqual(nextRelayUrls: string[], prevRelayUrls: string[]): boolean {
  return nextRelayUrls.length === prevRelayUrls.length
    && nextRelayUrls.every((relayUrl, index) => relayUrl === prevRelayUrls[index]);
}

function notify(pubkey: string, relayUrls: string[]): void {
  const pubkeyListeners = listeners.get(pubkey);

  if (!pubkeyListeners) {
    return;
  }

  for (const listener of pubkeyListeners) {
    listener(relayUrls);
  }
}

export function getInboxRelayUrls(pubkey: string): string[] {
  return inboxRelays.get(pubkey) ?? [];
}

export function getPublishRelayUrls(pubkey: string): string[] {
  return getInboxRelayUrls(pubkey);
}

const DM_RELAY_WAIT_TIMEOUT_MS = 3000;

export async function waitForInboxRelayUrls(
  pubkey: string,
  timeoutMs = DM_RELAY_WAIT_TIMEOUT_MS,
): Promise<string[]> {
  const currentRelayUrls = getInboxRelayUrls(pubkey);

  if (currentRelayUrls.length > 0) {
    return currentRelayUrls;
  }

  return new Promise<string[]>((resolve) => {
    const timeout = setTimeout(() => {
      unsubscribe();
      resolve(getInboxRelayUrls(pubkey));
    }, timeoutMs);

    const unsubscribe = onInboxRelayUrls(pubkey, (relayUrls) => {
      if (relayUrls.length === 0) {
        return;
      }

      clearTimeout(timeout);
      unsubscribe();
      resolve(relayUrls);
    });
  });
}

function applyInboxRelayUrls(pubkey: string, relayUrls: string[]): void {
  const nextRelayUrls = dedupeRelayUrls(relayUrls);
  const prevRelayUrls = inboxRelays.get(pubkey) ?? [];

  if (areRelayListsEqual(nextRelayUrls, prevRelayUrls)) {
    return;
  }

  inboxRelays.set(pubkey, nextRelayUrls);
  notify(pubkey, nextRelayUrls);
}

export async function hydrateInboxRelayUrls(): Promise<void> {
  const rows = await dmInboxRelaysRepo.listAll();
  const groupedRelayUrls = new Map<string, string[]>();

  for (const row of rows) {
    const relayUrls = groupedRelayUrls.get(row.pubkey) ?? [];
    relayUrls.push(row.relay_url);
    groupedRelayUrls.set(row.pubkey, relayUrls);
  }

  for (const [pubkey, relayUrls] of groupedRelayUrls.entries()) {
    applyInboxRelayUrls(pubkey, relayUrls);
  }
}

export async function setInboxRelayUrls(pubkey: string, relayUrls: string[]): Promise<void> {
  const nextRelayUrls = dedupeRelayUrls(relayUrls);

  applyInboxRelayUrls(pubkey, nextRelayUrls);
  await dmInboxRelaysRepo.replace(pubkey, nextRelayUrls);
}

export async function publishOwnInboxRelayList(relayUrls: string[] = DEFAULT_RELAYS): Promise<void> {
  const [pubkey, privkey] = await Promise.all([
    keysRepo.getPublicKey(),
    keysRepo.getPrivateKey(),
  ]);

  if (!pubkey || !privkey) {
    throw new Error('Sender keys are unavailable');
  }

  const nextRelayUrls = dedupeRelayUrls(relayUrls);

  await setInboxRelayUrls(pubkey, nextRelayUrls);
  await publishOrQueue(signEvent(buildDmRelayList(pubkey, nextRelayUrls), privkey), nextRelayUrls);
}

export function onInboxRelayUrls(pubkey: string, listener: RelayListener): () => void {
  const pubkeyListeners = listeners.get(pubkey) ?? new Set<RelayListener>();
  pubkeyListeners.add(listener);
  listeners.set(pubkey, pubkeyListeners);

  const currentRelayUrls = inboxRelays.get(pubkey);
  if (currentRelayUrls) {
    listener(currentRelayUrls);
  }

  return () => {
    const currentListeners = listeners.get(pubkey);
    if (!currentListeners) {
      return;
    }

    currentListeners.delete(listener);
    if (currentListeners.size === 0) {
      listeners.delete(pubkey);
    }
  };
}


export function resetInboxRelayState(): void {
  inboxRelays.clear();
  listeners.clear();
}
