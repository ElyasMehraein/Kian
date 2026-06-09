import { getDatabase } from '../init';

export interface StoredDmInboxRelay {
  pubkey: string;
  relay_url: string;
  created_at: number;
}

function mapRow(row: Record<string, unknown>): StoredDmInboxRelay {
  return {
    pubkey: String(row.pubkey),
    relay_url: String(row.relay_url),
    created_at: Number(row.created_at),
  };
}

export const dmInboxRelaysRepo = {
  async replace(pubkey: string, relayUrls: string[]): Promise<void> {
    const db = getDatabase();
    const createdAt = Math.floor(Date.now() / 1000);

    await db.transaction(async (tx) => {
      await tx.execute(
        `
          DELETE FROM dm_inbox_relays
          WHERE pubkey = ?
        `,
        [pubkey],
      );

      for (const relayUrl of relayUrls) {
        await tx.execute(
          `
            INSERT OR REPLACE INTO dm_inbox_relays (
              pubkey,
              relay_url,
              created_at
            )
            VALUES (?, ?, ?)
          `,
          [pubkey, relayUrl, createdAt],
        );
      }
    });
  },

  async listByPubkey(pubkey: string): Promise<string[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT relay_url
        FROM dm_inbox_relays
        WHERE pubkey = ?
        ORDER BY relay_url ASC
      `,
      [pubkey],
    );

    return result.rows.map((row) => String((row as Record<string, unknown>).relay_url));
  },

  async listAll(): Promise<StoredDmInboxRelay[]> {
    const db = getDatabase();
    const result = await db.execute(
      `
        SELECT pubkey, relay_url, created_at
        FROM dm_inbox_relays
        ORDER BY pubkey ASC, relay_url ASC
      `,
    );

    return result.rows.map((row) => mapRow(row as Record<string, unknown>));
  },
};
