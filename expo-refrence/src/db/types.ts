import type {
  DB as OPSQLiteDB,
  Transaction as OPSQLiteTransaction,
} from '@op-engineering/op-sqlite';

export type DB = OPSQLiteDB;
export type Transaction = OPSQLiteTransaction;
