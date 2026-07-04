package com.ely.kian.util

import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.dao.VoucherDao
import com.ely.kian.data.local.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DemoDataSeeder {
    const val STORE_PK = "4d61727453746f72655075626b65793132333435363738393041424344454631" // Dummy Hex
    const val STORE_NSEC = "4d61727453746f7265507269766174654b657931323334353637383930414243" // Dummy Hex nsec (32 bytes)

    suspend fun seedSmartStore(userProfileDao: UserProfileDao, voucherDao: VoucherDao) = withContext(Dispatchers.IO) {
        // Only seed if not already there or to update
        val existing = userProfileDao.getProfile(STORE_PK)
        
        // 1. Create Profile
        val profile = Profile(
            pubkey = STORE_PK,
            name = "smart_store",
            displayName = "فروشگاه هوشمند",
            about = "بزرگترین مرکز توزیع کالا و خدمات در شیراز. از شیر مرغ تا جان آدمیزاد! (فروشگاه تستی)",
            picture = "https://picsum.photos/seed/store/200",
            banner = "https://picsum.photos/seed/banner/800/400",
            website = "https://kian-app.com",
            nip05 = "smart_store@kian-app.com",
            location = "شیراز، بلوار ارم",
            geohash = "ssptdn", // Shiraz Geohash
            rawJson = "{}",
            isTrader = true,
            createdAt = System.currentTimeMillis() / 1000,
            updatedAt = System.currentTimeMillis() / 1000
        )
        userProfileDao.upsert(profile)

        if (existing != null) return@withContext // Skip product seeding if already exists

        // 2. Create Categories
        val categories = listOf(
            Triple("cat_super", "سوپرمارکت", null),
            Triple("cat_prot", "پروتئین (گوشت و مرغ)", "cat_super"),
            Triple("cat_dairy", "لبنیات", "cat_super"),
            Triple("cat_grocery", "خاروبار و برنج", "cat_super"),
            
            Triple("cat_fashion", "پوشاک و مد", null),
            Triple("cat_men", "مردانه", "cat_fashion"),
            Triple("cat_women", "زنانه", "cat_fashion"),
            
            Triple("cat_tech", "خدمات فنی و تعمیرات", null),
            Triple("cat_car", "تعمیرات خودرو", "cat_tech"),
            Triple("cat_home", "تعمیرات لوازم خانگی", "cat_tech"),
            
            Triple("cat_home_stuff", "لوازم خانه", null)
        )

        categories.forEachIndexed { index, (id, name, parent) ->
            voucherDao.upsertCategory(VoucherCategory(
                id = id,
                pubkey = STORE_PK,
                name = name,
                parentId = parent,
                level = if (parent == null) 1 else 2,
                createdAt = System.currentTimeMillis() / 1000 + index
            ))
        }

        // 3. Generate 200 Products
        val products = mutableListOf<VoucherDefinition>()
        val mappings = mutableListOf<VoucherCategoryMapping>()
        val settings = mutableListOf<VoucherAssetSettings>()

        for (i in 1..200) {
            val (catId, name, price, imgId) = when {
                i <= 30 -> Quad("cat_prot", listOf("مرغ تازه", "گوشت گوساله", "ماهی قزل‌آلا", "سینه مرغ").random() + " $i", (150000L..450000L).random(), "food")
                i <= 60 -> Quad("cat_dairy", listOf("پنیر تبریز", "شیر پرچرب", "ماست سون", "کره محلی").random() + " $i", (20000L..90000L).random(), "dairy")
                i <= 90 -> Quad("cat_grocery", listOf("برنج هاشمی", "روغن آفتابگردان", "ماکارونی", "چای عطری").random() + " $i", (50000L..800000L).random(), "grocery")
                i <= 120 -> Quad("cat_men", listOf("تیشرت نخی", "شلوار جین", "پیراهن مجلسی", "کت تک").random() + " $i", (200000L..1500000L).random(), "fashion")
                i <= 150 -> Quad("cat_women", listOf("مانتو تابستانی", "شال نخی", "کیف چرمی", "کفش پاشنه بلند").random() + " $i", (150000L..2500000L).random(), "women")
                i <= 180 -> Quad("cat_car", listOf("تعویض روغن", "لنت ترمز", "صافکاری بی رنگ", "دیاگ خودرو").random() + " $i", (100000L..5000000L).random(), "car")
                else -> Quad("cat_home", listOf("تعمیر یخچال", "سرویس کولر", "نصب تلویزیون", "تعمیر لباسشویی").random() + " $i", (200000L..2000000L).random(), "home")
            }

            val assetId = "prod_$i"
            val assetRef = "35001:$STORE_PK:$assetId"
            
            products.add(VoucherDefinition(
                assetId = assetId,
                pubkey = STORE_PK,
                name = name,
                description = "توضیحات محصول شماره $i - کیفیت تضمینی در فروشگاه هوشمند شیراز.",
                images = listOf("https://picsum.photos/seed/$assetId/400"),
                amount = price,
                eventId = "event_$i",
                createdAt = System.currentTimeMillis() / 1000
            ))

            mappings.add(VoucherCategoryMapping(STORE_PK, assetRef, catId))
            settings.add(VoucherAssetSettings(STORE_PK, assetRef, true))
        }

        // Batch Insert
        products.forEach { voucherDao.upsertDefinition(it) }
        mappings.forEach { voucherDao.upsertMapping(it) }
        settings.forEach { voucherDao.upsertAssetSettings(it) }
    }
}

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
