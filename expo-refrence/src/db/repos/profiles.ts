import type { Profile } from '@/types';

import { getDatabase } from '../init';

type StoredProfile = Profile & {
  is_trader?: boolean;
  tags?: string[][];
};

function hasMerchantTag(tags: string[][] | undefined): boolean {
  return Boolean(tags?.some(([name, value]) => name === 't' && value === 'trader'));
}

function normalizeStoredProfile(profile: StoredProfile): StoredProfile {
  return {
    ...profile,
    is_trader: profile.is_trader ?? hasMerchantTag(profile.tags),
  };
}

function mapRowToProfile(row: Record<string, unknown>): Profile {
  const raw = typeof row.raw_json === 'string' ? JSON.parse(row.raw_json) : {};

  return {
    ...raw,
    pubkey: String(row.pubkey),
    created_at: Number(row.created_at),
    display_name:
      (row.display_name as string | null | undefined) ?? raw.display_name,
    about: (row.about as string | null | undefined) ?? raw.about,
    picture: (row.picture as string | null | undefined) ?? raw.picture,
    nip05: (row.nip05 as string | null | undefined) ?? raw.nip05,
    geohash: (row.geohash as string | null | undefined) ?? raw.geohash,
  };
}

function buildSearchPattern(query: string): string {
  return `%${query.trim()}%`;
}

export const profilesRepo = {
  async upsert(profile: Profile): Promise<void> {
    const db = getDatabase();
    const storedProfile = normalizeStoredProfile(profile as StoredProfile);

    await db.execute(
      `
        INSERT INTO profiles (
          pubkey,
          name,
          display_name,
          about,
          picture,
          nip05,
          geohash,
          raw_json,
          created_at,
          updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, strftime('%s','now'))
        ON CONFLICT(pubkey) DO UPDATE SET
          name = excluded.name,
          display_name = excluded.display_name,
          about = excluded.about,
          picture = excluded.picture,
          nip05 = excluded.nip05,
          geohash = excluded.geohash,
          raw_json = excluded.raw_json,
          created_at = excluded.created_at,
          updated_at = strftime('%s','now')
        WHERE excluded.created_at >= profiles.created_at
      `,
      [
        storedProfile.pubkey,
        null,
        storedProfile.display_name ?? null,
        storedProfile.about ?? null,
        storedProfile.picture ?? null,
        storedProfile.nip05 ?? null,
        storedProfile.geohash ?? null,
        JSON.stringify(storedProfile),
        storedProfile.created_at,
      ],
    );
  },

  async get(pubkey: string): Promise<Profile | null> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM profiles
        WHERE pubkey = ?
        LIMIT 1
      `,
      [pubkey],
    );

    const row = result.rows[0];

    return row ? mapRowToProfile(row as Record<string, unknown>) : null;
  },

  async getMany(pubkeys: string[]): Promise<Profile[]> {
    if (pubkeys.length === 0) {
      return [];
    }

    const db = getDatabase();
    const placeholders = pubkeys.map(() => '?').join(', ');
    const result = await db.execute(
      `
        SELECT *
        FROM profiles
        WHERE pubkey IN (${placeholders})
      `,
      pubkeys,
    );

    return result.rows.map((row) => mapRowToProfile(row as Record<string, unknown>));
  },

  async searchByName(query: string): Promise<Profile[]> {
    const trimmed = query.trim();

    if (!trimmed) {
      return [];
    }

    const db = getDatabase();
    const pattern = buildSearchPattern(trimmed);
    const result = await db.execute(
      `
        SELECT *
        FROM profiles
        WHERE display_name LIKE ?
        ORDER BY updated_at DESC
      `,
      [pattern],
    );

    return result.rows.map((row) => mapRowToProfile(row as Record<string, unknown>));
  },

  async listMerchants(): Promise<Profile[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT *
        FROM profiles
        WHERE json_extract(raw_json, '$.is_trader') = 1
        ORDER BY updated_at DESC, pubkey ASC
      `,
    );

    return result.rows.map((row) => mapRowToProfile(row as Record<string, unknown>));
  },
};
