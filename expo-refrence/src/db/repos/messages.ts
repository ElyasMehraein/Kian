import type { ChatMessage, MessageStatus, ProductRequestStatus } from '@/types';

import { getDatabase } from '../init';

function mapRowToMessage(row: Record<string, unknown>): ChatMessage {
  const raw = typeof row.raw_json === 'string' ? JSON.parse(row.raw_json) : {};

  return {
    ...raw,
    id: String(row.id),
    conversation_pubkey: String(row.conversation_pubkey),
    sender: String(row.sender),
    content: String(row.content),
    message_type: String(row.message_type) as ChatMessage['message_type'],
    created_at: Number(row.created_at),
    status: String(row.status) as MessageStatus,
    request_status:
      raw && typeof raw.request_status === 'string'
        ? (raw.request_status as ProductRequestStatus)
        : undefined,
    request_transfer_utxo_id:
      raw && typeof raw.request_transfer_utxo_id === 'string'
        ? raw.request_transfer_utxo_id
        : undefined,
    transfer_counterparty:
      raw && typeof raw.transfer_counterparty === 'string'
        ? raw.transfer_counterparty
        : undefined,
    transfer_origin_message_id:
      raw && typeof raw.transfer_origin_message_id === 'string'
        ? raw.transfer_origin_message_id
        : undefined,
    transfer_decision:
      raw && typeof raw.transfer_decision === 'string'
        ? (raw.transfer_decision as 'approved' | 'rejected')
        : undefined,
    transfer_recipient:
      raw && typeof raw.transfer_recipient === 'string'
        ? raw.transfer_recipient
        : undefined,
  };
}

export const messagesRepo = {
  async insert(msg: ChatMessage): Promise<void> {
    const db = getDatabase();

    await db.execute(
      `
        INSERT OR REPLACE INTO messages (
          id,
          conversation_pubkey,
          sender,
          content,
          message_type,
          created_at,
          status,
          raw_json
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `,
      [
        msg.id,
        msg.conversation_pubkey,
        msg.sender,
        msg.content,
        msg.message_type,
        msg.created_at,
        msg.status,
        JSON.stringify(msg),
      ],
    );
  },

  async getConversation(peer: string): Promise<ChatMessage[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM messages
        WHERE conversation_pubkey = ?
        ORDER BY created_at ASC
      `,
      [peer],
    );

    return result.rows.map((row) => mapRowToMessage(row as Record<string, unknown>));
  },

  async getLatestConversationDeleteAt(peer: string): Promise<number | null> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT created_at
        FROM messages
        WHERE conversation_pubkey = ? AND message_type = 'conversation_delete'
        ORDER BY created_at DESC
        LIMIT 1
      `,
      [peer],
    );
    const createdAt = result.rows[0]?.created_at;

    return createdAt == null ? null : Number(createdAt);
  },

  async listConversationTransport(peer: string): Promise<ChatMessage[]> {
    return this.getConversation(peer);
  },

  async listProductRequests(): Promise<ChatMessage[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM messages
        WHERE message_type = 'product_request'
        ORDER BY created_at ASC
      `,
    );

    return result.rows.map((row) => mapRowToMessage(row as Record<string, unknown>));
  },


  async listTokenTransfers(): Promise<ChatMessage[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM messages
        WHERE message_type = 'token_transfer'
        ORDER BY created_at ASC
      `,
    );

    return result.rows
      .map((row) => mapRowToMessage(row as Record<string, unknown>))
;
  },

  async listTokenTransfersByUtxo(utxoId: string): Promise<ChatMessage[]> {
    return (await this.listTokenTransfers())
      .filter((message) => message.request_transfer_utxo_id === utxoId);
  },

  async deleteThrough(peer: string, createdAt: number): Promise<void> {
    const db = getDatabase();

    await db.execute(
      `
        DELETE FROM messages
        WHERE conversation_pubkey = ? AND created_at <= ?
      `,
      [peer, createdAt],
    );
  },

  async deleteConversation(peer: string): Promise<void> {
    const db = getDatabase();

    await db.execute(
      `
        DELETE FROM messages
        WHERE conversation_pubkey = ?
      `,
      [peer],
    );
  },


  async resolveTokenTransfersForUtxo(
    utxoId: string,
    approvedMessageId: string,
    recipient: string,
  ): Promise<void> {
    const transfers = await this.listTokenTransfersByUtxo(utxoId);

    for (const transfer of transfers) {
      const decision = transfer.id === approvedMessageId ? 'approved' : 'rejected';
      const status = transfer.id === approvedMessageId ? 'fulfilled' : 'rejected';

      await this.updateRequestMetadata(transfer.id, {
        request_status: status,
        transfer_decision: decision,
        transfer_recipient: recipient,
      });
    }
  },

  async updateStatus(id: string, status: MessageStatus): Promise<void> {
    const db = getDatabase();
    const existing = await db.execute(
      `
        SELECT raw_json
        FROM messages
        WHERE id = ?
        LIMIT 1
      `,
      [id],
    );

    const rawJson = existing.rows[0]?.raw_json;
    const nextRaw =
      typeof rawJson === 'string'
        ? JSON.stringify({ ...JSON.parse(rawJson), status })
        : null;

    await db.execute(
      `
        UPDATE messages
        SET status = ?, raw_json = COALESCE(?, raw_json)
        WHERE id = ?
      `,
      [status, nextRaw, id],
    );
  },

  async updateRequestStatus(id: string, requestStatus: ProductRequestStatus): Promise<void> {
    await this.updateRequestMetadata(id, { request_status: requestStatus });
  },

  async updateTokenTransferStatus(id: string, requestStatus: ProductRequestStatus): Promise<void> {
    const db = getDatabase();
    const existing = await db.execute(
      `
        SELECT id, raw_json
        FROM messages
        WHERE id = ? OR json_extract(raw_json, '$.transfer_origin_message_id') = ?
      `,
      [id, id],
    );

    for (const row of existing.rows as Record<string, unknown>[]) {
      const rawJson = row.raw_json;
      const nextRaw =
        typeof rawJson === 'string'
          ? JSON.stringify({ ...JSON.parse(rawJson), request_status: requestStatus })
          : JSON.stringify({ request_status: requestStatus });

      await db.execute(
        `
          UPDATE messages
          SET raw_json = ?
          WHERE id = ?
        `,
        [nextRaw, String(row.id)],
      );
    }
  },

  async updateRequestMetadata(
    id: string,
    patch: Record<string, unknown>,
  ): Promise<void> {
    const db = getDatabase();
    const existing = await db.execute(
      `
        SELECT status, raw_json
        FROM messages
        WHERE id = ?
        LIMIT 1
      `,
      [id],
    );

    const row = existing.rows[0] as Record<string, unknown> | undefined;
    const rawJson = row?.raw_json;
    const currentStatus = typeof row?.status === 'string' ? row.status : 'sent';
    const nextRaw =
      typeof rawJson === 'string'
        ? JSON.stringify({ ...JSON.parse(rawJson), ...patch })
        : JSON.stringify(patch);

    await db.execute(
      `
        UPDATE messages
        SET status = ?, raw_json = ?
        WHERE id = ?
      `,
      [currentStatus, nextRaw, id],
    );
  },
};
