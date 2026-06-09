import { decode, encode } from 'cbor-x';
import { base64 } from '@scure/base';

import { buildDeleteEvent } from '@/builders';
import { pubkeyFromPrivkey, signEvent } from '@/crypto';
import { getDatabase } from '@/db/init';
import { RelayPool } from '@/nostr/relayPool';
import { createOfflineSmsMessage, extractOfflineSmsPayload } from '@/services/offline-handoff';
import type { ChatMessage, NostrEvent } from '@/types';

const relayPool = new RelayPool();
let isConnected = false;
const RELAY_CONNECT_TIMEOUT_MS = 1500;

type QueueScope = 'generic' | 'private_message_peer_copy' | 'private_message_self_copy' | 'private_receipt';

type QueueMetadata = {
  scope?: QueueScope;
  peerPubkey?: string;
};

type QueuedOfflineEvent = {
  eventId: string;
  event: NostrEvent;
  relayUrls: string[];
  scope: QueueScope;
  peerPubkey: string | null;
  createdAt: number;
};

function isPrivateQueueScope(scope: QueueScope): boolean {
  return scope === 'private_message_peer_copy'
    || scope === 'private_message_self_copy'
    || scope === 'private_receipt';
}

async function ensureConnected(): Promise<void> {
  if (isConnected) {
    return;
  }

  await relayPool.connect();
  isConnected = true;
}

async function ensureRelayConnections(relayUrls?: string[]): Promise<void> {
  if (!relayUrls || relayUrls.length === 0) {
    await ensureConnected();
    return;
  }

  await relayPool.connect(relayUrls);
}

function hasConnectedRelay(relayUrls?: string[]): boolean {
  return relayPool.getRelayStates(relayUrls).some((state) => state.connected);
}

async function waitForConnectedRelay(
  relayUrls?: string[],
  timeoutMs = RELAY_CONNECT_TIMEOUT_MS,
): Promise<boolean> {
  if (hasConnectedRelay(relayUrls)) {
    return true;
  }

  return new Promise<boolean>((resolve) => {
    const timeout = setTimeout(() => {
      unsubscribe();
      resolve(hasConnectedRelay(relayUrls));
    }, timeoutMs);
    const unsubscribe = relayPool.onStateChange((states) => {
      const matchingStates = relayUrls?.length
        ? states.filter((state) => relayUrls.includes(state.url))
        : states;

      if (matchingStates.some((state) => state.connected)) {
        clearTimeout(timeout);
        unsubscribe();
        resolve(true);
      }
    });
  });
}

function toBytes(value: unknown): Uint8Array | null {
  if (value instanceof Uint8Array) {
    return value;
  }

  if (ArrayBuffer.isView(value)) {
    return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
  }

  if (value instanceof ArrayBuffer) {
    return new Uint8Array(value);
  }

  return null;
}

function normalizeRelayUrls(relayUrls?: string[]): string[] {
  if (!relayUrls) {
    return [];
  }

  return [...new Set(relayUrls.filter((relayUrl) => relayUrl.trim().length > 0))];
}

function parseRelayUrls(raw: unknown): string[] {
  if (typeof raw !== 'string') {
    return [];
  }

  try {
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed)
      ? normalizeRelayUrls(parsed.filter((value): value is string => typeof value === 'string'))
      : [];
  } catch {
    return [];
  }
}

async function queueOfflineEvent(
  event: NostrEvent,
  relayUrls?: string[],
  metadata?: QueueMetadata,
): Promise<void> {
  const normalizedRelayUrls = normalizeRelayUrls(relayUrls);
  const scope = metadata?.scope ?? 'generic';
  const peerPubkey = metadata?.peerPubkey ?? null;

  await getDatabase().execute(
    `
      INSERT OR REPLACE INTO offline_queue (
        event_id,
        cbor_payload,
        relay_urls,
        queue_scope,
        peer_pubkey,
        created_at
      )
      VALUES (?, ?, ?, ?, ?, ?)
    `,
    [
      event.id,
      encode(event),
      JSON.stringify(normalizedRelayUrls),
      scope,
      peerPubkey,
      event.created_at,
    ],
  );
}

async function removeQueuedEvent(eventId: string): Promise<void> {
  await getDatabase().execute(
    `
      DELETE FROM offline_queue
      WHERE event_id = ?
    `,
    [eventId],
  );
}

