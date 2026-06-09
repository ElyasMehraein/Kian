import { decode } from 'cbor-x';

import { getDatabase } from '@/db/init';
import { messagesRepo, utxoRepo } from '@/db/repos';
import { EVENT_KIND } from '@/types';

import type { PendingTokenConfirmation } from './types';

type QueuedEvent = {
  kind?: number;
  tags?: string[][];
  content?: string;
  created_at?: number;
  event?: QueuedEvent;
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

function decodeQueuedEvent(payload: unknown): QueuedEvent | null {
  try {
    const bytes = toBytes(payload);
    const decoded = bytes ? decode(bytes) : payload;
    const event =
      decoded && typeof decoded === 'object' && 'event' in (decoded as object)
        ? (decoded as QueuedEvent).event
        : decoded;

    return event && typeof event === 'object' ? (event as QueuedEvent) : null;
  } catch {
    return null;
  }
}

function parseTransferContent(content: string | undefined): {
  utxo_id?: string;
  amount?: number;
  to?: string;
  recipient?: string;
  owner?: string;
} | null {
  if (!content) {
    return null;
  }

  try {
    return JSON.parse(content) as {
      utxo_id?: string;
      amount?: number;
      to?: string;
      recipient?: string;
      owner?: string;
    };
  } catch {
    return null;
  }
}

async function getOfflinePendingConfirmations(pubkey: string): Promise<PendingTokenConfirmation[]> {
  const db = getDatabase();
  const result = await db.execute(
    `
      SELECT event_id, cbor_payload, relay_urls, queue_scope, peer_pubkey, created_at
      FROM offline_queue
      ORDER BY created_at DESC
    `,
  );
  const pending: PendingTokenConfirmation[] = [];

  for (const row of result.rows as Record<string, unknown>[]) {
    const queued = decodeQueuedEvent(row.cbor_payload);

    if (queued?.kind !== EVENT_KIND.TRANSFER_REQUEST) {
      continue;
    }

    const content = parseTransferContent(queued.content);
    const utxoId = content?.utxo_id ?? queued.tags?.find(([name]) => name === 'e')?.[1];
    const recipient = content?.to ?? content?.recipient ?? content?.owner ?? queued.tags?.find(([name]) => name === 'p')?.[1];

    if (!utxoId || !recipient) {
      continue;
    }

    const utxo = await utxoRepo.get(utxoId);

    if (!utxo || utxo.owner !== pubkey) {
      continue;
    }

    const amount = Number(content?.amount ?? utxo.amount);

    if (!Number.isInteger(amount) || amount <= 0) {
      continue;
    }

    pending.push({
      event_id: String(row.event_id),
      utxo_id: utxoId,
      asset_ref: utxo.asset_ref,
      amount,
      recipient,
      created_at: Number(row.created_at ?? queued.created_at ?? 0),
      status: 'offline',
    });
  }

  return pending;
}


export async function getPendingConfirmations(pubkey: string): Promise<PendingTokenConfirmation[]> {
  const [offlinePending, transferMessages] = await Promise.all([
    getOfflinePendingConfirmations(pubkey),
    messagesRepo.listTokenTransfers(),
  ]);

  const messagePending: PendingTokenConfirmation[] = [];

  for (const message of transferMessages) {
    if (!(message.sender === pubkey || message.transfer_counterparty === pubkey || message.conversation_pubkey === pubkey || message.transfer_recipient === pubkey)) {
      continue;
    }

    try {
      const parsed = JSON.parse(message.content) as { utxo_id?: string; asset_ref?: string; amount?: number; recipient?: string };

      if (!parsed.utxo_id || !parsed.asset_ref || !Number.isInteger(parsed.amount) || !parsed.recipient) {
        continue;
      }

      messagePending.push({
        event_id: message.id,
        message_id: message.id,
        utxo_id: parsed.utxo_id,
        asset_ref: parsed.asset_ref,
        amount: Number(parsed.amount),
        recipient: parsed.recipient,
        created_at: message.created_at,
        status: (message.request_status === 'fulfilled'
          ? 'fulfilled'
          : message.request_status === 'rejected'
            ? 'rejected'
            : 'waiting_mint') as 'waiting_mint' | 'fulfilled' | 'rejected',
      });
    } catch {
      continue;
    }
  }

  const deduped = new Map<string, PendingTokenConfirmation>();
  const statusRank = {
    fulfilled: 3,
    rejected: 2,
    waiting_mint: 1,
    offline: 0,
  } as const;

  for (const item of [...offlinePending, ...messagePending]) {
    const transferKey = `${item.utxo_id}:${item.recipient}:${item.amount}`;
    const existing = deduped.get(transferKey);

    if (!existing) {
      deduped.set(transferKey, item);
      continue;
    }

    const existingRank = statusRank[existing.status];
    const nextRank = statusRank[item.status];

    if (nextRank > existingRank || (nextRank === existingRank && item.created_at > existing.created_at)) {
      deduped.set(transferKey, item);
    }
  }

  return [...deduped.values()].sort((left, right) => right.created_at - left.created_at);
}
