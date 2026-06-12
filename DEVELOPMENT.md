# راهنمای توسعه (Development Guide)

این مستند شامل قوانین کدنویسی، ساختار پروژه و اولویت‌های فعلی تیم توسعه است.

## قوانین اصلی توسعه (Core Rules)

### 1. مدیریت پایگاه داده (Room Database)
- **Schema Changes**: در حال حاضر نسخه دیتابیس روی `1` فیکس شده است. برای اعمال تغییرات در ساختار جداول، از `fallbackToDestructiveMigration()` استفاده می‌شود؛ یعنی نیازی به نوشتن Migration نیست و دیتابیس با تغییر Schema پاک می‌شود.
- **Entities**: موجودیت‌ها باید در پکیج `com.ely.kian.data.local.entities` تعریف شوند.

### 2. معماری و رابط کاربری (UI & Architecture)
- **Compose First**: تمامی بخش‌های رابط کاربری باید با Jetpack Compose و رعایت سیستم طراحی اختصاصی `KianTheme` پیاده‌سازی شوند.
- **MVVM**: منطق برنامه باید در ViewModelها قرار گیرد و UI تنها مشاهده‌گر (Observer) وضعیت‌ها باشد.
- **Asynchronous**: برای عملیات سنگین و دیتابیس حتماً از Coroutines و Flow استفاده کنید.

### 3. پروتکل Nostr
- **Event Handling**: تمامی رویدادهای دریافتی از رله‌ها باید توسط `NostrSyncManager` مدیریت و در دیتابیس محلی ذخیره شوند.
- **Privacy**: برای ارسال پیام‌های خصوصی و اطلاعات حساس تجاری، حتماً از **NIP-59 (Gift Wrap)** استفاده کنید.

---

## ساختار پروژه (Project Structure)

- `com.ely.kian.ui`: شامل Screenها، ViewModelها و کامپوننت‌های Compose.
- `com.ely.kian.data.local`: شامل دیتابیس Room، DAOها و Entityها.
- `com.ely.kian.data.remote`: مدیریت ارتباطات WebSocket و همگام‌سازی با Nostr.
- `com.ely.kian.crypto`: پیاده‌سازی‌های مربوط به کلیدها، امضا و رمزنگاری (Secp256k1, NIP-44, BIP39).
- `com.ely.kian.services`: سرویس‌های پس‌زمینه و منطق‌های خاص (مانند Ranking Engine).

---

## تمرکز فعلی توسعه (Current Focus)

1.  **بهبود سیستم چت**: نهایی‌سازی وضعیت پیام‌ها (Delivered/Read) بر اساس رویدادهای transient.
2.  **مدیریت توکن‌ها**: پیاده‌سازی کامل چرخه حیات UTXO (Mint/Transfer).
3.  **بهینه‌سازی آفلاین**: بهبود صف ارسال آفلاین (Offline Queue) و فشرده‌سازی CBOR.
4.  **کاتالوگ محصولات**: تکمیل رابط کاربری مدیریت محصولات و دسته‌بندی‌ها.

---

## دستورالعمل برای AI (Instructions for AI)
- **Local Truth**: منبع اصلی برای منطق داده‌ها، کدهای موجود در `com.ely.kian.data.local` است.
- **Security**: هرگز کلیدهای خصوصی را به صورت Plain-text در دیتابیس معمولی ذخیره نکنید (از SecureStorage استفاده کنید).
- **Naming**: اسامی متغیرها و توابع باید توصیفی و مطابق با استانداردهای Kotlin باشد.
