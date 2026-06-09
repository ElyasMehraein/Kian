# Kian Agent Guide

Read this first before any patch, bugfix, or feature work. Update it when routing or ownership changes.

## Project Snapshot
- Product: tokenized barter marketplace on Nostr
- Stack: Expo, TypeScript, local DB, Nostr protocol
- Runtime: mobile-first, offline-first
- DB is the source of truth
- Realtime comes from relay/websocket flows
- Cart is UI/workflow state only

## Global Rules
- Investigate reported problems at the root cause level and prefer the smallest coherent fix that simplifies the affected flow, removes ineffective or excess code, keeps module boundaries clean, and leaves the subsystem more isolated, readable, and maintainable instead of layering patch on top of patch.
- If a prior patch did not fix the bug, revert that ineffective patch before continuing unless the user says otherwise.
- Read only the relevant subsystem first; avoid scanning unrelated areas unless blocked.
- Do not combine unrelated fixes, broad cleanup, or speculative refactors.
- Reuse existing helpers and modules before creating new ones.
- Prefer pure functions, type-safe changes, small diffs, and files under 200 lines when practical.

## Product Invariants
- Chat drives barter, requests, receipts, and transfer flows.
- All private messages and transfer requests are wrapped private events.
- Token amounts are integers only.
- Producer authority controls mint, burn, split, and transfer finalization.
- Ownership is not final until the producer-issued token event arrives.
- Merchants only differ by being shown in the home merchants list.
- Profile metadata uses `display_name`; do not add or depend on `username` in kind `0` metadata.

## Ask Before Changing
- user-visible behavior
- DB schema, migrations, or persistence shape
- Nostr event kinds, tags, payloads, or subscription behavior
- event-driven flows replaced with timers/polling
- logic moved between UI/store/service/handler/DB/crypto/relay layers
- any second mechanism added only to mask a bug
- native behavior weakened to make web easier
- cryptographic behavior

## Styling Rules
- Use NativeWind/Tailwind for screen styling.
- Theme tokens live in `tailwind.config.js`.
- Global Tailwind entrypoint is `global.css`.
- Prefer `className` over `StyleSheet.create` unless native APIs require inline styles.

## Layer Ownership
- `app/`: screens, navigation, local UI interactions
- `src/bootstrap/`: startup order and readiness wiring
- `src/stores/`: cache, session, UI state only
- `src/services/`: workflows and orchestration
- `src/handlers/`: incoming events and event-driven state transitions
- `src/db/`: schema, init, repos, migrations, local truth
- `src/builders/`: outgoing event construction
- `src/crypto/`: keys, signing, encryption, wrapping
- `src/nostr/`: relays, subscriptions, dispatch
- `src/utils/`: pure helpers
- `src/types/`: shared contracts and constants

Fix issues in the owning subsystem. Do not make one layer compensate for another unless approved.

## Routing Map
### Startup / session / first screen
Read first:
- `app/index.tsx`
- `src/bootstrap/index.ts`
- `src/db/init.ts`
- `src/db/init.web.ts`
- `src/stores/session.ts`
- `src/stores/ui.ts`
Owns DB init, session restore, initial relay connection, first-screen navigation.

### Onboarding / keys / logout
Read first:
- `app/onboarding.tsx`
- `app/private-key.tsx`
- `src/crypto/keys.ts`
- `src/db/repos/keys.ts`
- `components/app-menu.tsx`
Owns key generation/restore, secret persistence, logout/session clearing.

### Profiles / merchant discovery / reviews
Read first:
- `app/(tabs)/home.tsx`
- `app/user/[pubkey].tsx`
- `app/review/[pubkey].tsx`
- `src/handlers/metadata.ts`
- `src/handlers/follow-list.ts`
- `src/handlers/review.ts`
- `src/services/merchant-ranking.ts`
- `src/db/repos/profiles.ts`
- `src/db/repos/reviews.ts`
Rules:
- home merchant cards show avatar, `display_name`, truncated pubkey, bio
- mirror local web DB changes in `src/db/init.web.ts`

### Products
Read first:
- `app/products/manage.tsx`
- `app/user/[pubkey].tsx`
- `src/builders/product.ts`
- `src/handlers/product.ts`
- `src/db/repos/products.ts`
Owns product create/edit, ingestion, local persistence.

