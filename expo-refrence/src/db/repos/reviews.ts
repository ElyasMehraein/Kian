import type { ReviewEntry } from '@/types';

import { getDatabase } from '../init';

function mapRowToReview(row: Record<string, unknown>): ReviewEntry {
  return {
    pubkey: String(row.pubkey),
    target_pubkey: String(row.target_pubkey),
    rating: Number(row.rating),
    comment: String(row.comment ?? ''),
    created_at: Number(row.created_at),
  };
}

export const reviewsRepo = {
  async upsert(review: ReviewEntry): Promise<void> {
    const db = getDatabase();

    await db.execute(
      `
        INSERT OR REPLACE INTO reviews (
          pubkey,
          target_pubkey,
          rating,
          comment,
          created_at
        )
        VALUES (?, ?, ?, ?, ?)
      `,
      [
        review.pubkey,
        review.target_pubkey,
        review.rating,
        review.comment,
        review.created_at,
      ],
    );
  },

  async getForTarget(pubkey: string): Promise<ReviewEntry[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT pubkey, target_pubkey, rating, comment, created_at
        FROM reviews
        WHERE target_pubkey = ?
        ORDER BY created_at DESC
      `,
      [pubkey],
    );

    return result.rows.map((row) => mapRowToReview(row as Record<string, unknown>));
  },

  async getByAuthorAndTarget(
    pubkey: string,
    targetPubkey: string,
  ): Promise<ReviewEntry | null> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT pubkey, target_pubkey, rating, comment, created_at
        FROM reviews
        WHERE pubkey = ? AND target_pubkey = ?
        LIMIT 1
      `,
      [pubkey, targetPubkey],
    );

    const row = result.rows[0];

    return row ? mapRowToReview(row as Record<string, unknown>) : null;
  },
};
