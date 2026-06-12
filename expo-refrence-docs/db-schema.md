-- Kian Offline-First SQLite Schema (Chat \& Cart Centric)



-- 1. Profiles \& Reputation

CREATE TABLE IF NOT EXISTS user\_profiles (

&nbsp;  pubkey TEXT PRIMARY KEY,

&nbsp;  name TEXT,

&nbsp;  about TEXT,

&nbsp;  geohash TEXT,

&nbsp;  -- is\_trader is a CACHED/DERIVED field.

&nbsp;  -- Source of truth: presence of tag \["t", "trader"] in user's Kind 0 event.

&nbsp;  -- Updated locally whenever a Kind 0 event is received/processed.

&nbsp;  is\_trader BOOLEAN DEFAULT false,

&nbsp;  updated\_at INTEGER                -- Kind 0 event created\_at (unix timestamp)

);



CREATE TABLE IF NOT EXISTS user\_follows (

&nbsp;  follower\_pubkey TEXT,

&nbsp;  followed\_pubkey TEXT,

&nbsp;  created\_at INTEGER,

&nbsp;  PRIMARY KEY (follower\_pubkey, followed\_pubkey),

&nbsp;  FOREIGN KEY (follower\_pubkey) REFERENCES user\_profiles(pubkey),

&nbsp;  FOREIGN KEY (followed\_pubkey) REFERENCES user\_profiles(pubkey)

);



-- trader\_ratings stores the PARSED content of Kind 31999 events.

-- Design: Each user has exactly ONE Kind 31999 event (d-tag = "kian\_reviews")

--         containing ALL their ratings for ALL traders in a single JSON.

--         This table is a local cache: the client parses that JSON and

--         inserts/replaces one row per (rater, trader) pair.

--

-- Example Kind 31999 content (JSON inside event.content):

--   {"<trader\_pubkey\_1>": {"rating": 5, "review": "..."}, 

--    "<trader\_pubkey\_2>": {"rating": 4, "review": "..."}}

--

-- On receiving a newer Kind 31999 from a user, the client:

--   1. DELETEs all rows WHERE rater\_pubkey = <that user>

--   2. Re-INSERTs from the new JSON content



CREATE TABLE IF NOT EXISTS trader\_ratings (

&nbsp;  rater\_pubkey  TEXT NOT NULL,       -- pubkey of the user who rates

&nbsp;  trader\_pubkey  TEXT NOT NULL,       -- pubkey of the trader being rated

&nbsp;  rating INTEGER CHECK(rating >= 1 AND rating <= 5),

&nbsp;  review TEXT,

&nbsp;  event\_created\_at INTEGER,          -- created\_at of the Kind 31999 event

&nbsp;  PRIMARY KEY (rater\_pubkey, trader\_pubkey),

&nbsp;  FOREIGN KEY (rater\_pubkey) REFERENCES user\_profiles(pubkey),

&nbsp;  FOREIGN KEY (trader\_pubkey) REFERENCES user\_profiles(pubkey)

);



CREATE INDEX idx\_trader\_ratings\_trader ON trader\_ratings(trader\_pubkey);

CREATE INDEX idx\_trader\_ratings\_rater  ON trader\_ratings(rater\_pubkey);



-- ═══════════════════════════════════════════════════════════

-- 2. Products \& Catalog (NIP-15)

-- ═══════════════════════════════════════════════════════════



CREATE TABLE IF NOT EXISTS products (

&nbsp;  product\_id TEXT PRIMARY KEY,       -- d-tag of the Kind 30018 event

&nbsp;  trader\_pubkey TEXT NOT NULL,

&nbsp;  title TEXT NOT NULL,

&nbsp;  description TEXT,

&nbsp;  price\_msat INTEGER,

&nbsp;  currency TEXT,

&nbsp;  FOREIGN KEY (trader\_pubkey) REFERENCES user\_profiles(pubkey)

);



-- ═══════════════════════════════════════════════════════════

-- 3. Local Shopping Cart (client-only, never published to relays)

-- ═══════════════════════════════════════════════════════════



CREATE TABLE IF NOT EXISTS shopping\_cart\_items (

&nbsp;  id TEXT PRIMARY KEY,

&nbsp;  user\_pubkey TEXT NOT NULL,

&nbsp;  trader\_pubkey TEXT NOT NULL,

&nbsp;  product\_id TEXT NOT NULL,

&nbsp;  quantity INTEGER NOT NULL DEFAULT 1,

&nbsp;  added\_at DATETIME DEFAULT CURRENT\_TIMESTAMP,

&nbsp;  FOREIGN KEY (product\_id) REFERENCES products(product\_id)

);



