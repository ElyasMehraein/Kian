import { DEFAULT_RELAYS, EVENT_KIND } from '@/types';

import { dispatchEvent } from '@/nostr/dispatcher';
import { RelayPool } from '@/nostr/relayPool';
import { getInboxRelayUrls, onInboxRelayUrls } from '@/services/dm-relays';
import { useUIStore } from '@/stores/ui';

const relayPool = new RelayPool();
let isConnected = false;
let isStateBound = false;
const boundInboxRelayPubkeys = new Set<string>();

function bindRelayState(): void {
  if (isStateBound) {
    return;
  }

  relayPool.onStateChange((states) => {
    const hasConnectedRelay = states.some((state) => state.connected);
    const latestError = [...states]
      .reverse()
      .find((state) => state.lastError)?.lastError ?? null;

    useUIStore.getState().setRelayConnected(hasConnectedRelay);
    useUIStore.getState().setErrorMessage(latestError);
  });
  isStateBound = true;
}

async function ensureConnected(): Promise<void> {
  if (isConnected) {
    return;
  }

  bindRelayState();
  await relayPool.connect();
  isConnected = true;
}

function subscribe(
  filters: Parameters<RelayPool['subscribe']>[0],
  relayUrls?: string[],
): void {
  void ensureConnected().then(() => {
    relayPool.subscribe(filters, (event) => {
      void dispatchEvent(event);
    }, relayUrls);
  });
}

function messageFilters(pubkey: string): { kinds: number[]; '#p': string[] }[] {
  return [
    {
      kinds: [
        EVENT_KIND.GIFT_WRAP,
        EVENT_KIND.RECEIPT_DELIVERED,
        EVENT_KIND.RECEIPT_READ,
      ],
      '#p': [pubkey],
    },
  ];
}

function getAdditionalInboxRelayUrls(relayUrls: string[]): string[] {
  return relayUrls.filter((relayUrl) => !DEFAULT_RELAYS.includes(relayUrl));
}

function bindInboxRelaySubscriptions(pubkey: string): void {
  if (boundInboxRelayPubkeys.has(pubkey)) {
    return;
  }

  onInboxRelayUrls(pubkey, (relayUrls) => {
    const additionalRelayUrls = getAdditionalInboxRelayUrls(relayUrls);

    if (additionalRelayUrls.length === 0) {
      return;
    }

    subscribe(messageFilters(pubkey), additionalRelayUrls);
  });
  boundInboxRelayPubkeys.add(pubkey);
}

export const subscriptionService = {
  async connectRelays(): Promise<void> {
    try {
      await ensureConnected();
      useUIStore.getState().setRelayConnected(true);
      useUIStore.getState().setErrorMessage(null);
    } catch (error) {
      useUIStore.getState().setRelayConnected(false);
      useUIStore.getState().setErrorMessage(
        error instanceof Error ? error.message : 'Relay connection failed',
      );
      throw error;
    }
  },

  getRelayStates() {
    bindRelayState();

    return relayPool.getRelayStates();
  },

  subscribeMessages(pubkey: string): void {
    subscribe(messageFilters(pubkey));

    const inboxRelayUrls = getAdditionalInboxRelayUrls(getInboxRelayUrls(pubkey));
    if (inboxRelayUrls.length > 0) {
      subscribe(messageFilters(pubkey), inboxRelayUrls);
    }
  },

  subscribeDmRelayLists(pubkeys: string[]): void {
    if (pubkeys.length === 0) {
      return;
    }

    subscribe([{ kinds: [EVENT_KIND.DM_RELAY_LIST], authors: pubkeys }]);
  },

  watchDmRelayList(pubkey: string): void {
    this.subscribeDmRelayLists([pubkey]);
  },

  subscribeProducts(pubkeys: string[]): void {
    if (pubkeys.length === 0) {
      return;
    }

    subscribe([{ kinds: [EVENT_KIND.PRODUCT, EVENT_KIND.DELETE], authors: pubkeys }]);
  },

  subscribeTokens(pubkey: string): void {
    subscribe([
      {
        kinds: [
          EVENT_KIND.TOKEN_MINT,
          EVENT_KIND.TOKEN_UTXO,
          EVENT_KIND.TRANSFER_REQUEST,
        ],
        authors: [pubkey],
      },
      {
        kinds: [EVENT_KIND.TOKEN_UTXO, EVENT_KIND.TRANSFER_REQUEST],
        '#p': [pubkey],
      },
    ]);
  },

  subscribeProfiles(pubkeys: string[]): void {
    if (pubkeys.length === 0) {
      return;
    }

    subscribe([{ kinds: [EVENT_KIND.METADATA], authors: pubkeys }]);
  },

  subscribeDiscovery(): void {
    subscribe([
      {
        kinds: [
          EVENT_KIND.METADATA,
          EVENT_KIND.DELETE,
          EVENT_KIND.PRODUCT,
          EVENT_KIND.FOLLOW_LIST,
          EVENT_KIND.REVIEW,
          EVENT_KIND.DM_RELAY_LIST,
        ],
        limit: 200,
      },
    ]);
  },

  subscribeSocialGraph(pubkey: string): void {
    subscribe([
      {
        kinds: [EVENT_KIND.FOLLOW_LIST, EVENT_KIND.REVIEW],
        authors: [pubkey],
      },
      {
        kinds: [EVENT_KIND.REVIEW],
        '#p': [pubkey],
      },
    ]);
  },

  startSubscriptions(pubkey: string): void {
    bindInboxRelaySubscriptions(pubkey);
    this.subscribeDiscovery();
    this.subscribeDmRelayLists([pubkey]);
    this.subscribeMessages(pubkey);
    this.subscribeTokens(pubkey);
    this.subscribeProfiles([pubkey]);
    this.subscribeSocialGraph(pubkey);
  },

  resetSubscriptions(): void {
    boundInboxRelayPubkeys.clear();
  },
};
