import { DB_VERSION } from '@/types';

import { LATE_USE_SCHEMA_STATEMENTS, SCHEMA_STATEMENTS } from './schema';
import type { DB } from './types';

async function runStatements(
  db: DB,
  statements: readonly string[],
): Promise<void> {
  await db.transaction(async (tx) => {
    for (const statement of statements) {
      await tx.execute(statement);
    }
  });
}

async function ensureTokenDefinitionColumns(db: DB): Promise<void> {
  await db.execute(`ALTER TABLE token_definitions ADD COLUMN images TEXT DEFAULT '[]'`);
  await db.execute(`ALTER TABLE token_definitions ADD COLUMN categories TEXT DEFAULT '[]'`);
}

async function ensureOfflineQueueColumns(db: DB): Promise<void> {
  await db.execute(`ALTER TABLE offline_queue ADD COLUMN relay_urls TEXT NOT NULL DEFAULT '[]'`);
  await db.execute(`ALTER TABLE offline_queue ADD COLUMN queue_scope TEXT NOT NULL DEFAULT 'generic'`);
  await db.execute(`ALTER TABLE offline_queue ADD COLUMN peer_pubkey TEXT`);
}

export async function runMigrations(db: DB): Promise<void> {
  const versionResult = await db.execute('PRAGMA user_version');
  const version = Number(versionResult.rows[0]?.user_version ?? 0);

  if (version < DB_VERSION) {
    await runStatements(db, SCHEMA_STATEMENTS);
    await db.execute(`PRAGMA user_version = ${DB_VERSION}`);
  }

  if (version >= DB_VERSION) {
    await runStatements(db, LATE_USE_SCHEMA_STATEMENTS);
    try {
      await ensureTokenDefinitionColumns(db);
    } catch {}
    try {
      await ensureOfflineQueueColumns(db);
    } catch {}
    return;
  }

  await runStatements(db, LATE_USE_SCHEMA_STATEMENTS);
  try {
    await ensureTokenDefinitionColumns(db);
  } catch {}
  try {
    await ensureOfflineQueueColumns(db);
  } catch {}
}
