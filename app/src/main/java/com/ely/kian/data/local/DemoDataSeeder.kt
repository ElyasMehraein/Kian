package com.ely.kian.data.local

import com.ely.kian.data.local.dao.ProductDao
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.dao.ReviewDao
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.local.entities.Review
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DemoDataSeeder {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun forceSeed(
        pubkey: String,
        index: Int,
        userProfileDao: UserProfileDao,
        productDao: ProductDao,
        reviewDao: ReviewDao
    ) {
        val names = listOf("Aria Bakery", "Tech Service", "Green Leaf")
        val name = names.getOrNull(index) ?: return
        seedData(pubkey, name, userProfileDao, productDao, reviewDao)
    }

    suspend fun seedIfTestAccount(
        pubkey: String,
        userProfileDao: UserProfileDao,
        productDao: ProductDao
    ) { }

    private suspend fun seedData(
        pubkey: String,
        name: String,
        userProfileDao: UserProfileDao,
        productDao: ProductDao,
        reviewDao: ReviewDao
    ) {
        val now = System.currentTimeMillis() / 1000
        
        // 1. Seed Profile with working Iranian CDN links
        val profile = Profile(
            pubkey = pubkey,
            name = name.lowercase().replace(" ", "_"),
            displayName = name,
            about = when(name) {
                "Aria Bakery" -> "نان و شیرینی تازه با آرد کامل و مواد اولیه درجه یک ارگانیک. طعم واقعی نان سنتی و فانتزی. 🥖🥐"
                "Tech Service" -> "مرکز تخصصی فروش و تعمیرات گجت‌های هوشمند. جدیدترین لوازم جانبی موبایل و قطعات کامپیوتر. 💻📱"
                else -> "فروشگاه تخصصی گل و گیاه آپارتمانی. طراوت و شادابی را به خانه خود بیاورید. مشاوره رایگان نگهداری. 🌿🍃"
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
            location = "Tehran, Iran",
            geohash = null,
            rawJson = "{}",
            isTrader = true,
            createdAt = now,
            updatedAt = now
        )
        userProfileDao.upsert(profile)

        // 2. Seed Category
        val catId = "cat-demo-$pubkey"
        val category = ProductCategory(
            id = catId,
            pubkey = pubkey,
            name = when(name) {
                "Aria Bakery" -> "نان و شیرینی"
                "Tech Service" -> "تجهیزات دیجیتال"
                else -> "گل و گیاه"
            },
            parentId = null,
            level = 1,
            createdAt = now
        )
        productDao.upsertCategory(category)

        // 3. Seed Products
        val products = when(name) {
            "Aria Bakery" -> listOf(
                ProductData("کروسان طلایی", "کروسان کره ای تازه با لایه های ترد و لذیذ. مناسب برای صبحانه.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/121006450.jpg")),
                ProductData("نان تست هفت غله", "تهیه شده از آرد کامل و دانه های مغذی. سرشار از فیبر.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/481c4e7434316a7593c661df659527ec5ec9e88d_1639904251.jpg")),
                ProductData("کیک شکلاتی کلاسیک", "کیک غلیظ شکلاتی با روکش گاناش و تزیینات ویژه.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/111867160.jpg")),
                ProductData("شیرینی دانمارکی", "تازه و خوشمزه، تهیه شده به صورت روزانه.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/112674.jpg")),
                ProductData("باقلوا سنتی", "باقلوای مخصوص با مغز پسته و گردو و شهد زعفرانی.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/121345.jpg"))
            )
            "Tech Service" -> listOf(
                ProductData("ساعت هوشمند پرو", "قابلیت مکالمه، پایش ضربان قلب و ضد آب. صفحه نمایش امولد.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/119914436.jpg")),
                ProductData("هندزفری بی سیم سونیک", "کیفیت صدای عالی با قابلیت حذف نویز محیطی.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/122045610.jpg")),
                ProductData("پاوربانک ۲۰۰۰۰", "شارژ سریع ۳ دستگاه همزمان. دارای گارانتی معتبر ۱۸ ماهه.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/121098.jpg")),
                ProductData("کیبورد مکانیکال گیمینگ", "نورپردازی RGB و سوییچ های آبی با طول عمر بالا.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/121456.jpg")),
                ProductData("مانیتور ۲۷ اینچ خمیده", "رزولوشن بالا و نرخ نوسازی ۱۴۴ هرتز برای گیمرها و طراحان.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/121789.jpg"))
            )
            else -> listOf(
                ProductData("گل مونسترا (پنیر سوئیسی)", "گیاه آپارتمانی شیک و مقاوم. همراه با گلدان سرامیکی.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/1143899.jpg")),
                ProductData("سانسوریا شمشیری", "بهترین گیاه تصفیه کننده هوا. مناسب برای محیط های کم نور.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/1143900.jpg")),
                ProductData("پک ساکولنت ۳ تایی", "سه عدد ساکولنت مینیاتوری زیبا برای روی میز کار.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/1143901.jpg")),
                ProductData("گلدان هوشمند آبیار", "دارای سیستم آبیاری خودکار برای زمانی که در سفر هستید.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/1143902.jpg")),
                ProductData("فیکوس لیراتا (برگ ویلونی)", "درختی زیبا و مینیاتوری برای پذیرایی های مدرن.", 
                    listOf("https://dkstatics-public.digikala.com/digikala-products/1143903.jpg"))
            )
        }

        products.forEachIndexed { i, p ->
            productDao.upsertProduct(
                Product(
                    id = "demo-prod-$i-$pubkey",
                    pubkey = pubkey,
                    name = p.name,
                    description = p.description,
                    images = json.encodeToString(p.images),
                    categories = json.encodeToString(listOf(catId)),
                    geohash = null,
                    eventId = "demo-event-$i-$pubkey",
                    isShowcase = true,
                    createdAt = now - i
                )
            )
        }

/*
        val reviewData = listOf(
            Pair("سینا", "کیفیت محصولات عالی بود. حتما باز هم سفارش میدم!"),
            Pair("سارا", "ارسال خیلی سریع و برخورد محترمانه. ممنون از شما."),
            Pair("امیر", "قیمت مناسب نسبت به کیفیت. جزئیات خیلی خوب رعایت شده بود.")
        )

        reviewData.forEachIndexed { i, (author, comment) ->
            reviewDao.upsertReview(
                Review(
                    pubkey = "reviewer-$i-$pubkey",
                    targetPubkey = pubkey,
                    authorName = author,
                    rating = 5 - (i % 2),
                    comment = comment,
                    createdAt = now - (i * 3600)
                )
            )
        }
*/
    }

    private data class ProductData(val name: String, val description: String, val images: List<String>)
}
