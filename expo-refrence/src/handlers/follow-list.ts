import { getDatabase } from '@/db/init';
import type { NostrEvent } from '@/types';

type FollowEntry = {
  follows_pubkey: string;
  relay_hint?: string;
  pet_name?: string;
};

function toFollowEntry(tag: string[]): FollowEntry | null {
  const [name, followsPubkey, relayHint, petName] = tag;

  if (name !== 'p' || !followsPubkey) {
    return null;
  }

  return {
    follows_pubkey: followsPubkey,
    relay_hint: relayHint || undefined,
    pet_name: petName || undefined,
  };
}

export async function handleFollowList(event: NostrEvent): Promise<void> {
  const follows = event.tags
    .map((tag) => toFollowEntry(tag))
    .filter((entry): entry is FollowEntry => entry !== null);
  const db = getDatabase();

  await db.execute('BEGIN');

  try {
    await db.execute('DELETE FROM user_follows WHERE pubkey = ?', [event.pubkey]);

    for (const follow of follows) {
      await db.execute(
        `
          INSERT INTO user_follows (
            pubkey,
            follows_pubkey,
            pet_name,
            relay_hint,
            created_at
          )
          VALUES (?, ?, ?, ?, ?)
        `,
        [
          event.pubkey,
          follow.follows_pubkey,
          follow.pet_name ?? null,
          follow.relay_hint ?? null,
          event.created_at,
        ],
      );
    }

    await db.execute('COMMIT');
  } catch (error) {
    await db.execute('ROLLBACK');
    throw error;
  }
}
