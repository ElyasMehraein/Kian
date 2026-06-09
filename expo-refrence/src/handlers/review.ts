import { getDatabase } from '@/db/init';
import type { NostrEvent, ReviewEntry } from '@/types';

type ReviewPageEntry = {
  rating?: number;
  review?: string;
  comment?: string;
};

type ReviewPage = Record<string, ReviewPageEntry>;

function getReviewPageId(event: NostrEvent): string | undefined {
  return event.tags.find(([name]) => name === 'd')?.[1];
}

function parseReviewPage(content: string): ReviewPage | null {
  try {
    const parsed = JSON.parse(content) as unknown;

    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      return null;
    }

    return parsed as ReviewPage;
  } catch {
    return null;
  }
}

function normalizeReviewEntry(entry: ReviewPageEntry): {
  rating: number;
  comment: string;
} | null {
  if (!entry || Array.isArray(entry) || typeof entry !== 'object') {
    return null;
  }

  const rating = Number(entry.rating ?? 0);

  if (!Number.isFinite(rating)) {
    return null;
  }

  const commentValue = entry.review ?? entry.comment ?? '';

  return {
    rating,
    comment: typeof commentValue === 'string' ? commentValue : '',
  };
}

function toReviewEntries(event: NostrEvent, page: ReviewPage): ReviewEntry[] {
  return Object.entries(page).flatMap(([targetPubkey, entry]) => {
    if (!targetPubkey) {
      return [];
    }

    const normalized = normalizeReviewEntry(entry);

    if (!normalized) {
      return [];
    }

    return [
      {
        pubkey: event.pubkey,
        target_pubkey: targetPubkey,
        rating: normalized.rating,
        comment: normalized.comment,
        created_at: event.created_at,
      },
    ];
  });
}

export async function handleReview(event: NostrEvent): Promise<void> {
  if (getReviewPageId(event) !== 'kian_reviews') {
    return;
  }

  const page = parseReviewPage(event.content);

  if (!page) {
    return;
  }

  const entries = toReviewEntries(event, page);
  const db = getDatabase();

  await db.execute('BEGIN');

  try {
    await db.execute('DELETE FROM reviews WHERE pubkey = ?', [event.pubkey]);

    for (const entry of entries) {
      await db.execute(
        `
          INSERT INTO reviews (
            pubkey,
            target_pubkey,
            rating,
            comment,
            created_at
          )
          VALUES (?, ?, ?, ?, ?)
        `,
        [
          entry.pubkey,
          entry.target_pubkey,
          entry.rating,
          entry.comment,
          entry.created_at,
        ],
      );
    }

    await db.execute('COMMIT');
  } catch (error) {
    await db.execute('ROLLBACK');
    throw error;
  }
}
