# Kian Architecture & Workflows
*Last updated: 1405/01/04*

## 1. System Components
Kian is designed as a strict Offline-First, Contract-less, Chat-driven application.

*   Local Data Store: SQLite handling Profiles, Local Shopping Carts, Chat Threads,
    Token UTXOs, and Trader Ratings cache. Each user's phone is their own Source of
    Truth. (Schema: db-schema.md)

*   Cryptography Engine: Utilizes NIP-44 for payload encryption and NIP-59 (Gift Wrap)
    to obfuscate metadata (sender/receiver/time) from relays. All chat messages and
    token operations are wrapped — no relay can determine who is talking to whom.

*   Offline State Engine: Serializes JSON events into highly compressed CBOR payloads
    for transmission over SMS or Acoustic FSK.
    Pipeline: JSON -> CBOR -> Base64 (SMS) | FSK (Acoustic).

*   Trader Sorting Engine: A Web-of-Trust algorithm sorting merchants on the home
    screen based on 3 factors:
    1) Mutual Follows — how many of my followings (NIP-02) also follow this trader
    2) Spatial Distance — Geohash proximity between user and trader
    3) Social Rating — average Kind 31999 rating given by my followings
       (not global average)

*   Message Receipt Engine: Lightweight delivery/read tracking using transient events
    (Kind 20001 / Kind 20002). These events are never stored — they only trigger a
    status field update on the corresponding chat_messages row.
    (Details: nostr-protocol.md section 2.3)


## 2. Trader Identity & Discovery

A user becomes a Trader by adding the tag ["t", "trader"] to their Kind 0 (Profile)
event. There is no approval process or central authority.

*   is_trader field: The user_profiles.is_trader column in SQLite is a cached/derived
    value. Whenever the client receives or processes a Kind 0 event, it checks for the
    presence of ["t", "trader"] and sets the boolean accordingly.

*   Home Screen: Only users with is_trader = true appear in the trader listing on the
    home screen.

*   Rating Titles: Based on the average rating from the user's social circle (followings):
    - >= 4 stars   ->  KianBan (kian-baan)
    - >= 3 and < 4 ->  BazarGardan (Market Maker)
    - < 3 stars    ->  Tajer (Trader)

### Kind 31999 — All-in-One Reviews

Each user maintains exactly one replaceable Kind 31999 event with d = "kian_reviews"
that contains ALL their ratings for ALL traders in a single JSON content.

    Nostr Event (Kind 31999)
      pubkey: <rater>
      tags: [["d","kian_reviews"], ["p","<trader_1>"], ["p","<trader_2>"], ...]
      content: JSON {
        "<trader_1_pubkey>": {"rating": 5, "review": "great seller"},
        "<trader_2_pubkey>": {"rating": 3, "review": "slow delivery"}
      }

Why All-in-One?
The client fetches one event per following, parses the JSON, and caches rows in the
trader_ratings table. When the user opens any trader's profile, ratings from their
social circle are displayed instantly from local cache with zero network requests.
This is critical for offline-first UX.

Update flow:
1. User rates a new trader (or edits an existing rating).
2. Client reads current Kind 31999 from local DB, modifies the JSON, publishes a
   new Kind 31999 with the same d-tag. The relay replaces the old one automatically.
3. Client runs DELETE FROM trader_ratings WHERE rater_pubkey = ? then re-inserts
   all rows from the updated JSON.

(DB details: trader_ratings table in db-schema.md)
(Protocol details: nostr-protocol.md section 2.1)


## 3. E-Commerce & Shopping Cart Workflow

Unlike traditional smart-contract flows, Kian uses a standard local cart combined
with chat:

1. Browsing: User views a Trader's NIP-15 product catalog (Kind 30017 stalls /
   Kind 30018 products).
2. Cart: User adds items to their local shopping_cart_items table. This data never
   leaves the device until checkout.
