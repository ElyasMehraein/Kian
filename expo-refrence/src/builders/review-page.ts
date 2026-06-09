import { EVENT_KIND } from '@/types';
import type { ReviewEntry, UnsignedEvent } from '@/types';

function serializeReviewPage(reviews: ReviewEntry[]): string {
  const page = Object.fromEntries(
    reviews.map((review) => [
      review.target_pubkey,
      {
        rating: review.rating,
        review: review.comment,
      },
    ]),
  );

  return JSON.stringify(page);
}

function buildReviewTags(reviews: ReviewEntry[], page: number): string[][] {
  const targetTags = reviews.map((review) => ['p', review.target_pubkey]);

  return [['d', page === 0 ? 'kian_reviews' : `kian_reviews:${page}`], ...targetTags];
}

export function buildReviewPage(
  reviews: ReviewEntry[],
  page: number,
): UnsignedEvent {
  return {
    pubkey: reviews[0]?.pubkey ?? '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.REVIEW,
    tags: buildReviewTags(reviews, page),
    content: serializeReviewPage(reviews),
  };
}
