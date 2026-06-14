package com.ely.kian.data.local

import com.ely.kian.data.local.dao.ProductDao
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.dao.ReviewDao
import com.ely.kian.data.local.dao.TokenDao
import com.ely.kian.data.local.entities.*
import com.ely.kian.util.Geohash
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DemoDataSeeder {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun forceSeed(
        pubkey: String,
        index: Int,
        userProfileDao: UserProfileDao,
        productDao: ProductDao,
        tokenDao: TokenDao,
        reviewDao: ReviewDao
    ) {
        val names = listOf("Aria Bakery", "Tech Service", "Green Leaf")
        val name = names.getOrNull(index) ?: return
        seedData(pubkey, name, index, userProfileDao, productDao, tokenDao, reviewDao)
    }

    suspend fun seedIfTestAccount(
        pubkey: String,
        userProfileDao: UserProfileDao,
        productDao: ProductDao
    ) { }

    private suspend fun seedData(
        pubkey: String,
        name: String,
        index: Int,
        userProfileDao: UserProfileDao,
        productDao: ProductDao,
        tokenDao: TokenDao,
        reviewDao: ReviewDao
    ) {
        val now = System.currentTimeMillis() / 1000
        
        // Locations in Tehran (different parts)
        val locations = listOf(
            Triple("Tehran, Vanak", 35.757, 51.409),
            Triple("Tehran, Pasdaran", 35.768, 51.464),
            Triple("Tehran, Saadat Abad", 35.782, 51.365)
        )
        val (locName, lat, lon) = locations[index % locations.size]
        val gh = Geohash.encode(lat, lon, 8)

        // 1. Seed Profile
        val profile = Profile(
            pubkey = pubkey,
            name = name.lowercase().replace(" ", "_"),
            displayName = name,
            about = when(name) {
                "Aria Bakery" -> "نان و شیرینی تازه با آرد کامل و مواد اولیه درجه یک ارگانیک. 🥖🥐"
                "Tech Service" -> "مرکز تخصصی فروش و تعمیرات گجت‌های هوشمند و لوازم جانبی. 💻📱"
                else -> "فروشگاه تخصصی گل و گیاه آپارتمانی و ملزومات نگهداری. 🌿🍃"
            },
            picture = when(name) {
                "Aria Bakery" -> "https://dkstatics-public.digikala.com/digikala-products/121006450.jpg?x-oss-process=image/resize,m_lfit,h_300,w_300/quality,q_80"
                "Tech Service" -> "https://dkstatics-public.digikala.com/digikala-products/119914436.jpg?x-oss-process=image/resize,m_lfit,h_300,w_300/quality,q_80"
                else -> "https://dkstatics-public.digikala.com/digikala-products/1143899.jpg?x-oss-process=image/resize,m_lfit,h_300,w_300/quality,q_80"
            },
            banner = when(name) {
                "Aria Bakery" -> "https://dkstatics-public.digikala.com/digikala-products/111867160.jpg?x-oss-process=image/resize,m_lfit,h_600,w_1600/quality,q_80"
                "Tech Service" -> "https://dkstatics-public.digikala.com/digikala-products/121456.jpg?x-oss-process=image/resize,m_lfit,h_600,w_1600/quality,q_80"
                else -> "https://dkstatics-public.digikala.com/digikala-products/1143903.jpg?x-oss-process=image/resize,m_lfit,h_600,w_1600/quality,q_80"
            },
            website = "https://kian.social/traders/${name.lowercase().replace(" ", "")}",
            nip05 = "${name.lowercase().replace(" ", "")}@kian.social",
            location = locName,
            geohash = gh,
            rawJson = "{}",
            isTrader = true,
            createdAt = now,
            updatedAt = now
        )
        userProfileDao.upsert(profile)

        // 2. Nested Categories
        val rootCatId = "cat-root-$pubkey"
        val subCatId1 = "cat-sub1-$pubkey"
        val subCatId2 = "cat-sub2-$pubkey"

        val categories = when(name) {
            "Aria Bakery" -> listOf(
                ProductCategory(rootCatId, pubkey, "محصولات نانوایی", null, 1, now),
                ProductCategory(subCatId1, pubkey, "نان های سنتی", rootCatId, 2, now),
                ProductCategory(subCatId2, pubkey, "کیک و شیرینی", rootCatId, 2, now)
            )
            "Tech Service" -> listOf(
                ProductCategory(rootCatId, pubkey, "کالای دیجیتال", null, 1, now),
                ProductCategory(subCatId1, pubkey, "گوشی و لوازم جانبی", rootCatId, 2, now),
                ProductCategory(subCatId2, pubkey, "قطعات کامپیوتر", rootCatId, 2, now)
            )
            else -> listOf(
                ProductCategory(rootCatId, pubkey, "گل و گیاه", null, 1, now),
                ProductCategory(subCatId1, pubkey, "گیاهان آپارتمانی", rootCatId, 2, now),
                ProductCategory(subCatId2, pubkey, "ادوات باغبانی", rootCatId, 2, now)
            )
        }
        categories.forEach { productDao.upsertCategory(it) }

        // 3. Products
        val productsData = when(name) {
            "Aria Bakery" -> listOf(
                ProductData("نان سنگک کنجدی", "نان سنگک تازه و داغ با کنجد فراوان.", listOf("https://dkstatics-public.digikala.com/digikala-products/121345.jpg"), subCatId1),
                ProductData("بربری ماشینی", "نان بربری ترد و خشخاشی.", listOf("https://dkstatics-public.digikala.com/digikala-products/112674.jpg"), subCatId1),
                ProductData("کیک یزدی", "کیک سنتی یزدی با طعم هل و گلاب.", listOf("https://dkstatics-public.digikala.com/digikala-products/111867160.jpg"), subCatId2),
                ProductData("شیرینی لطیفه", "خامه ای و تازه.", listOf("https://dkstatics-public.digikala.com/digikala-products/121006450.jpg"), subCatId2),
                ProductData("کروسان شکلاتی", "نان فانتزی با مغز شکلات تلخ.", listOf("https://dkstatics-public.digikala.com/digikala-products/121006450.jpg"), subCatId2)
            )
            "Tech Service" -> listOf(
                ProductData("iPhone 15 Pro", "جدیدترین پرچمدار اپل با بدنه تیتانیومی.", listOf("https://dkstatics-public.digikala.com/digikala-products/119914436.jpg"), subCatId1),
                ProductData("سامسونگ S23 Ultra", "بهترین گوشی اندرویدی با قلم S-Pen.", listOf("https://dkstatics-public.digikala.com/digikala-products/122045610.jpg"), subCatId1),
                ProductData("کارت گرافیک RTX 4090", "قدرتمندترین کارت گرافیک دنیا برای گیمینگ.", listOf("https://dkstatics-public.digikala.com/digikala-products/121456.jpg"), subCatId2),
                ProductData("حافظه SSD 1TB", "سرعت فوق العاده بالا برای سیستم شما.", listOf("https://dkstatics-public.digikala.com/digikala-products/121098.jpg"), subCatId2),
                ProductData("مانیتور گیمینگ ROG", "۱۴۴ هرتز با تاخیر ۱ میلی ثانیه.", listOf("https://dkstatics-public.digikala.com/digikala-products/121789.jpg"), subCatId2)
            )
            else -> listOf(
                ProductData("آگلونما صورتی", "گیاه آپارتمانی بسیار زیبا و خاص.", listOf("https://dkstatics-public.digikala.com/digikala-products/1143899.jpg"), subCatId1),
                ProductData("زامیفولیا بلک", "گیاهی مقاوم و لوکس برای آپارتمان.", listOf("https://dkstatics-public.digikala.com/digikala-products/1143900.jpg"), subCatId1),
                ProductData("گلدان سرامیکی", "طرح های مدرن و مینیمال.", listOf("https://dkstatics-public.digikala.com/digikala-products/1143901.jpg"), subCatId2),
                ProductData("خاک مخصوص کاکتوس", "ترکیب غنی شده برای رشد بهتر.", listOf("https://dkstatics-public.digikala.com/digikala-products/1143902.jpg"), subCatId2),
                ProductData("سمپاش دستی ۲ لیتری", "کیفیت ساخت بالا و نازل قابل تنظیم.", listOf("https://dkstatics-public.digikala.com/digikala-products/1143903.jpg"), subCatId2)
            )
        }

        productsData.forEachIndexed { i, p ->
            productDao.upsertProduct(
                Product(
                    id = "demo-prod-$i-$pubkey",
                    pubkey = pubkey,
                    name = p.name,
                    description = p.description,
                    images = json.encodeToString(p.images),
                    categories = json.encodeToString(listOf(p.catId)),
                    geohash = gh,
                    eventId = "demo-event-$i-$pubkey",
                    isShowcase = true,
                    createdAt = now - i
                )
            )
        }

        // 4. Tokens (Definitions and UTXOs for owner)
        val tokenDefinitions = when(name) {
            "Aria Bakery" -> listOf(
                TokenData("بون تخفیف نان", "تخفیف ۲۰ درصدی برای خریدهای بالای ۱۰۰ هزار تومان.", "درصد", subCatId1),
                TokenData("امتیاز وفاداری آریا", "هر ۱۰ امتیاز مساوی یک نان رایگان.", "امتیاز", rootCatId)
            )
            "Tech Service" -> listOf(
                TokenData("گیفت کارت تعمیرات", "اعتبار ۵۰۰ هزار تومانی برای خدمات نرم افزاری.", "تومان", subCatId1),
                TokenData("ووچر خرید قطعات", "تخفیف ویژه برای مشتریان دائمی.", "واحد", subCatId2)
            )
            else -> listOf(
                TokenData("کارت هدیه گلستان", "اعتبار خرید انواع گل های آپارتمانی.", "واحد", subCatId1),
                TokenData("امتیاز سبز", "امتیازات جمع آوری شده برای دریافت کود رایگان.", "امتیاز", subCatId2)
            )
        }

        tokenDefinitions.forEachIndexed { i, t ->
            val assetId = "demo-token-$i-$pubkey"
            val assetRef = "35001:$pubkey:$assetId"
            
            tokenDao.upsertDefinition(
                TokenDefinition(
                    assetId = assetId,
                    pubkey = pubkey,
                    productId = null,
                    name = t.name,
                    description = t.description,
                    images = "[]",
                    categories = json.encodeToString(listOf(t.catId)),
                    unit = t.unit,
                    eventId = "demo-token-ev-$i-$pubkey",
                    isShowcase = true,
                    createdAt = now - i
                )
            )

            // Give the owner some initial balance of their own tokens
            tokenDao.insertUtxo(
                TokenUtxo(
                    utxoId = "demo-utxo-$i-$pubkey",
                    assetRef = assetRef,
                    producer = pubkey,
                    owner = pubkey,
                    amount = 1000L,
                    prevUtxoId = null,
                    createdAt = now - i,
                    spent = false
                )
            )
        }
    }

    private data class ProductData(val name: String, val description: String, val images: List<String>, val catId: String)
    private data class TokenData(val name: String, val description: String, val unit: String, val catId: String)
}