3. Checkout: The cart is serialized into a formatted JSON string and sent to the
   Trader inside a private Chat Message (Kind 14, wrapped in NIP-59).
4. Negotiation: Trader and User negotiate in chat. Trader may send counter-offers
   or confirm availability.


## 4. Token Transfer Workflow (Chat-Based)

Tokens (UTXOs) are non-fractional and can only be re-issued by their original
producer.

1. Minting (Kind 35001): A producer creates tokens backed by their products.
   The token event names a specific owner_pubkey.
2. Direct Send: If the owner IS the producer, they can simply mint a new
   Kind 35001 for the recipient.
3. Transfer Request (Kind 1050): To transfer a token produced by someone else,
   the current owner sends a NIP-59 encrypted Chat Message containing a Kind 1050
   (Transfer Request) directed at the Token Producer.
4. Producer Approval (Kind 35002): The Producer receives the Kind 1050 in their
   chat. If valid, they mark the old UTXO as spent and emit a Kind 35002 (Remint)
   assigning the token to the new owner.
5. Completion: The recipient receives the Kind 35002 event, validates the
   producer's signature, and the token becomes unspent in their local wallet.
6. Offline Caveat: If the producer is offline, the transfer stays in
   pending_transfer status. The Kind 1050 sits in offline_queue until
   connectivity is restored or manually transported.


## 5. Chat Message Lifecycle & Receipts

Every chat message progresses through four status stages:

    sending -----> sent -----> delivered -----> read

- sending:   Event created in local DB, not yet published to any relay.

- sent:      At least one relay responded with OK. Status updated locally.

- delivered: Recipient's client successfully decrypts the message and automatically
             sends a Kind 20001 transient event back to the sender. This event is
             NOT stored in any table — it only triggers an UPDATE on
             chat_messages.status to 'delivered'.

- read:      Recipient opens the chat screen. Their client sends a Kind 20002
             transient event back to the sender. Again NOT stored — only triggers
             an UPDATE on chat_messages.status to 'read'.

Important notes:

- Kind 20001 and 20002 are transient (ephemeral) events per Nostr convention
  (prefix 2xxxx). Relays MAY discard them after delivery.

- Receipt events are also wrapped in NIP-59 Gift Wrap to preserve metadata
  privacy. No relay can correlate a receipt to its original message.

- If the sender is offline when receipts arrive, the status update happens
  upon next sync.

(Protocol details: nostr-protocol.md section 2.3)


## 6. Zero-Internet Fallback

If there is no internet connectivity:

1. The App generates the NIP-59 Gift Wrap or NIP-44 encrypted event as usual.
   The event is fully valid and signed — it just cannot reach a relay yet.

2. The JSON event is converted to CBOR (Concise Binary Object Representation)
   to minimize byte size. Typical compression ratio: 40-60% smaller than JSON.

3. The CBOR binary is stored as a BLOB in the offline_queue table with
   status = 'queued'.

4. The user chooses a transport method:

   - SMS: CBOR binary is Base64-encoded, then chunked into multipart SMS
     messages (each around 140 bytes). Reassembled at destination.

   - Acoustic FSK: CBOR binary is modulated into audio frequencies and
     transmitted over a standard phone call. The receiving device demodulates
     the audio back into binary.

   - Manual transfer: The user can also move the event via:
     -- Messenger apps (Telegram, WhatsApp, etc.) as a file or text
     -- Bluetooth or Wi-Fi Direct
     -- USB flash drive
     -- QR code (for small events)
     -- Handwritten Base64 on paper (extreme fallback)

5. Upon successful delivery confirmation, the offline_queue row is updated
   to status = 'delivered'.

6. UI indicators:
   - A "pending" icon appears next to unsent messages in chat.
   - Tapping the icon reveals available offline transport options.
   - A dedicated "Outbox Queue" screen (accessible from settings) shows all
     queued events with their transport status and lets the user manually
     retry or change transport method.

(DB details: offline_queue table in db-schema.md)


