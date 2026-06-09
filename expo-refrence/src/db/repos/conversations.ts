import type { Conversation } from '@/types';

import { getDatabase } from '../init';
import { messagesRepo } from './messages';

const DELETED_CONVERSATION_MARKER = '__deleted_conversation__';

function mapRowToConversation(row: Record<string, unknown>): Conversation {
  return {
    peer_pubkey: String(row.peer_pubkey),
    last_message: (row.last_message as string | null | undefined) ?? undefined,
    last_message_at:
      row.last_message_at == null ? undefined : Number(row.last_message_at),
    unread_count: Number(row.unread_count ?? 0),
  };
}

export const conversationsRepo = {
  async upsert(peer: string): Promise<void> {
    const db = getDatabase();

    await db.execute(
      `
        INSERT OR IGNORE INTO conversations (peer_pubkey, unread_count)
        VALUES (?, 0)
      `,
      [peer],
    );
  },

  async updateLastMessage(
    peer: string,
    message: string,
    ts: number,
  ): Promise<void> {
    const db = getDatabase();

    await this.upsert(peer);
    await db.execute(
      `
        UPDATE conversations
        SET last_message = ?, last_message_at = ?
        WHERE peer_pubkey = ?
      `,
      [message, ts, peer],
    );
  },

  async incrementUnread(peer: string): Promise<void> {
    const db = getDatabase();

    await this.upsert(peer);
    await db.execute(
      `
        UPDATE conversations
        SET unread_count = unread_count + 1
        WHERE peer_pubkey = ?
      `,
      [peer],
    );
  },

  async resetUnread(peer: string): Promise<void> {
    const db = getDatabase();

    await db.execute(
      `
        UPDATE conversations
        SET unread_count = 0
        WHERE peer_pubkey = ?
      `,
      [peer],
    );
  },

  async delete(peer: string): Promise<void> {
    await this.markDeleted(peer, Math.floor(Date.now() / 1000));
  },

  async markDeleted(peer: string, deletedAt: number): Promise<void> {
    const db = getDatabase();

    await messagesRepo.deleteThrough(peer, deletedAt);
    await this.upsert(peer);
    await db.execute(
      `
        UPDATE conversations
        SET last_message = ?, last_message_at = ?, unread_count = 0
        WHERE peer_pubkey = ?
      `,
      [DELETED_CONVERSATION_MARKER, deletedAt, peer],
    );
  },

  async getDeletedAt(peer: string): Promise<number | null> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT last_message_at
        FROM conversations
        WHERE peer_pubkey = ? AND last_message = ?
        LIMIT 1
      `,
      [peer, DELETED_CONVERSATION_MARKER],
    );
    const deletedAt = result.rows[0]?.last_message_at;

    return deletedAt == null ? null : Number(deletedAt);
  },

  async list(): Promise<Conversation[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM conversations
        WHERE last_message IS NULL OR last_message != ?
        ORDER BY
          CASE WHEN last_message_at IS NULL THEN 1 ELSE 0 END,
          last_message_at DESC,
          peer_pubkey ASC
      `,
      [DELETED_CONVERSATION_MARKER],
    );

    return result.rows.map((row) =>
      mapRowToConversation(row as Record<string, unknown>),
    );
  },
};
