import { getDatabase, keysRepo, profilesRepo, reviewsRepo } from '@/db';
import { MERCHANT_TITLE_THRESHOLDS } from '@/types';
import type { Profile, MerchantInfo } from '@/types';
import { geohashDistance } from '@/utils';

function getFallbackProfile(pubkey: string): Profile {
  return {
    pubkey,
    created_at: 0,
  };
}

function average(values: number[]): number {
  if (values.length === 0) {
    return 0;
  }

  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

async function getMutualFollows(currentPubkey: string, merchantPubkey: string): Promise<number> {
  const db = getDatabase();
  const result = await db.execute(
    `
      SELECT COUNT(*) AS count
      FROM user_follows AS mine
      INNER JOIN user_follows AS theirs
        ON mine.follows_pubkey = theirs.pubkey
      WHERE mine.pubkey = ?
        AND theirs.follows_pubkey = ?
    `,
    [currentPubkey, merchantPubkey],
  );

  return Number(result.rows[0]?.count ?? 0);
}

async function getFollowingSet(currentPubkey: string | null): Promise<Set<string>> {
  if (!currentPubkey) {
    return new Set();
  }

  const db = getDatabase();
  const result = await db.execute(
    `
      SELECT follows_pubkey
      FROM user_follows
      WHERE pubkey = ?
    `,
    [currentPubkey],
  );

  return new Set(
    result.rows.map((row) => String((row as Record<string, unknown>).follows_pubkey)),
  );
}

function getDistanceKm(currentProfile: Profile | null, merchantProfile: Profile): number | undefined {
  if (!currentProfile?.geohash || !merchantProfile.geohash) {
    return undefined;
  }

  return geohashDistance(currentProfile.geohash, merchantProfile.geohash);
}

function getDistanceFactor(distanceKm: number | undefined): number {
  if (distanceKm == null) {
    return 0;
  }

  return distanceKm <= 10 ? 1 : 0;
}

function getTitle(score: number): MerchantInfo['title'] {
  if (score >= MERCHANT_TITLE_THRESHOLDS.KIANBAN) {
    return 'KianBan';
  }

  if (score >= MERCHANT_TITLE_THRESHOLDS.BAZARGARDAN) {
    return 'BazarGardan';
  }

  return 'Tajer';
}

export async function rankMerchants(pubkeys: string[]): Promise<MerchantInfo[]> {
  if (pubkeys.length === 0) {
    return [];
  }

  const uniquePubkeys = [...new Set(pubkeys)];
  const [currentPubkey, currentProfile, profiles] = await Promise.all([
    keysRepo.getPublicKey(),
    keysRepo.getPublicKey().then(async (pubkey) =>
      pubkey ? profilesRepo.get(pubkey) : null,
    ),
    profilesRepo.getMany(uniquePubkeys),
  ]);
  const followingSet = await getFollowingSet(currentPubkey);
  const profileMap = new Map(profiles.map((profile) => [profile.pubkey, profile]));

  const ranked = await Promise.all(
    uniquePubkeys.map(async (pubkey) => {
      const profile = profileMap.get(pubkey) ?? getFallbackProfile(pubkey);
      const [reviews, mutualFollows] = await Promise.all([
        reviewsRepo.getForTarget(pubkey),
        currentPubkey ? getMutualFollows(currentPubkey, pubkey) : Promise.resolve(0),
      ]);
      const socialRating = average(
        reviews
          .filter((review) => followingSet.has(review.pubkey))
          .map((review) => review.rating),
      );
      const distanceKm = getDistanceKm(currentProfile, profile);
      const score = mutualFollows + socialRating + getDistanceFactor(distanceKm);

      return {
        pubkey,
        profile,
        score,
        title: getTitle(score),
        mutual_follows: mutualFollows,
        distance_km: distanceKm,
        social_rating: socialRating,
      } satisfies MerchantInfo;
    }),
  );

  return ranked.sort((left, right) => right.score - left.score);
}
