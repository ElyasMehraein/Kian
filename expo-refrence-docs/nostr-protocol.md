# Kian Nostr Protocol Specification

## 1. Supported NIPs

The Kian system strictly relies on the following NIPs to facilitate a decentralized, contract-less, and offline-first marketplace:

* **NIP-01:** Basic protocol flow parameters.
* **NIP-02:** Follow List (Kind 3). Used heavily by the local Trader Sorting Engine (Web of Trust).
* **NIP-15:** Nostr Marketplace. Used for standardizing merchant product catalogs.
* **NIP-32:** Labeling. Used within Kind 31999 for structured trader reviews.
* **NIP-44:** Encrypted Payloads. Provides robust, versioned encryption for all private communications.
* **NIP-59:** Gift Wrap. Used to completely hide metadata (sender, receiver, timestamp) of all private interactions. Every chat message, order payload, and transfer request is wrapped using NIP-59.

*(Note: NIP-26 for delegation and NIP-17 have been completely removed from this architecture to maintain a lock-free, adminless environment).*

## 2. Core Event Kinds & Schemas

### 2.1 Profiles & Catalogs (Public Events)

* **Kind 0 (Metadata):** User and Trader profiles. A user is recognized as a Trader when their Kind 0 event contains the tag ["t", "trader"]. Traders MUST also include a geohash field in their profile JSON to enable the Spatial factor of the Trader Sorting Engine. The local DB field is_trader is a cached/derived boolean set from the presence of this tag.

* **Kind 3 (Follow List / NIP-02):** Standard follow list. The Trader Sorting Engine uses this to calculate the "Mutual Follows" factor.

* **Kind 30018 / 30017 (NIP-15 Catalog & Products):** Replaceable events defining merchant product listings. Stored locally in the products table.

* **Kind 31999 (All-in-One Trader Reviews):**
  A single Parameterized Replaceable Event per user that contains ALL of their trader reviews. When a user rates a new trader or updates an existing rating, the same event is replaced.
  This design ensures that anyone who fetches the Kind 31999 (d = kian_reviews) event of their followings has instant offline access to all their reviews.

  Structure:

  {
    "kind": 31999,
    "pubkey": "<reviewer_pubkey>",
    "tags": [
      ["d", "kian_reviews"],
      ["p", "<trader_pubkey_1>"],
      ["p", "<trader_pubkey_2>"],
      ["p", "<trader_pubkey_3>"]
    ],
    "content": "{\"<trader_pubkey_1>\": {\"rating\": 5, \"review\": \"Excellent quality and fast delivery!\"}, \"<trader_pubkey_2>\": {\"rating\": 3, \"review\": \"Average experience.\"}, \"<trader_pubkey_3>\": {\"rating\": 5, \"review\": \"Best trader in town!\"}}"
  }

  Rules:
  - The d tag is always "kian_reviews", ensuring only one instance per pubkey exists.
  - Each reviewed trader MUST also appear as a ["p", "<trader_pubkey>"] tag, enabling relays to index and serve this event when querying for a specific trader's reviews.
  - content is a JSON object keyed by trader pubkey. Each value contains rating (1-5) and review (string).
  - When the user rates a new (e.g., 501st) trader, the client updates the content JSON, adds the new p tag, increments the created_at timestamp, re-signs the event, and publishes. The relay replaces the old version.

  Offline Benefit: A client only needs to fetch one event per following to have all their reviews cached locally. When entering a trader's profile, the app instantly displays reviews from the user's social circle without any network request.

  Note: See trader_ratings table in db-schema.md for local storage of parsed review data.

### 2.2 Chat & E-Commerce (NIP-59 Wrapped)

All e-commerce flows (orders, negotiations) occur inside NIP-59 Gift Wraps, utilizing NIP-44 for encryption. Inside the decrypted seal, the following kinds are used:

