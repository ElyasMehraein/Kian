# AI Agent Guidelines & Project Context

## 1. Project Identity
This project is a **Native Android (Kotlin + Jetpack Compose)** refactor of an existing **Expo (React Native)** application.

## 2. Source of Truth
The directory `/expo-refrence` is the absolute source of truth for:
- **UI Design:** Pixel-perfect matching of layouts, spacing, and components.
- **Design System:** All colors, typography, and tokens must strictly match `expo-refrence/tailwind.config.js`.
- **Database Schema:** Tables, fields, and relationships must be strictly modeled after `expo-refrence/src/db/schema.ts`.
- **Data Logic:** Business logic for data handling (Repositories) must follow `expo-refrence/src/db/repos/`.
- **Logic & Protocol:** All business logic and Nostr NIP implementations (NIP-44, NIP-59, etc.) must be ported from the Expo source code (refer to `expo-refrence/src/services/` and `expo-refrence/docs/`).

## 3. Core Principles
- **Money-less System:** Kian is a decentralized marketplace that does not use traditional fiat or crypto currencies. It uses custom **Assets/Tokens** managed via a **UTXO model**.
- **Offline-First:** The local Room database is the primary source of truth. All operations should work without internet connectivity, queueing events for later broadcast.
- **Nostr-Based:** All communication (Chat, Orders, Reviews) happens over the Nostr protocol using specific Kinds (14, 1050, 31999, 30018, 35001, 35002).
- **Privacy by Default:** Use **NIP-59 (Gift Wrap)** and **NIP-44** for all private interactions to hide metadata and content.

## 4. UI Implementation Rules
- Always use `KianTheme.colors` to access design tokens (`canvas`, `ink`, `line`, `panel`, `accent`, `accentSoft`, `muted`, `danger`).
- Do NOT hardcode colors or use standard Material 3 colors unless they perfectly align with the design system.
- Support both **Light** and **Dark** modes as defined in `Theme.kt`.

## 5. File Structure Reference
- `/app/src/main/java/com/ely/kian/ui`: UI components and screens.
- `/app/src/main/java/com/ely/kian/data/local`: Room DB, Entities, and DAOs (Aligned with `expo-refrence/src/db/`).
- `/app/src/main/java/com/ely/kian/data/remote`: Nostr networking and Relay management.
- `/app/src/main/java/com/ely/kian/crypto`: BIP39, Secp256k1, and NIP implementations.
- `/app/src/main/java/com/ely/kian/services`: Business logic (e.g., Ranking engine).
