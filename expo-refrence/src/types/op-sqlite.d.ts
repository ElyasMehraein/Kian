declare module '@op-engineering/op-sqlite' {
  export type SQLiteBindValue =
    | string
    | number
    | null
    | Uint8Array
    | ArrayBuffer
    | SQLiteBindValue[];

  export type SQLiteRow = Record<string, unknown>;

  export interface QueryResult {
    rows: SQLiteRow[];
  }

  export interface Transaction {
    execute(query: string, params?: SQLiteBindValue[]): Promise<QueryResult>;
    commit(): Promise<QueryResult>;
    rollback(): QueryResult;
  }

  export interface DB {
    execute(query: string, params?: SQLiteBindValue[]): Promise<QueryResult>;
    transaction<T>(callback: (tx: Transaction) => Promise<T>): Promise<T>;
    close(): void;
  }

  export function open(options: { name?: string; url?: string }): DB;
}
