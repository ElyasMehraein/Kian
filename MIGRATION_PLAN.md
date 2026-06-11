# Detailed Refactoring Plan: Expo to Android Kotlin (Compose)

This plan is optimized for **UI Fidelity** and **Granular Implementation**. We will build the UI shells first to match the original design, then wire them to the offline-first logic.

## 1. Project Overview
- **UI Goal:** Pixel-perfect matching of the React Native (NativeWind) UI.
- **Architecture:** Clean Architecture + Hilt + Room + Flow.
- **Reference:** `/expo-refrence/app` for UI, `/expo-refrence/docs` for logic.

---

## Phase 1: UI Foundation & Design System (Completed)
Match the Tailwind-based design system in Compose.

- [x] **Task 1.1: KianTheme & Design Tokens**
- [x] **Task 1.2: Atomic UI Components**
- [x] **Task 1.3: Navigation & Scaffold**

---

## Phase 2: Screen Migration (Completed)
Create the screens and ensure the layout matches the original Expo screens.

- [x] **Task 2.1: Home Screen (Trader Listing)**
- [x] **Task 2.2: Wallet Screen**
- [x] **Task 2.3: Product Manager Screen**
- [x] **Task 2.4: Chat & Conversation Screens (REMOVED)**
- [x] **Task 2.5: Onboarding & Identity**
- [x] **Task 2.6: Private Key Management Screen**
- [x] **Task 2.7: Backup & Recovery Screen**

---

## Phase 3: Data Layer & Offline Logic (Completed)
Implement the core logic following `expo-refrence/src/db/schema.ts` and `expo-refrence/docs/db-schema.md`.

- [x] **Task 3.1: Room Database Setup**
- [x] **Task 3.2: Key Management & Cryptography**
  - [x] BIP39 mnemonic handling.
  - [x] Secp256k1 key derivation.
  - [x] Multi-device Sync (Self-Copy mechanism).
  - [x] **NIP-44** (Encryption) and **NIP-59** (Gift Wrap) implementation.
- [x] **Task 3.3: Secure Backup Engine**
  - [x] AES-GCM encrypted database export.
  - [x] Integration with Android Share Intent and FileProvider.

---

## Phase 4: Networking & Nostr Protocol (In Progress)
- [x] **Task 4.1: Relay Pool Manager**
- [x] **Task 4.2: Event Parsing (Kinds 0, 3, 31999, 30018)**
- [x] **Task 4.3: Message Status & Receipts (Kind 20001/20002)**
  - Implement transient receipts (Delivered/Read) with visual status in UI.
- [x] **Task 4.4: Conversation Deletion (Kind 15001) (REMOVED)**
  - Implement two-way secure deletion of chat history.
- [ ] **Task 4.5: DM Inbox Relays (Kind 10050)**
  - [x] Discovery logic in SyncManager.
  - [ ] Automatic routing for all NIP-17 private events.

---

## Phase 5: Trade & Tokenization (Upcoming)
Implementing the "Real World" commerce features from the Expo reference.

- [ ] **Task 5.1: Shopping Cart System**
  - [ ] Implement `CartItem` entity and `CartDao`.
  - [ ] Checkout logic: Convert cart to formatted JSON payload.
  - [ ] Send order as Kind 14 (Wrapped in NIP-59) to the merchant.
- [ ] **Task 5.2: Token Lifecycle (UTXO Engine)**
  - [ ] UI and logic for **Kind 35001 (Genesis)**: Minting new tokens from products.
  - [ ] Logic for **Kind 35002 (Remint)**: Producer-side approval of transfer requests.
- [ ] **Task 5.3: In-Chat Token Transfers (Kind 1050) (REMOVED)**
  - [x] Repository logic for Transfer Requests.
  - [ ] Integration with the Chat UI to show transfer status (Waiting/Fulfilled).

---

## Phase 6: Web of Trust & Discovery (Upcoming)
Refining the merchant discovery based on social circles.

- [ ] **Task 6.1: Aggregated Reviews (Kind 31999)**
  - [ ] Logic to parse the "All-in-One" reviews JSON.
  - [ ] Incremental updates to the local `reviews` table.
- [ ] **Task 6.2: Mutual Follows Calculation**
  - [ ] Background sync of Kind 3 (Follow lists) for all followings.
  - [ ] Integration with `MerchantRankingEngine` for social scoring.

---

## Instructions for AI
- **Current Focus**: Phase 5.1 (Shopping Cart).
- **Security**: All commerce events MUST be wrapped in NIP-59.
- Use `MockData` for ViewModels only when real repository data is not yet available.