* **Kind 14 (Direct Message):** Standard private text messages. When a user checks out their local Shopping Cart, the aggregated JSON order payload is sent as a formatted Kind 14 message.

* **Kind 1050 (Chat Transfer Request):** Sent by a user/trader within the chat thread to the token producer, requesting the transfer of a specific asset.

  {
    "kind": 1050,
    "content": "{\"asset_id\": \"ast_sugar_100\", \"qty\": 1, \"note\": \"Payment for order #123\"}",
    "tags": [["p", "<producer_pubkey>"]]
  }

### 2.3 Message Status Receipts (Transient Events)

To confirm message delivery and read status, clients MUST send ephemeral (transient) events. These events SHOULD NOT be stored by relays. They are direct, encrypted messages sent back to the original sender inside NIP-59 wrappers.

The lifecycle of a message from the sender's perspective:

1. "sending" -- Event created locally, not yet published (local state only).
2. "sent" -- Event successfully published to at least one relay (local state only).
3. "delivered" -- A Kind 20001 receipt was received from the recipient's client.
4. "read" -- A Kind 20002 receipt was received from the recipient's client.

These receipts are NOT stored as separate rows in the database. Upon receiving a Kind 20001 or 20002, the client simply updates the status field of the corresponding row in the chat_messages table (see db-schema.md).

* **Kind 20001: "Delivered" Receipt**
  Content: An encrypted string containing the event_id of the delivered message.
  Tags:
    ["p", "<original_sender_pubkey>"]
    ["e", "<original_message_id>"]
  Trigger: Sent automatically by the recipient's client as soon as it receives and successfully decrypts a Kind 14 or 1050 message.

* **Kind 20002: "Read" Receipt**
  Content: An encrypted string containing the event_id of the read message.
  Tags:
    ["p", "<original_sender_pubkey>"]
    ["e", "<original_message_id>"]
  Trigger: Sent by the recipient's client when the user opens the chat thread and the message becomes visible on their screen.

### 2.4 Token Lifecycle (UTXO-style)

Tokens in Kian are strictly non-fractional integer values. The complex lock/burn mechanisms have been replaced with a streamlined Genesis and Remint flow.

Note: See token_utxos table in db-schema.md for local persistence of UTXO state.

* **Kind 35001 (Genesis Issuance):** Issued by a producer to mint a new token class/batch.

  {
    "kind": 35001,
    "content": "{\"amount\": 100, \"unit\": \"kg\", \"note\": \"Initial sugar batch\"}",
    "tags": [["d", "ast_sugar_100"]]
  }

* **Kind 35002 (Remint / Transfer):** Executed ONLY by the original producer. Upon accepting a Kind 1050 request, the producer emits this event to nullify the previous UTXO state and re-assign the token to the new owner's pubkey. This event is sent directly back through the NIP-59 chat thread.

  {
    "kind": 35002,
    "content": "{\"previous_utxo\": \"<event_id>\", \"amount\": 1}",
    "tags": [
      ["a", "35001:<producer_pubkey>:ast_sugar_100"],
      ["p", "<new_owner_pubkey>"]
    ]
  }

## 3. Fallback Transport Layers (Zero-Internet)

To ensure system resilience during network outages, Kian utilizes an Offline State Engine:

Note: See offline_queue table in db-schema.md for local queue persistence.

* **Queueing:** Any event generated offline (NIP-59 messages, ratings, remints) is stored locally in the offline_queue database table.
* **CBOR Compression:** To drastically reduce the payload byte size for low-bandwidth transport, the JSON event is serialized and compressed using CBOR (Concise Binary Object Representation).
* **Transport:**
  * **Auto-Sync:** The background worker will automatically broadcast the CBOR-decoded events to Nostr relays once standard internet connectivity is restored.
  * **Fallback Mediums:** With explicit user consent, the highly compressed CBOR payload can be encoded and transmitted via multipart SMS or Acoustic FSK over a voice call.
