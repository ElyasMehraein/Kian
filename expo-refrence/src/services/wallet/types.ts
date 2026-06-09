import type { ProductSend } from '@/types';

export type PendingTokenConfirmation = {
  event_id: string;
  utxo_id: string;
  asset_ref: string;
  amount: number;
  recipient: string;
  created_at: number;
  status: 'waiting_mint' | 'fulfilled' | 'rejected' | 'offline';
  message_id?: string;
};

export type FulfillProductRequestResult =
  | { status: 'fulfilled' }
  | { status: 'waiting_mint'; transferUtxoId: string };

export type MintProductForChatResult = {
  minted: ProductSend;
};
