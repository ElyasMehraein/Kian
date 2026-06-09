export interface NostrEvent {
  id: string;
  pubkey: string;
  created_at: number;
  kind: number;
  tags: string[][];
  content: string;
  sig: string;
}

export interface UnsignedEvent {
  pubkey: string;
  created_at: number;
  kind: number;
  tags: string[][];
  content: string;
}

export interface NostrFilter {
  ids?: string[];
  authors?: string[];
  kinds?: number[];
  '#e'?: string[];
  '#p'?: string[];
  '#a'?: string[];
  '#d'?: string[];
  since?: number;
  until?: number;
  limit?: number;
}

export type RelayMessage =
  | ['EVENT', string, NostrEvent]
  | ['OK', string, boolean, string]
  | ['EOSE', string]
  | ['NOTICE', string]
  | ['CLOSED', string, string];

export type ClientMessage =
  | ['REQ', string, ...NostrFilter[]]
  | ['EVENT', NostrEvent]
  | ['CLOSE', string];
