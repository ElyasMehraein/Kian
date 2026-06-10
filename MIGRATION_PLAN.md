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
- [x] **Task 2.4: Chat & Conversation Screens**
- [x] **Task 2.5: Onboarding & Identity**
- [x] **Task 2.6: Private Key Management Screen**

---

## Phase 3: Data Layer & Offline Logic (In Progress)
Implement the core logic following `expo-refrence/src/db/schema.ts` and `expo-refrence/docs/db-schema.md`.

- [x] **Task 3.1: Room Database Setup**
- [x] **Task 3.2: Key Management & Cryptography**
  - [x] BIP39 mnemonic handling.
  - [x] Secp256k1 key derivation.
  - [x] Multi-device Sync (Self-Copy mechanism).
  - [/] **NIP-44** (Encryption) and **NIP-59** (Gift Wrap) - *Core structure implemented, full crypto library integration pending*.
- [x] **Task 3.3: WoT Ranking Engine**

---

## Phase 4: Networking & Nostr Protocol (In Progress)
- [x] **Task 4.1: Relay Pool Manager**
- [x] **Task 4.2: Event Parsing (Kinds 0, 3, 31999, 30018)**
- [x] **Task 4.3: Message Status & Receipts (Kind 20001/20002)**
  - Implement transient receipts (Delivered/Read) with visual tick icons in UI.
- [x] **Task 4.4: Conversation Deletion (Kind 15001)**
  - Implement two-way secure deletion of chat history.

---

## Phase 5: Trade & Tokenization (Upcoming)
Implementing the "Real World" commerce features from the Expo reference.

- [ ] **Task 5.1: Token Lifecycle (UTXO Engine)**
  - Implement Kind 35001 (Genesis) and 35002 (Remint).
  - Logic for tracking owned assets in the `token_utxos` table.
- [ ] **Task 5.2: In-Chat Token Transfers (Kind 1050)**
  - UI for requesting/sending tokens directly within the chat thread.
  - Integration with the Wallet balance.
- [ ] **Task 5.3: Product Integration in Chat**
  - Send product cards from catalog to chat.
  - Order flow (Kind 14/15 formatted payloads).
- [ ] **Task 5.4: Offline Transport (Zero-Internet)**
  - CBOR compression of events.
  - SMS transport for encrypted chat payloads.

---

## Instructions for AI
- **Current Focus**: Task 5.2 and 5.3 (Commerce in Chat).
- **Security**: All commerce events MUST be wrapped in NIP-59.
- Use `MockData` for ViewModels only when real repository data is not yet available.