### Cart
Read first:
- `app/cart.tsx`
- `src/services/cart.ts`
- `src/stores/cart.ts`
Rules: cart stores merchant-specific request drafts, sends chat requests, and leaves approval/rejection to chat.

### Chat / conversations / receipts / offline send
Read first:
- `app/chat/[pubkey].tsx`
- `app/chat/_components/message-status-indicator.tsx`
- `src/hooks/use-chat-conversation.ts`
- `src/builders/chat-message.ts`
- `src/builders/delivered-receipt.ts`
- `src/builders/read-receipt.ts`
- `src/services/chat.ts`
- `src/services/chat-transport.ts`
- `src/services/dm-relays.ts`
- `src/services/message-events.ts`
- `src/services/subscriptions.ts`
- `src/handlers/chat-message.ts`
- `src/handlers/delivered.ts`
- `src/handlers/dm-relay-list.ts`
- `src/handlers/read.ts`
- `src/db/repos/messages.ts`
- `src/db/repos/conversations.ts`
- `src/db/repos/receipts.ts`
- `src/db/repos/dm-inbox-relays.ts`
- `src/crypto/nip59.ts`
Rules:
- keep status changes event-driven
- do not add polling/refresh timers without approval
- mirror `messages` and `conversations` behavior in `src/db/init.web.ts`
- offline banner appears only with an actual pending outgoing message
- conversation deletion is a two-party PM deletion workflow
- NIP-17 delivery uses explicit DM inbox relays; do not fall back to arbitrary relays for private events

### NIP-17 private chat method
Use this model when touching chat, receipts, relay discovery, or offline DM handoff.

Protocol shape:
- NIP-17 private chat uses an unsigned inner event ("rumor"), usually kind `14`, addressed with a `p` tag to the peer.
- The rumor is encrypted into a NIP-59 seal, which is then encrypted again into a NIP-59 gift wrap.
- The only event published to relays is the outer gift wrap, kind `1059`.
- The recipient discovers where to receive gift wraps from the sender's kind `10050` DM inbox relay list.
- In this app, delivered and read receipts are app-specific private events with kinds `20001` and `20002`, but they follow the same NIP-17 wrapping path: unsigned inner event -> signed seal -> signed gift wrap.

Implementation in this app:
- Sender flow: build the unsigned chat or receipt event, wrap it with `src/crypto/nip59.ts`, publish the recipient copy to the peer's DM inbox relays, and publish a self-copy to the sender's own inbox relays so the conversation can be reconstructed locally from relay traffic.
- Receiver flow: subscribe to gift wraps addressed to the local pubkey, unwrap with `src/crypto/nip59.ts`, compute the inner event id from the unsigned rumor, route by inner kind, persist the resulting message or receipt state, and emit message events for the UI.
- Relay discovery flow: ingest kind `10050` relay-list events into local DB/cache, publish this user's own kind `10050` event during bootstrap, and use those relay lists for DM send/receive routing.
- Offline flow: if a gift wrap cannot be published to the target inbox relays, queue the outer wrap in the local offline queue and optionally hand the encoded payload to SMS. Do not change offline fallback to bypass the NIP-17 relay model without approval.

