import { open } from '@op-engineering/op-sqlite';

import { DB_NAME } from '@/types';

import { runMigrations } from './migrations';
import type { DB } from './types';

let db: DB | null = null;

export function getDatabase(): DB {
  if (!db) {
    throw new Error('Database not initialized');
  }

  return db;
}

export async function initDatabase(): Promise<void> {
  if (db) {
    return;
  }

  db = open({ name: DB_NAME });
  await db.execute('PRAGMA journal_mode = WAL');
  await db.execute('PRAGMA foreign_keys = ON');
  await runMigrations(db);
}
