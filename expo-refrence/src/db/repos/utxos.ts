import type { TokenUtxo } from '@/types';

import { getDatabase } from '../init';

function mapRowToTokenUtxo(row: Record<string, unknown>): TokenUtxo {
  return {
    utxo_id: String(row.utxo_id),
    asset_ref: String(row.asset_ref),
    producer: String(row.producer),
    owner: String(row.owner),
    amount: Number(row.amount),
    prev_utxo_id: (row.prev_utxo_id as string | null | undefined) ?? undefined,
    created_at: Number(row.created_at),
    spent: Boolean(row.spent),
  };
}

export const utxoRepo = {
  async insert(utxo: TokenUtxo): Promise<void> {
    const db = getDatabase();

    await db.execute(
      `
        INSERT OR REPLACE INTO token_utxos (
          utxo_id,
          asset_ref,
          producer,
          owner,
          amount,
          prev_utxo_id,
          created_at,
          spent
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `,
      [
        utxo.utxo_id,
        utxo.asset_ref,
        utxo.producer,
        utxo.owner,
        utxo.amount,
        utxo.prev_utxo_id ?? null,
        utxo.created_at,
        utxo.spent ? 1 : 0,
      ],
    );
  },

  async markSpent(utxoId: string): Promise<void> {
    const db = getDatabase();

    await db.execute(
      `
        UPDATE token_utxos
        SET spent = 1
        WHERE utxo_id = ?
      `,
      [utxoId],
    );
  },

  async getByOwner(pubkey: string): Promise<TokenUtxo[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM token_utxos
        WHERE owner = ?
        ORDER BY created_at DESC
      `,
      [pubkey],
    );

    return result.rows.map((row) => mapRowToTokenUtxo(row as Record<string, unknown>));
  },

  async getUnspentByOwner(pubkey: string): Promise<TokenUtxo[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM token_utxos
        WHERE owner = ? AND spent = 0
        ORDER BY created_at DESC
      `,
      [pubkey],
    );

    return result.rows.map((row) => mapRowToTokenUtxo(row as Record<string, unknown>));
  },

  async get(utxoId: string): Promise<TokenUtxo | null> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM token_utxos
        WHERE utxo_id = ?
        LIMIT 1
      `,
      [utxoId],
    );

    const row = result.rows[0];

    return row ? mapRowToTokenUtxo(row as Record<string, unknown>) : null;
  },
};
