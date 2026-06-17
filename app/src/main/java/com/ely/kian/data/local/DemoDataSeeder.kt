package com.ely.kian.data.local

import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.dao.ReviewDao
import com.ely.kian.data.local.dao.VoucherDao
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
        voucherDao: VoucherDao,
        reviewDao: ReviewDao
    ) {
        val names = listOf("نانوایی سنتی برکت", "مرکز خدمات هوشمند پارس", "گلخانه گلستان")
        val name = names.getOrNull(index) ?: return
        seedData(pubkey, name, index, userProfileDao, voucherDao, reviewDao)
    }

    suspend fun seedIfTestAccount(
        pubkey: String,
        userProfileDao: UserProfileDao
    ) { }

    private suspend fun seedData(
        pubkey: String,
        name: String,
        index: Int,
        userProfileDao: UserProfileDao,
        voucherDao: VoucherDao,
        reviewDao: ReviewDao
    ) {
        val now = System.currentTimeMillis() / 1000
        
        val locations = listOf(
            Triple("تهران، میدان ونک", 35.757, 51.409),
            Triple("تهران، خیابان پاسداران", 35.768, 51.464),
            Triple("تهران، سعادت‌آباد", 35.782, 51.365)
        )
        val (locName, lat, lon) = locations[index % locations.size]
        val gh = Geohash.encode(lat, lon, 8)

        // 1. Seed Profile
        val profile = Profile(
            pubkey = pubkey,
            name = when(index) {
                0 -> "barekat_bakery"
                1 -> "pars_tech"
                else -> "golestan_garden"
            },
            displayName = name,
            about = when(index) {
                0 -> "ارائه انواع نان‌های سنتی سنگک و بربری با آرد سبوس‌دار و کیفیت عالی. 🥖"
                1 -> "تخصصی‌ترین مرکز تعمیرات موبایل، لپ‌تاپ و کنسول‌های بازی در منطقه. 💻"
                else -> "تولید و عرضه مستقیم انواع گل‌های آپارتمانی و زینتی به همراه ملزومات باغبانی. 🌿"
            },
            picture = when(index) {
                0 -> "https://dkstatics-public.digikala.com/digikala-products/121006450.jpg?x-oss-process=image/resize,m_lfit,h_300,w_300/quality,q_80"
                1 -> "https://dkstatics-public.digikala.com/digikala-products/119914436.jpg?x-oss-process=image/resize,m_lfit,h_300,w_300/quality,q_80"
                else -> "https://dkstatics-public.digikala.com/digikala-products/1143899.jpg?x-oss-process=image/resize,m_lfit,h_300,w_300/quality,q_80"
            },
            banner = when(index) {
                0 -> "https://dkstatics-public.digikala.com/digikala-products/111867160.jpg?x-oss-process=image/resize,m_lfit,h_600,w_1600/quality,q_80"
                1 -> "https://dkstatics-public.digikala.com/digikala-products/121456.jpg?x-oss-process=image/resize,m_lfit,h_600,w_1600/quality,q_80"
                else -> "https://dkstatics-public.digikala.com/digikala-products/1143903.jpg?x-oss-process=image/resize,m_lfit,h_600,w_1600/quality,q_80"
            },
            website = "https://kian.social/traders/${name}",
            nip05 = "${name}@kian.social",
            location = locName,
            geohash = gh,
            rawJson = "{}",
            isTrader = true,
            createdAt = now,
            updatedAt = now
        )
        userProfileDao.upsert(profile)

        // 2. Categories
        val catId = "demo-cat-$index-$pubkey"
        val category = VoucherCategory(
            id = catId,
            pubkey = pubkey,
            name = when(index) {
                0 -> "نان و غلات"
                1 -> "خدمات فنی"
                else -> "گیاه و باغچه"
            },
            parentId = null,
            level = 1,
            createdAt = now
        )
        voucherDao.upsertCategory(category)

        // 3. Vouchers (Definitions and UTXOs)
        val tokenDataList = when(index) {
            0 -> listOf(
                TokenData("حواله نان سنگک دوآتیشه", "شامل ۵ قرص نان سنگک کنجدی داغ.", "عدد"),
                TokenData("بن خرید محصولات نانوایی", "قابل استفاده برای خرید انواع نان و کیک.", "واحد")
            )
            1 -> listOf(
                TokenData("حواله سرویس کامل سیستم", "پاکسازی سخت‌افزاری و نصب مجدد ویندوز.", "ساعت"),
                TokenData("ووچر خدمات نرم‌افزاری", "اعتبار جهت نصب نرم‌افزارهای تخصصی.", "تومان")
            )
            else -> listOf(
                TokenData("حواله گیاه سانسوریا", "یک گلدان سانسوریا ابلق سایز بزرگ.", "گلدان"),
                TokenData("کارت هدیه گلستان", "جهت خرید گل و گیاه و خاک گلدان.", "واحد")
            )
        }

        tokenDataList.forEachIndexed { i, t ->
            val assetId = "demo-asset-$i-$pubkey"
            val assetRef = "35001:$pubkey:$assetId"
            
            voucherDao.upsertDefinition(
                VoucherDefinition(
                    assetId = assetId,
                    pubkey = pubkey,
                    name = t.name,
                    description = t.description,
                    images = "[]",
                    categories = json.encodeToString(listOf(catId)),
                    unit = t.unit,
                    eventId = "demo-ev-$i-$pubkey",
                    isShowcase = true,
                    createdAt = now - i
                )
            )

            voucherDao.insertUtxo(
                VoucherUtxo(
                    utxoId = "demo-utxo-$i-$pubkey",
                    assetRef = assetRef,
                    producer = pubkey,
                    owner = pubkey,
                    amount = 500L,
                    prevUtxoId = null,
                    createdAt = now - i,
                    spent = false
                )
            )
        }
    }

    private data class TokenData(val name: String, val description: String, val unit: String)
}
