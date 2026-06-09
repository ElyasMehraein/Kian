# Project Kian Summary

## Goal
Migrating an **Offline-First**, Nostr-based e-commerce and chat application from Expo (React Native) to Native Android (Kotlin + Compose).

## Core Philosophy
- **UI Fidelity**: The Android app must look and feel identical to the Expo version (Tailwind/NativeWind style).
- **Offline-First**: Local Room DB is the source of truth. All logic assumes zero-internet availability as a primary state.
- **Privacy (Gift Wrap)**: Uses NIP-59 (Gift Wrap) and NIP-44 for all communications.
- **Web-of-Trust (WoT)**: Traders are ranked based on social circles and proximity, not global averages.

## Key Features
1. **Nostr Integration**: Profiles, Reviews (31999), Products (30018), and Receipts.
2. **E-commerce**: Local cart and NIP-15 products.
3. **UTXO Tokens**: Custom token system managed via chat.

## Project Structure
- `/app`: The new Android project (Kotlin/Compose).
- `/expo-refrence`: The original project (Reference for UI and Logic).
  - `/docs`: Architecture and Protocol specifics.

## Instructions for AI
- **Prioritize UI Migration**: Use Compose to replicate the Tailwind styles.
- **Granular Implementation**: Follow the step-by-step tasks in `MIGRATION_PLAN.md`.
- Use Hilt, Room, and Compose.
