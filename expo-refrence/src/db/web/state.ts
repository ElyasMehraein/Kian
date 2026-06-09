import { DB_VERSION } from '@/types';

type WebQueryResult = { rows: Record<string, unknown>[] };

type WebDbState = {
  userVersion: number;
  keys: Array<{ pubkey: string; npub: string; created_at: number }>;
  profiles: Array<{
    pubkey: string;
    name: string | null;
    display_name: string | null;
    about: string | null;
    picture: string | null;
    nip05: string | null;
    geohash: string | null;
    raw_json: string;
    created_at: number;
    updated_at: number;
  }>;
  products: Array<{
    id: string;
    pubkey: string;
    name: string;
    description: string;
    images: string;
    categories: string;
    geohash: string | null;
    event_id: string;
    created_at: number;
  }>;
  productCategories: Array<{
    id: string;
    pubkey: string;
    name: string;
    parent_id: string | null;
    level: number;
    created_at: number;
  }>;
  tokenDefinitions: Array<{
    asset_id: string;
    pubkey: string;
    product_id: string | null;
    name: string;
    description: string;
    images: string;
    categories: string;
    unit: string;
    event_id: string;
    created_at: number;
  }>;
  tokenUtxos: Array<{
    utxo_id: string;
    asset_ref: string;
    producer: string;
    owner: string;
    amount: number;
    prev_utxo_id: string | null;
    created_at: number;
    spent: number;
  }>;
  conversations: Array<{
    peer_pubkey: string;
    last_message: string | null;
    last_message_at: number | null;
    unread_count: number;
  }>;
  messages: Array<{
    id: string;
    conversation_pubkey: string;
    sender: string;
    content: string;
    message_type: string;
    created_at: number;
    status: string;
    raw_json: string | null;
  }>;
  messageReceipts: Array<{
    message_id: string;
    receipt_type: string;
    created_at: number;
  }>;
  dmInboxRelays: Array<{
    pubkey: string;
    relay_url: string;
    created_at: number;
  }>;
  offlineQueue: Array<{
    event_id: string;
    cbor_payload: unknown;
    relay_urls: string;
    queue_scope: string;
    peer_pubkey: string | null;
    created_at: number;
    retry_count: number;
  }>;
};

const STORAGE_KEY = 'kian:web-db';

function createEmptyState(): WebDbState {
  return {
    userVersion: DB_VERSION,
    keys: [],
    profiles: [],
    products: [],
    productCategories: [],
    tokenDefinitions: [],
    tokenUtxos: [],
    conversations: [],
    messages: [],
    messageReceipts: [],
    dmInboxRelays: [],
    offlineQueue: [],
  };
}

function loadState(): WebDbState {
  if (typeof localStorage === 'undefined') {
    return createEmptyState();
  }

  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return createEmptyState();
    }

    const parsed = JSON.parse(raw) as Partial<WebDbState>;
    return {
      userVersion: typeof parsed.userVersion === 'number' ? parsed.userVersion : DB_VERSION,
      keys: Array.isArray(parsed.keys) ? parsed.keys : [],
      profiles: Array.isArray(parsed.profiles) ? parsed.profiles : [],
      products: Array.isArray(parsed.products) ? parsed.products : [],
      productCategories: Array.isArray(parsed.productCategories) ? parsed.productCategories : [],
      tokenDefinitions: Array.isArray(parsed.tokenDefinitions) ? parsed.tokenDefinitions : [],
      tokenUtxos: Array.isArray(parsed.tokenUtxos) ? parsed.tokenUtxos : [],
      conversations: Array.isArray(parsed.conversations) ? parsed.conversations : [],
      messages: Array.isArray(parsed.messages) ? parsed.messages : [],
      messageReceipts: Array.isArray(parsed.messageReceipts) ? parsed.messageReceipts : [],
      dmInboxRelays: Array.isArray(parsed.dmInboxRelays) ? parsed.dmInboxRelays : [],
      offlineQueue: Array.isArray(parsed.offlineQueue) ? parsed.offlineQueue : [],
    };
  } catch {
    return createEmptyState();
  }
}

function persistState(state: WebDbState): void {
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }
}

function normalizeQuery(query: string): string {
  return query.replace(/\s+/g, ' ').trim().toLowerCase();
}

export {
  createEmptyState,
  loadState,
  normalizeQuery,
  persistState,
  type WebDbState,
  type WebQueryResult,
};
