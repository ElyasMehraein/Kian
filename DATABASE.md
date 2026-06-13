# Database Schema

The Kian database is implemented using the **Room Persistence Library**. Below are the details of the tables and fields for each, based on the current project code.

## Entities and Tables

### 1. `profiles` (Profiles Table)
Stores information related to users and merchants.
- `pubkey` (PK): User's public key.
- `name`, `displayName`: Username and display name.
- `about`, `picture`: Description and profile picture.
- `nip05`, `geohash`: Verification info and geographic location.
- `isTrader`: Merchant status (Boolean).
- `rawJson`: Full content of the Kind 0 event.
- `createdAt`, `updatedAt`: Creation and update timestamps.

### 2. `chat_messages` (Chat Messages Table)
- `id` (PK): Event ID.
- `pubkey`: Message sender.
- `contactPubkey`: Public key of the other party in the conversation.
- `content`: Message content (decrypted).
- `kind`: Nostr event type.
- `isMine`: Whether the message was sent by the current user.
- `status`: Message status (`sent`, `delivered`, `read`).
- `createdAt`: Time the message was sent.

### 3. `conversations` (Conversation Summaries)
- `contactPubkey` (PK): Contact's public key.
- `lastMessage`: The last exchanged message.
- `lastTimestamp`: Time of the last activity.
- `unreadCount`: Number of unread messages.

### 4. `products` (Store Products)
- `id`, `pubkey` (Composite PK): Product ID and owner ID.
- `name`, `description`: Product specifications.
- `images`, `categories`: List of images and categories (as JSON).
- `geohash`: Geographic location of the product.
- `eventId`: Kind 30018 event ID.

### 5. `token_utxos` (Asset Management)
- `utxoId` (PK): Unique UTXO ID.
- `assetRef`: Reference to the respective asset.
- `producer`, `owner`: Producer and current owner.
- `amount`: Asset amount.
- `spent`: Whether this token has been spent (Boolean).

### 6. `offline_queue` (Offline Transmission Queue)
- `eventId` (PK): ID of the event awaiting transmission.
- `cborPayload`: Compressed content for offline transmission.
- `relayUrls`: List of target relays (JSON).
- `retryCount`: Number of retries for transmission.

### 7. `reviews` (Reviews and Ratings)
- `pubkey`, `targetPubkey` (Composite PK): Reviewer and target of the review.
- `rating`: Rating (1 to 5).
- `comment`: Review text.

---

## Technical Notes

- **Indexing**: Optimized indexes are created for columns such as `contactPubkey`, `owner`, and `pubkey` to maintain search speed with large data volumes.
- **Data Types**: JSON strings are used to store lists (like images), managed at the DAO layer.
- **Destructive Migration**: Due to the development phase, changes to any of these classes lead to clearing the old database and creating a new version.
