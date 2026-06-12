# کیان (Kian) - پلتفرم تجارت غیرمتمرکز بر بستر Nostr

**Kian** یک اپلیکیشن اندرویدی (Native Android) برای تجارت الکترونیک و چت با اولویت آفلاین (Offline-First) است که بر پایه پروتکل Nostr بنا شده است. این پروژه با استفاده از Kotlin و Jetpack Compose توسعه یافته است.

## هدف پروژه (Goal)
هدف اصلی، ایجاد یک بازارچه امن، خصوصی و بدون نیاز به واسطه است که حتی در شرایط عدم دسترسی به اینترنت (از طریق SMS یا Acoustic FSK) نیز قابل استفاده باشد. کاربر در این پلتفرم کنترل کامل بر کلیدها و داده‌های خود دارد.

## ویژگی‌های کلیدی (Key Features)

*   **Nostr Native**: پیاده‌سازی مستقیم پروتکل Nostr برای مدیریت هویت، پروفایل‌ها و ارتباطات.
*   **Offline-First**: استفاده از پایگاه داده محلی Room به عنوان منبع اصلی حقیقت. تمام داده‌ها ابتدا به صورت محلی ذخیره و سپس با شبکه همگام می‌شوند.
*   **Privacy & Security**: استفاده از استاندارد **NIP-59 (Gift Wrap)** برای مخفی‌سازی متادیتا و **NIP-44** برای رمزنگاری محتوا.
*   **Merchant & User Modes**: قابلیت سوئیچ بین حالت عادی (Business) و حالت فروشنده (Merchant).
*   **Tokenized Assets**: مدیریت دارایی‌ها و توکن‌ها بر اساس مدل **UTXO** برای تبادلات کالا به کالا.
*   **Secure Backup**: امکان پشتیبان‌گیری رمزنگاری شده از کل پایگاه داده محلی.

## پشته تکنولوژی (Tech Stack)

*   **Language**: Kotlin
*   **UI**: Jetpack Compose (Material 3)
*   **Architecture**: MVVM + Clean Architecture principles
*   **Dependency Injection**: Hilt
*   **Database**: Room (SQLite)
*   **Networking**: WebSockets (OkHttp) برای اتصال به رله‌های Nostr
*   **Cryptography**: Secp256k1 (Bouncy Castle / Native libs), BIP39 برای مدیریت کلیدها

## مستندات پروژه (Documentation)

*   [DEVELOPMENT.md](./DEVELOPMENT.md): قوانین کدنویسی، ساختار پروژه و تمرکز فعلی توسعه.
*   [ARCHITECTURE.md](./ARCHITECTURE.md): جزئیات فنی پروتکل Nostr، امنیت و منطق آفلاین.
*   [DATABASE.md](./DATABASE.md): ساختار دقیق جداول و موجودیت‌های دیتابیس Room.

---

## شروع به کار (Getting Started)

1.  پروژه را در Android Studio باز کنید.
2.  از نصب بودن Android SDK مناسب (API 34+) اطمینان حاصل کنید.
3.  پروژه را Build کرده و اجرا کنید.

**نکته**: در فاز فعلی، دیتابیس در صورت تغییر Schema به صورت خودکار پاک می‌شود (`destructiveMigration`).
