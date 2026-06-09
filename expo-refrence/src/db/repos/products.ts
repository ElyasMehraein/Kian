import type { Product } from '@/types';

import { getDatabase } from '../init';

function mapRowToProduct(row: Record<string, unknown>): Product {
  return {
    id: String(row.id),
    pubkey: String(row.pubkey),
    name: String(row.name),
    description: String(row.description ?? ''),
    images: JSON.parse(String(row.images ?? '[]')) as string[],
    categories: JSON.parse(String(row.categories ?? '[]')) as string[],
    geohash: (row.geohash as string | null | undefined) ?? undefined,
    created_at: Number(row.created_at),
    event_id: String(row.event_id),
  };
}

export const productsRepo = {
  async upsert(product: Product): Promise<void> {
    const db = getDatabase();

    await db.execute(
      `
        INSERT INTO products (
          id,
          pubkey,
          name,
          description,
          images,
          categories,
          geohash,
          event_id,
          created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id, pubkey) DO UPDATE SET
          name = excluded.name,
          description = excluded.description,
          images = excluded.images,
          categories = excluded.categories,
          geohash = excluded.geohash,
          event_id = excluded.event_id,
          created_at = excluded.created_at
      `,
      [
        product.id,
        product.pubkey,
        product.name,
        product.description,
        JSON.stringify(product.images),
        JSON.stringify(product.categories),
        product.geohash ?? null,
        product.event_id,
        product.created_at,
      ],
    );
  },

  async getByProducer(pubkey: string): Promise<Product[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM products
        WHERE pubkey = ?
        ORDER BY created_at DESC
      `,
      [pubkey],
    );

    return result.rows.map((row) => mapRowToProduct(row as Record<string, unknown>));
  },

  async listProducerPubkeys(): Promise<string[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT DISTINCT pubkey
        FROM products
        ORDER BY pubkey ASC
      `,
    );

    return result.rows.map((row) => String((row as Record<string, unknown>).pubkey));
  },

  async get(productId: string, pubkey: string): Promise<Product | null> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM products
        WHERE id = ? AND pubkey = ?
        LIMIT 1
      `,
      [productId, pubkey],
    );

    const row = result.rows[0];

    return row ? mapRowToProduct(row as Record<string, unknown>) : null;
  },

  async deleteByEventIds(pubkey: string, eventIds: string[]): Promise<void> {
    if (eventIds.length === 0) {
      return;
    }

    const db = getDatabase();
    const placeholders = eventIds.map(() => '?').join(', ');

    await db.execute(
      `
        DELETE FROM products
        WHERE pubkey = ? AND event_id IN (${placeholders})
      `,
      [pubkey, ...eventIds],
    );
  },
};
