import type { DB, Transaction } from './types';
import { handleCoreQueries } from './web/core-queries';
import { handleMessageAndConversationQueries } from './web/message-conversation-queries';
import { handleProfileAndProductQueries } from './web/profile-product-queries';
import { loadState, normalizeQuery, type WebQueryResult } from './web/state';

const QUERY_HANDLERS = [
  handleCoreQueries,
  handleProfileAndProductQueries,
  handleMessageAndConversationQueries,
] as const;

class WebDatabase implements DB {
  private state = loadState();

  async execute(query: string, params: unknown[] = []): Promise<WebQueryResult> {
    const normalized = normalizeQuery(query);

    for (const handler of QUERY_HANDLERS) {
      const result = handler(normalized, this.state, params);
      if (result) {
        return result;
      }
    }

    return { rows: [] };
  }

  async transaction<T>(callback: (tx: Transaction) => Promise<T>): Promise<T> {
    const tx: Transaction = {
      execute: async (query, params) => this.execute(query, params),
      commit: async () => ({ rows: [] }),
      rollback: () => ({ rows: [] }),
    };

    return callback(tx);
  }

  close(): void {}
}

let db: DB | null = null;

export function getDatabase(): DB {
  if (!db) {
    db = new WebDatabase();
  }

  return db;
}

export async function initDatabase(): Promise<void> {
  if (!db) {
    db = new WebDatabase();
  }
}