function decodeQueuedEvent(payload: unknown): NostrEvent | null {
  const bytes = toBytes(payload);

  try {
    const decoded = bytes ? decode(bytes) : payload;
    const event =
      decoded && typeof decoded === 'object' && 'event' in (decoded as object)
        ? (decoded as { event?: NostrEvent }).event
        : decoded;

    return event
      && typeof event === 'object'
      && typeof (event as { kind?: unknown }).kind === 'number'
      && typeof (event as { content?: unknown }).content === 'string'
      ? (event as NostrEvent)
      : null;
  } catch {
    return null;
  }
}

async function listQueuedEvents(): Promise<QueuedOfflineEvent[]> {
  const result = await getDatabase().execute(
    `
      SELECT event_id, cbor_payload, relay_urls, queue_scope, peer_pubkey, created_at
      FROM offline_queue
      ORDER BY created_at ASC
    `,
  );

  return (result.rows as Record<string, unknown>[])
    .map((row) => {
      const event = decodeQueuedEvent(row.cbor_payload);

      if (!event) {
        return null;
      }

      return {
        eventId: String(row.event_id),
        event,
        relayUrls: parseRelayUrls(row.relay_urls),
        scope: isPrivateQueueScope(
          row.queue_scope === 'private_message_peer_copy'
          || row.queue_scope === 'private_message_self_copy'
          || row.queue_scope === 'private_receipt'
            ? row.queue_scope
            : 'generic',
        )
          ? row.queue_scope as QueueScope
          : 'generic',
        peerPubkey: typeof row.peer_pubkey === 'string' ? row.peer_pubkey : null,
        createdAt: Number(row.created_at ?? event.created_at ?? 0),
      } satisfies QueuedOfflineEvent;
    })
    .filter((item): item is QueuedOfflineEvent => item !== null)
    .sort((left, right) => left.createdAt - right.createdAt);
}

async function publishNow(event: NostrEvent, relayUrls?: string[]): Promise<void> {
  if (relayUrls && relayUrls.length === 0) {
    throw new Error('No target relays available');
  }

  await ensureRelayConnections(relayUrls);

  if (!(await waitForConnectedRelay(relayUrls))) {
    throw new Error('No connected relay');
  }

  relayPool.publish(event, relayUrls);
}

export async function flushQueuedEvents(options?: {
  relayUrls?: string[];
  matches?: (queuedEvent: QueuedOfflineEvent) => boolean;
}): Promise<number> {
  const queuedEvents = await listQueuedEvents();
  let flushed = 0;

  for (const queuedEvent of queuedEvents) {
    if (options?.matches && !options.matches(queuedEvent)) {
      continue;
    }

    const relayUrls = normalizeRelayUrls(options?.relayUrls ?? queuedEvent.relayUrls);

    if (relayUrls.length === 0) {
      continue;
    }

    try {
      await publishNow(queuedEvent.event, relayUrls);
      await removeQueuedEvent(queuedEvent.eventId);
      flushed += 1;
    } catch {
      break;
    }
  }

  return flushed;
}

export async function publishOrQueue(
  event: NostrEvent,
  relayUrls?: string[],
  metadata?: QueueMetadata,
): Promise<boolean> {
  try {
    await publishNow(event, relayUrls);
    return true;
  } catch {
    await queueOfflineEvent(event, relayUrls, metadata);
    return false;
  }
}

export async function publishRelayDelete(eventId: string, privkey: string): Promise<void> {
  const pubkey = pubkeyFromPrivkey(privkey);
  const deletion = signEvent(buildDeleteEvent([eventId], pubkey), privkey);
  await publishOrQueue(deletion);
}

export async function deleteLocalRelayCopies(messages: ChatMessage[]): Promise<void> {
  for (const message of messages) {
    if (!message.transport_event_id || !message.transport_privkey) {
      continue;
    }

    await publishRelayDelete(message.transport_event_id, message.transport_privkey);
  }
}

export async function encodeOfflinePayload(eventId: string): Promise<string | null> {
  const result = await getDatabase().execute(
    `
      SELECT cbor_payload
      FROM offline_queue
      WHERE event_id = ?
      LIMIT 1
    `,
    [eventId],
  );
  const bytes = toBytes(result.rows[0]?.cbor_payload);
  return bytes ? base64.encode(bytes) : null;
}

export function createOfflineSmsPayload(payload: string): string {
  return createOfflineSmsMessage(payload);
}

export function decodeOfflinePayload(payload: string): NostrEvent | null {
  try {
    return decode(base64.decode(extractOfflineSmsPayload(payload))) as NostrEvent;
  } catch {
    return null;
  }
}