CREATE INDEX idx\_cart\_user\_trader ON shopping\_cart\_items(user\_pubkey, trader\_pubkey);



-- ═══════════════════════════════════════════════════════════

-- 4. Chat \& Messages (NIP-59 Gift Wrap / NIP-44 Encryption)

-- ═══════════════════════════════════════════════════════════



-- Message Receipts Strategy (Kind 20001 / 20002):

--   These are TRANSIENT events (ephemeral, prefix 2xxxx) and are

--   NOT stored in any table. They only trigger an UPDATE on

--   chat\_messages.status:

--     • Kind 20001 (delivered receipt) → status = 'delivered'

--     • Kind 20002 (read receipt)      → status = 'read'

--   See: nostr-protocol.md section 2.3



CREATE TABLE IF NOT EXISTS chat\_messages (

&nbsp;  event\_id TEXT PRIMARY KEY,

&nbsp;  thread\_id TEXT NOT NULL,           -- deterministic: sorted(pubkey\_a, pubkey\_b)

&nbsp;  sender\_pubkey TEXT,

&nbsp;  receiver\_pubkey TEXT,

&nbsp;  message\_type TEXT,                 -- 'text', 'cart\_order', 'token\_transfer', 'transfer\_request'

&nbsp;  decrypted\_content TEXT,

&nbsp;  -- Status lifecycle: sending → sent → delivered → read

&nbsp;  --   sending   : event created locally, not yet sent to any relay

&nbsp;  --   sent      : successfully published to ≥1 relay (OK response)

&nbsp;  --   delivered : Kind 20001 transient event received from recipient

&nbsp;  --   read      : Kind 20002 transient event received from recipient

&nbsp;  status TEXT CHECK(status IN ('sending', 'sent', 'delivered', 'read')) DEFAULT 'sending',

&nbsp;  created\_at INTEGER,

&nbsp;  FOREIGN KEY (sender\_pubkey) REFERENCES user\_profiles(pubkey),

&nbsp;  FOREIGN KEY (receiver\_pubkey) REFERENCES user\_profiles(pubkey)

);



CREATE INDEX idx\_chat\_threads ON chat\_messages(thread\_id, created\_at);



-- ═══════════════════════════════════════════════════════════

-- 5. Token UTXOs (Kind 35001 = Mint, Kind 35002 = Transfer)

-- ═══════════════════════════════════════════════════════════



CREATE TABLE IF NOT EXISTS token\_utxos (

&nbsp;  utxo\_id TEXT PRIMARY KEY,          -- event\_id of the Kind 35001 or 35002

&nbsp;  asset\_id TEXT NOT NULL,            -- identifies the product/asset type

&nbsp;  owner\_pubkey TEXT NOT NULL,        -- current owner (named in the token)

&nbsp;  producer\_pubkey TEXT NOT NULL,     -- original producer/issuer of the token

&nbsp;  amount INTEGER NOT NULL,

&nbsp;  -- Status lifecycle:

&nbsp;  --   unspent          : token is valid and spendable by owner

&nbsp;  --   spent            : token has been consumed (replaced by a new 35002)

&nbsp;  --   pending\_transfer : transfer requested (Kind 1050 sent), awaiting

&nbsp;  --                      producer to issue new Kind 35002

&nbsp;  status TEXT CHECK(status IN ('unspent', 'spent', 'pending\_transfer')) DEFAULT 'unspent',

&nbsp;  created\_at DATETIME DEFAULT CURRENT\_TIMESTAMP

);



CREATE INDEX idx\_token\_owner ON token\_utxos(owner\_pubkey, status);



-- ═══════════════════════════════════════════════════════════

-- 6. Offline Queue (CBOR / FSK / SMS Ready)

-- ═══════════════════════════════════════════════════════════



-- Events that need to be published but have no internet connectivity.

-- The user can manually choose a transport method from the UI.

-- Pipeline: JSON event → CBOR compress → Base64 (for SMS) or FSK (acoustic)



CREATE TABLE IF NOT EXISTS offline\_queue (

&nbsp;  id INTEGER PRIMARY KEY AUTOINCREMENT,

&nbsp;  event\_id TEXT UNIQUE NOT NULL,

&nbsp;  cbor\_payload BLOB NOT NULL,        -- highly compressed CBOR representation

&nbsp;  transport\_type TEXT CHECK(transport\_type IN ('auto\_nostr', 'sms', 'acoustic')) DEFAULT 'auto\_nostr',

&nbsp;  status TEXT CHECK(status IN ('queued', 'transmitting', 'delivered', 'failed')) DEFAULT 'queued',

&nbsp;  queued\_at DATETIME DEFAULT CURRENT\_TIMESTAMP

);



