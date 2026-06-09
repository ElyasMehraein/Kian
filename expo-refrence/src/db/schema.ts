export const SCHEMA_STATEMENTS = [
  `CREATE TABLE IF NOT EXISTS keys (
    pubkey TEXT PRIMARY KEY,
    npub TEXT NOT NULL,
    created_at INTEGER NOT NULL
  )`,
  `CREATE TABLE IF NOT EXISTS profiles (
    pubkey TEXT PRIMARY KEY,
    name TEXT,
    display_name TEXT,
    about TEXT,
    picture TEXT,
    nip05 TEXT,
    geohash TEXT,
    raw_json TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
  )`,
  `CREATE TABLE IF NOT EXISTS user_follows (
    pubkey TEXT NOT NULL,
    follows_pubkey TEXT NOT NULL,
    pet_name TEXT,
    relay_hint TEXT,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (pubkey, follows_pubkey)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_follows_pubkey ON user_follows(pubkey)`,
  `CREATE INDEX IF NOT EXISTS idx_follows_target ON user_follows(follows_pubkey)`,
  `CREATE TABLE IF NOT EXISTS products (
    id TEXT NOT NULL,
    pubkey TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    images TEXT DEFAULT '[]',
    categories TEXT DEFAULT '[]',
    geohash TEXT,
    event_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (id, pubkey)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_products_pubkey ON products(pubkey)`,
  `CREATE TABLE IF NOT EXISTS product_categories (
    id TEXT NOT NULL,
    pubkey TEXT NOT NULL,
    name TEXT NOT NULL,
    parent_id TEXT,
    level INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (id, pubkey)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_product_categories_pubkey ON product_categories(pubkey)`,
  `CREATE INDEX IF NOT EXISTS idx_product_categories_parent ON product_categories(pubkey, parent_id)`,
  `CREATE TABLE IF NOT EXISTS token_definitions (
    asset_id TEXT NOT NULL,
    pubkey TEXT NOT NULL,
    product_id TEXT,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    images TEXT DEFAULT '[]',
    categories TEXT DEFAULT '[]',
    unit TEXT DEFAULT 'unit',
    event_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (asset_id, pubkey)
  )`,
  `CREATE TABLE IF NOT EXISTS token_utxos (
    utxo_id TEXT PRIMARY KEY,
    asset_ref TEXT NOT NULL,
    producer TEXT NOT NULL,
    owner TEXT NOT NULL,
    amount INTEGER NOT NULL,
    prev_utxo_id TEXT,
    created_at INTEGER NOT NULL,
    spent INTEGER NOT NULL DEFAULT 0
  )`,
  `CREATE INDEX IF NOT EXISTS idx_utxos_owner ON token_utxos(owner)`,
  `CREATE INDEX IF NOT EXISTS idx_utxos_producer ON token_utxos(producer)`,
  `CREATE INDEX IF NOT EXISTS idx_utxos_asset ON token_utxos(asset_ref)`,
  `CREATE TABLE IF NOT EXISTS conversations (
    peer_pubkey TEXT PRIMARY KEY,
    last_message TEXT,
    last_message_at INTEGER,
    unread_count INTEGER NOT NULL DEFAULT 0
  )`,
  `CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    conversation_pubkey TEXT NOT NULL,
    sender TEXT NOT NULL,
    content TEXT NOT NULL,
    message_type TEXT NOT NULL DEFAULT 'text',
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'sending',
    raw_json TEXT,
    FOREIGN KEY (conversation_pubkey) REFERENCES conversations(peer_pubkey)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conversation_pubkey, created_at)`,
  `CREATE TABLE IF NOT EXISTS message_receipts (
    message_id TEXT NOT NULL,
    receipt_type TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (message_id, receipt_type)
  )`,
  `CREATE TABLE IF NOT EXISTS reviews (
    pubkey TEXT NOT NULL,
    target_pubkey TEXT NOT NULL,
    rating INTEGER NOT NULL,
    comment TEXT DEFAULT '',
    page_index INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (pubkey, target_pubkey)
  )`,
  `CREATE TABLE IF NOT EXISTS offline_queue (
    event_id TEXT PRIMARY KEY,
    cbor_payload BLOB NOT NULL,
    relay_urls TEXT NOT NULL DEFAULT '[]',
    queue_scope TEXT NOT NULL DEFAULT 'generic',
    peer_pubkey TEXT,
    created_at INTEGER NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0
  )`,
  `CREATE TABLE IF NOT EXISTS relays (
    url TEXT PRIMARY KEY,
    read_enabled INTEGER NOT NULL DEFAULT 1,
    write_enabled INTEGER NOT NULL DEFAULT 1
  )`,
  `CREATE TABLE IF NOT EXISTS dm_inbox_relays (
    pubkey TEXT NOT NULL,
    relay_url TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (pubkey, relay_url)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_dm_inbox_relays_pubkey ON dm_inbox_relays(pubkey)`,
] as const;

export const LATE_USE_SCHEMA_STATEMENTS = [
  `CREATE TABLE IF NOT EXISTS user_follows (
    pubkey TEXT NOT NULL,
    follows_pubkey TEXT NOT NULL,
    pet_name TEXT,
    relay_hint TEXT,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (pubkey, follows_pubkey)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_follows_pubkey ON user_follows(pubkey)`,
  `CREATE INDEX IF NOT EXISTS idx_follows_target ON user_follows(follows_pubkey)`,
  `CREATE TABLE IF NOT EXISTS product_categories (
    id TEXT NOT NULL,
    pubkey TEXT NOT NULL,
    name TEXT NOT NULL,
    parent_id TEXT,
    level INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (id, pubkey)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_product_categories_pubkey ON product_categories(pubkey)`,
  `CREATE INDEX IF NOT EXISTS idx_product_categories_parent ON product_categories(pubkey, parent_id)`,
  `CREATE TABLE IF NOT EXISTS message_receipts (
    message_id TEXT NOT NULL,
    receipt_type TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (message_id, receipt_type)
  )`,
  `CREATE TABLE IF NOT EXISTS offline_queue (
    event_id TEXT PRIMARY KEY,
    cbor_payload BLOB NOT NULL,
    relay_urls TEXT NOT NULL DEFAULT '[]',
    queue_scope TEXT NOT NULL DEFAULT 'generic',
    peer_pubkey TEXT,
    created_at INTEGER NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0
  )`,
  `CREATE TABLE IF NOT EXISTS relays (
    url TEXT PRIMARY KEY,
    read_enabled INTEGER NOT NULL DEFAULT 1,
    write_enabled INTEGER NOT NULL DEFAULT 1
  )`,
  `CREATE TABLE IF NOT EXISTS dm_inbox_relays (
    pubkey TEXT NOT NULL,
    relay_url TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (pubkey, relay_url)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_dm_inbox_relays_pubkey ON dm_inbox_relays(pubkey)`,
] as const;
