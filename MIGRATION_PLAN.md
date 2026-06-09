# Detailed Refactoring Plan: Expo to Android Kotlin (Compose)

This plan is optimized for **UI Fidelity** and **Granular Implementation**. We will build the UI shells first to match the original design, then wire them to the offline-first logic.

## 1. Project Overview
- **UI Goal:** Pixel-perfect matching of the React Native (NativeWind) UI.
- **Architecture:** Clean Architecture + Hilt + Room + Flow.
- **Reference:** `/expo-refrence/app` for UI, `/expo-refrence/docs` for logic.

---

## Phase 1: UI Foundation & Design System (Priority)
Match the Tailwind-based design system in Compose.

- [ ] **Task 1.1: KianTheme & Design Tokens**
  - Implement `Color.kt` with: `canvas (#ffffff)`, `ink (#111827)`, `line (#e5e7eb)`, `panel (#f8fafc)`, `accent (#2563eb)`, `accentSoft (#dbeafe)`.
  - Implement `Type.kt` (using Inter or similar sans-serif font matching the Expo look).
- [ ] **Task 1.2: Atomic UI Components**
  - `KianButton`: Primary (ink), Secondary (white/line), Soft (accentSoft).
  - `KianInput`: Bordered text field with specific rounded-xl corners.
  - `SortChip` / `SelectorChip`: Rounded-full chips with active/inactive states.
  - `MerchantCard`: Card with image/initial avatar, bio, and WoT metadata.
  - `InitialAvatar`: Circle with initials for users without pictures.
- [ ] **Task 1.3: Navigation & Scaffold**
  - Setup `NavHost` with Bottom Tabs: **Home, Wallet, Products, Chats**.
  - Implement `AppMenu` (Top/Side menu from `AppMenu.tsx`).

---

## Phase 2: Screen Migration (UI Shells with Mock Data)
Create the screens and ensure the layout matches the original Expo screens.

- [ ] **Task 2.1: Home Screen (Trader Listing)**
  - Port `home.tsx`. Implement the "Merchants" header and the Sort Chips row.
- [ ] **Task 2.2: Wallet Screen**
  - Port `wallet.tsx`. Implement "Token Balances", "Activity", and "Spendable entries" sections.
- [ ] **Task 2.3: Product Manager Screen**
  - Port `product-manager-screen.tsx`. Implement category filtering and the product list.
  - Implement the "Create/Edit Product" bottom sheet/overlay.
- [ ] **Task 2.4: Chat & Conversation Screens**
  - Port `chats.tsx` (Conversation list).
  - Port `chat/[pubkey].tsx` (Chat room with message bubbles and status icons).
- [ ] **Task 2.5: Onboarding & Identity**
  - Port `onboarding.tsx` and `private-key.tsx`.

---

## Phase 3: Data Layer & Offline Logic (Room)
Implement the core logic following `db-schema.md` and `architecture.md`.

- [ ] **Task 3.1: Room Database Setup**
  - Create entities: `UserProfile`, `Product`, `ChatMessage`, `TokenUtxo`, `OfflineQueue`.
  - Implement DAOs with specific queries (e.g., `listMerchants` with WoT sorting).
- [ ] **Task 3.2: Key Management & Cryptography**
  - Implement BIP39 mnemonic handling.
  - Implement Secp256k1 key derivation.
  - Port **NIP-44** (Encryption) and **NIP-59** (Gift Wrap) logic.
- [ ] **Task 3.3: WoT Ranking Engine**
  - Port the `rankMerchants` logic from `services/ranking.ts` to Kotlin.
  - Factors: Mutual Follows, Geohash Distance, Social Rating.

---

## Phase 4: Networking & Nostr Protocol
- [ ] **Task 4.1: Relay Pool Manager**
  - Implement WebSocket client using OkHttp/Ktor.
  - Handle Event subscription and publication.
- [ ] **Task 4.2: Event Parsing (Kinds 0, 3, 31999, 30018)**
  - Implement parsers for specific Nostr Kinds.
  - Implement the "All-in-One Review" (Kind 31999) logic.
- [ ] **Task 4.3: Message Status & Receipts**
  - Implement Kind 20001/20002 transient receipts to update `ChatMessage` status.

---

## Instructions for AI
- Focus on Phase 1 & 2 first to verify the UI.
- Use `MockData` for ViewModels during UI development.
- Always check `expo-refrence/tailwind.config.js` for styling details.
- Refer to `db-schema.md` for exact field names in Room entities.
