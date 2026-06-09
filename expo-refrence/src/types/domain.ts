export interface KeyPair {
  pubkey: string;
  privkey: string;
  mnemonic: string;
  npub: string;
  nsec: string;
}

export interface Profile {
  pubkey: string;
  display_name?: string;
  about?: string;
  picture?: string;
  nip05?: string;
  lud16?: string;
  geohash?: string;
  created_at: number;
}

export interface Product {
  id: string;
  pubkey: string;
  name: string;
  description: string;
  images: string[];
  categories: string[];
  geohash?: string;
  created_at: number;
  event_id: string;
}

export interface ProductCategory {
  id: string;
  pubkey: string;
  name: string;
  parent_id?: string;
  level: number;
  created_at: number;
}

export interface TokenDefinition {
  asset_id: string;
  pubkey: string;
  product_id: string;
  name: string;
  description: string;
  images: string[];
  categories: string[];
  unit: string;
  created_at: number;
}

export interface TokenUtxo {
  utxo_id: string;
  asset_ref: string;
  producer: string;
  owner: string;
  amount: number;
  prev_utxo_id?: string;
  created_at: number;
  spent: boolean;
}

export interface Conversation {
  peer_pubkey: string;
  last_message?: string;
  last_message_at?: number;
  unread_count: number;
}

export interface ChatMessage {
  id: string;
  conversation_pubkey: string;
  sender: string;
  content: string;
  message_type: MessageType;
  created_at: number;
  status: MessageStatus;
  request_status?: ProductRequestStatus;
  request_transfer_utxo_id?: string;
  transfer_counterparty?: string;
  transfer_origin_message_id?: string;
  transfer_decision?: 'approved' | 'rejected';
  transfer_recipient?: string;
  transport_event_id?: string;
  transport_privkey?: string;
}

export type MessageType =
  | 'text'
  | 'image'
  | 'product_request'
  | 'product_send'
  | 'token_transfer'
  | 'conversation_delete';

export type MessageStatus =
  | 'sending'
  | 'sent'
  | 'delivered'
  | 'read';

export interface ProductRequest {
  product_id: string;
  product_name: string;
  quantity: number;
  producer_pubkey: string;
}

export type ProductRequestStatus =
  | 'open'
  | 'waiting_mint'
  | 'declined'
  | 'fulfilled'
  | 'rejected';

export interface TokenTransfer {
  utxo_id: string;
  asset_ref: string;
  amount: number;
  sender: string;
  recipient: string;
}

export interface ProductSend {
  product_id: string;
  product_name: string;
  product_description: string;
  product_images: string[];
  product_categories: string[];
  quantity: number;
  utxo_ids: string[];
  asset_ref: string;
}

export interface CartItem {
  product: Product;
  quantity: number;
}

export interface ReviewEntry {
  pubkey: string;
  target_pubkey: string;
  rating: number;
  comment: string;
  created_at: number;
}

export interface MerchantInfo {
  pubkey: string;
  profile: Profile;
  score: number;
  title: MerchantTitle;
  mutual_follows: number;
  distance_km?: number;
  social_rating?: number;
}

export type MerchantTitle = 'KianBan' | 'BazarGardan' | 'Tajer';
