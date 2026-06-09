import { decode } from 'cbor-x';

import { getDatabase } from '@/db/init';
import { flushQueuedEvents } from '@/services/chat-transport';
import type { NostrEvent } from '@/types';

export type PendingEventCategory =
  | 'Direct Messages'
  | 'Receipts & Status Updates'
  | 'Token Transfers'
  | 'Profiles & Social'
  | 'Products & Reviews'
  | 'Other Events';

export type PendingEventItem = {
  eventId: string;
  kind: number;
  createdAt: number;
  category: PendingEventCategory;
  preview: string;
  rawJson: string;
  event: NostrEvent;
};

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

function getCategory(kind: number): PendingEventCategory {
  if ([4, 14].includes(kind)) {
    return 'Direct Messages';
  }

  if ([20001, 20002, 20003, 20004, 20005, 20006, 20007, 20008].includes(kind)) {
    return 'Receipts & Status Updates';
  }

  if ([1050, 35001, 35002].includes(kind)) {
    return 'Token Transfers';
  }

  if ([0, 3, 10050].includes(kind)) {
    return 'Profiles & Social';
  }

  if ([30018, 31999].includes(kind)) {
    return 'Products & Reviews';
  }

  return 'Other Events';
}

function getPreview(event: NostrEvent | null, decodeState: 'decoded' | 'raw-bytes'): string {
  if (!event) {
    return decodeState === 'raw-bytes'
      ? 'Queued payload could not be decoded into a Nostr event yet'
      : 'Unknown queued event';
  }

  if (event.kind in {4: 1, 14: 1, 1059: 1}) {
    return 'Encrypted payload waiting to be broadcasted';
  }

  const content = typeof event.content === 'string' ? event.content : '';

  if (content.trim()) {
    return content.length > 88 ? `${content.slice(0, 88)}...` : content;
  }

  return `Event kind ${event.kind}`;
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

export async function listPendingEvents(): Promise<PendingEventItem[]> {
  const result = await getDatabase().execute(
    `
      SELECT event_id, cbor_payload, relay_urls, queue_scope, peer_pubkey, created_at
      FROM offline_queue
      ORDER BY created_at DESC
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
        kind: event.kind,
        createdAt: Number(row.created_at ?? event.created_at ?? 0),
        category: getCategory(event.kind),
        preview: getPreview(event, 'decoded'),
        rawJson: JSON.stringify(event, null, 2),
        event,
      } satisfies PendingEventItem;
    })
    .filter((item): item is PendingEventItem => item !== null);
}

export async function retryPendingEvent(eventId: string): Promise<boolean> {
  const events = await listPendingEvents();
  const item = events.find((entry) => entry.eventId === eventId);

  if (!item) {
    return false;
  }

  const sent = await flushQueuedEvents({
    matches: (queuedEvent) => queuedEvent.eventId === eventId,
  });

  return sent > 0;
}

export async function retryAllPendingEvents(): Promise<{ sent: number; total: number }> {
  const events = await listPendingEvents();
  const sent = await flushQueuedEvents();

  return { sent, total: events.length };
}

export async function deletePendingEvent(eventId: string): Promise<void> {
  await removeQueuedEvent(eventId);
}

export const pendingEventsService = {
  listPendingEvents,
  retryPendingEvent,
  retryAllPendingEvents,
  deletePendingEvent,
};