Related files and responsibilities:
- `src/crypto/nip59.ts`: owns NIP-59 wrapping and unwrapping. Keeps the inner event unsigned, signs the seal and wrap, decrypts incoming wraps, and computes the inner event id after unwrap.
- `src/builders/chat-message.ts`: builds the unsigned inner kind `14` rumor for chat messages and applies the peer `p` tag plus app-specific message-type tags.
- `src/builders/delivered-receipt.ts`: builds the unsigned inner kind `20001` delivered-receipt rumor.
- `src/builders/read-receipt.ts`: builds the unsigned inner kind `20002` read-receipt rumor.
- `src/builders/dm-relay-list.ts`: builds the signed kind `10050` DM inbox relay-list event describing where this user accepts private gift wraps.
- `src/services/chat.ts`: orchestrates private send, self-copy publish, read/delivered receipt publish, conversation delete, and offline SMS handoff entrypoints.
- `src/services/chat-transport.ts`: publishes outer relay events, waits for relay connectivity, queues failed outer wraps in `offline_queue`, and encodes/decodes offline payloads.
- `src/services/dm-relays.ts`: owns DM inbox relay cache, local persistence, lookup for private sends, and publishing this user's own kind `10050` relay list.
- `src/services/subscriptions.ts`: subscribes to kind `1059` gift wraps addressed to the local pubkey and to kind `10050` relay-list events needed for DM routing.
- `src/handlers/chat-message.ts`: unwraps incoming gift wraps, routes inner kinds, stores messages, updates conversations, and emits delivered receipts for inbound messages.
- `src/handlers/delivered.ts`: parses unwrapped delivered receipts and advances local message status to `delivered`.
- `src/handlers/read.ts`: parses unwrapped read receipts and advances local message status to `read`.
- `src/handlers/dm-relay-list.ts`: parses incoming kind `10050` relay-list tags and updates the DM inbox relay cache/persistence.
- `src/db/repos/messages.ts`: persists chat messages, message status, and transport metadata for relay deletion/offline recovery.
- `src/db/repos/conversations.ts`: persists conversation summaries, unread counts, and conversation deletion markers.
- `src/db/repos/receipts.ts`: deduplicates delivered/read receipts so handlers and senders stay event-driven and idempotent.
- `src/db/repos/dm-inbox-relays.ts`: persists DM inbox relay lists learned from kind `10050` events for bootstrap hydration and routing.
- `src/bootstrap/index.ts`: hydrates cached DM inbox relays, connects relays, publishes this user's own kind `10050` relay list, then starts subscriptions.
- `app/chat/[pubkey].tsx`: chat screen entrypoint; loads the conversation, marks inbound messages as read, and shows offline-send UI only when there is a real pending outgoing message.
- `src/hooks/use-chat-conversation.ts`: reloads a conversation from local DB in response to event-driven message updates and applies deletion filtering.

NIP-17 guardrails:
- Do not sign the inner rumor.
- Do not publish private kind `14`, `20001`, or `20002` events directly to relays; only publish the outer kind `1059` gift wrap.
- Do not send private events to default/global relays when no DM inbox relay list is known for the peer.
- Keep send/receipt status transitions event-driven through incoming wrapped events and local receipt dedupe.
- If web or DB behavior for chat changes, mirror the relevant local DB behavior in `src/db/init.web.ts`.

### Wallet / tokens / producer-mediated transfer
Read first:
- `app/(tabs)/wallet.tsx`
- `src/services/wallet.ts`
- `src/builders/transfer-request.ts`
- `src/handlers/transfer-request.ts`
- `src/handlers/token-utxo.ts`
- `src/handlers/token-definition.ts`
- `src/db/repos/utxos.ts`
- `src/db/repos/token-definitions.ts`
Rules: non-producer transfers stay waiting until producer event arrives; never finalize ownership locally.

### Relay / dispatch / realtime
Read first:
- `src/services/subscriptions.ts`
- `src/nostr/relayPool.ts`
- `src/nostr/dispatcher.ts`
- `src/handlers/index.ts`
- `src/stores/ui.ts`
Owns relay state, subscriptions, and dispatch into handlers.

### DB / schema / web parity
Read first:
- `src/db/schema.ts`
- `src/db/migrations.ts`
- `src/db/types.ts`
- `src/db/init.ts`
- `src/db/init.web.ts`
- relevant `src/db/repos/*`
Rules:
- prefer repo/query fixes over schema changes
- if web behavior reads/writes local DB, update `src/db/init.web.ts` in the same patch
- do not bump DB version or add migrations unless explicitly requested

### Crypto / signing / encoding
Read first:
- `src/crypto/keys.ts`
- `src/crypto/sign.ts`
- `src/crypto/nip44.ts`
- `src/crypto/nip59.ts`
- `src/utils/event.ts`
- `src/utils/bech32.ts`
- `src/utils/encoding.ts`
Rule: be conservative and ask before changing crypto behavior.

## Event Kinds
- `0`: profile
- `3`: follow list
- `14`: DM, encrypted and wrapped
- `1050`: token transfer request
- `20001`: delivered receipt
- `20002`: read receipt
- `30017` / `30018`: product-related events
- `31999`: merchant reviews
- `35001`: token genesis
- `35002`: token transfer

## Verification
- Run the smallest relevant check first.
- If types changed, run `npx tsc --noEmit`.
- If web behavior changed, run a relevant web validation.
- If startup/build changed, run the relevant Expo command.
- Report only the checks you actually performed.

## Final Rule
Keep subsystems isolated, patch the owning area, preserve handoff boundaries, and ask before changing behavior or architecture.
