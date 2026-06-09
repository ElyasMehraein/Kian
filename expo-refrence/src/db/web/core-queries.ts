import { DB_VERSION } from '@/types';

import { persistState, type WebDbState, type WebQueryResult } from './state';

export function handleCoreQueries(
  normalized: string,
  state: WebDbState,
  params: unknown[],
): WebQueryResult | null {
  if (
    normalized.startsWith('pragma journal_mode')
    || normalized.startsWith('pragma foreign_keys')
  ) {
    return { rows: [] };
  }

  if (normalized === 'pragma user_version') {
    return { rows: [{ user_version: state.userVersion }] };
  }

  if (normalized.startsWith('pragma user_version =')) {
    const version = Number(normalized.split('=').pop()?.trim() ?? DB_VERSION);
    state.userVersion = Number.isFinite(version) ? version : DB_VERSION;
    persistState(state);
    return { rows: [] };
  }

  if (normalized === 'delete from keys') {
    state.keys = [];
    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('insert into keys')) {
    const [pubkey, npub, createdAt] = params as [string, string, number];
    state.keys = [{ pubkey, npub, created_at: createdAt }];
    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('select pubkey from keys')) {
    const key = state.keys[0];
    return { rows: key ? [{ pubkey: key.pubkey }] : [] };
  }

  if (normalized.includes('delete from dm_inbox_relays where pubkey = ?')) {
    const [pubkey] = params as [string];
    state.dmInboxRelays = state.dmInboxRelays.filter((entry) => entry.pubkey !== pubkey);
    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('insert or replace into dm_inbox_relays')) {
    const [pubkey, relayUrl, createdAt] = params as [string, string, number];
    const existingIndex = state.dmInboxRelays.findIndex(
      (entry) => entry.pubkey === pubkey && entry.relay_url === relayUrl,
    );
    const nextEntry = { pubkey, relay_url: relayUrl, created_at: createdAt };

    if (existingIndex >= 0) {
      state.dmInboxRelays[existingIndex] = nextEntry;
    } else {
      state.dmInboxRelays.push(nextEntry);
    }

    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('select relay_url from dm_inbox_relays where pubkey = ? order by relay_url asc')) {
    const [pubkey] = params as [string];
    const rows = state.dmInboxRelays
      .filter((entry) => entry.pubkey === pubkey)
      .sort((left, right) => left.relay_url.localeCompare(right.relay_url))
      .map((entry) => ({ relay_url: entry.relay_url }));

    return { rows };
  }

  if (normalized.includes('select pubkey, relay_url, created_at from dm_inbox_relays order by pubkey asc, relay_url asc')) {
    const rows = [...state.dmInboxRelays].sort(
      (left, right) => left.pubkey.localeCompare(right.pubkey) || left.relay_url.localeCompare(right.relay_url),
    );

    return { rows };
  }

  if (normalized.includes('insert or ignore into message_receipts')) {
    const [messageId, receiptType, createdAt] = params as [string, string, number];
    const exists = state.messageReceipts.some((entry) => entry.message_id === messageId && entry.receipt_type === receiptType);

    if (!exists) {
      state.messageReceipts.push({
        message_id: messageId,
        receipt_type: receiptType,
        created_at: createdAt,
      });
      persistState(state);
    }

    return { rows: [] };
  }

  if (normalized.includes('select receipt_type from message_receipts where message_id = ?')) {
    const [messageId] = params as [string];
    const rows = state.messageReceipts
      .filter((entry) => entry.message_id === messageId)
      .map((entry) => ({ receipt_type: entry.receipt_type }));

    return { rows };
  }


  if (normalized.includes('insert or replace into offline_queue')) {
    const [eventId, cborPayload, relayUrls, queueScope, peerPubkey, createdAt] = params as [string, unknown, string, string, string | null, number];
    const existingIndex = state.offlineQueue.findIndex((entry) => entry.event_id === eventId);
    const nextEntry = {
      event_id: eventId,
      cbor_payload: cborPayload,
      relay_urls: relayUrls,
      queue_scope: queueScope,
      peer_pubkey: peerPubkey,
      created_at: createdAt,
      retry_count: existingIndex >= 0 ? state.offlineQueue[existingIndex].retry_count : 0,
    };

    if (existingIndex >= 0) {
      state.offlineQueue[existingIndex] = nextEntry;
    } else {
      state.offlineQueue.push(nextEntry);
    }

    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('delete from offline_queue where event_id = ?')) {
    const [eventId] = params as [string];
    state.offlineQueue = state.offlineQueue.filter((entry) => entry.event_id !== eventId);
    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('select event_id, cbor_payload, relay_urls, queue_scope, peer_pubkey, created_at from offline_queue order by created_at desc')) {
    const rows = [...state.offlineQueue].sort((left, right) => right.created_at - left.created_at);
    return { rows };
  }

  return null;
}
