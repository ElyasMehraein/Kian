import type { TokenDefinition } from '@/types';

import { getDatabase } from '../init';

type StoredTokenDefinition = TokenDefinition & { event_id?: string };

function mapRowToTokenDefinition(row: Record<string, unknown>): TokenDefinition {
  return {
    asset_id: String(row.asset_id),
    pubkey: String(row.pubkey),
    product_id: String(row.product_id ?? ''),
    name: String(row.name),
    description: String(row.description ?? ''),
    images: JSON.parse(String(row.images ?? '[]')) as string[],
    categories: JSON.parse(String(row.categories ?? '[]')) as string[],
    unit: String(row.unit ?? 'unit'),
    created_at: Number(row.created_at),
  };
}

export const tokenDefinitionsRepo = {
  async upsert(def: TokenDefinition): Promise<void> {
    const db = getDatabase();
    const storedDef = def as StoredTokenDefinition;

    await db.execute(
      `
        INSERT INTO token_definitions (
          asset_id,
          pubkey,
          product_id,
          name,
          description,
          images,
          categories,
          unit,
          event_id,
          created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(asset_id, pubkey) DO UPDATE SET
          product_id = excluded.product_id,
          name = excluded.name,
          description = excluded.description,
          images = excluded.images,
          categories = excluded.categories,
          unit = excluded.unit,
          event_id = excluded.event_id,
          created_at = excluded.created_at
      `,
      [
        def.asset_id,
        def.pubkey,
        def.product_id,
        def.name,
        def.description,
        JSON.stringify(def.images),
        JSON.stringify(def.categories),
        def.unit,
        storedDef.event_id ?? '',
        def.created_at,
      ],
    );
  },

  async get(assetId: string, pubkey: string): Promise<TokenDefinition | null> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM token_definitions
        WHERE asset_id = ? AND pubkey = ?
        LIMIT 1
      `,
      [assetId, pubkey],
    );

    const row = result.rows[0];

    return row ? mapRowToTokenDefinition(row as Record<string, unknown>) : null;
  },

  async getByProducer(pubkey: string): Promise<TokenDefinition[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM token_definitions
        WHERE pubkey = ?
        ORDER BY created_at DESC
      `,
      [pubkey],
    );

    return result.rows.map((row) =>
      mapRowToTokenDefinition(row as Record<string, unknown>),
    );
  },
};
