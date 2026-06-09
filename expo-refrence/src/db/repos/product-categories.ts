import type { ProductCategory } from '@/types';

import { getDatabase } from '../init';

function mapRowToCategory(row: Record<string, unknown>): ProductCategory {
  return {
    id: String(row.id),
    pubkey: String(row.pubkey),
    name: String(row.name),
    parent_id: (row.parent_id as string | null | undefined) ?? undefined,
    level: Number(row.level),
    created_at: Number(row.created_at),
  };
}

export const productCategoriesRepo = {
  async upsert(category: ProductCategory): Promise<void> {
    const db = getDatabase();

    await db.execute(
      `
        INSERT INTO product_categories (
          id,
          pubkey,
          name,
          parent_id,
          level,
          created_at
        )
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(id, pubkey) DO UPDATE SET
          name = excluded.name,
          parent_id = excluded.parent_id,
          level = excluded.level,
          created_at = excluded.created_at
      `,
      [
        category.id,
        category.pubkey,
        category.name,
        category.parent_id ?? null,
        category.level,
        category.created_at,
      ],
    );
  },

  async listByPubkey(pubkey: string): Promise<ProductCategory[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM product_categories
        WHERE pubkey = ?
        ORDER BY level ASC, created_at ASC, name COLLATE NOCASE ASC
      `,
      [pubkey],
    );

    return result.rows.map((row) => mapRowToCategory(row as Record<string, unknown>));
  },

  async deleteBranch(pubkey: string, ids: string[]): Promise<void> {
    if (ids.length === 0) {
      return;
    }

    const db = getDatabase();
    const placeholders = ids.map(() => '?').join(', ');

    await db.execute(
      `
        DELETE FROM product_categories
        WHERE pubkey = ? AND id IN (${placeholders})
      `,
      [pubkey, ...ids],
    );
  },
};
