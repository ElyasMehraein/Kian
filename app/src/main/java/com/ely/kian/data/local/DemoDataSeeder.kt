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
    ) {
        // This is a placeholder for background seeding if needed
    }

    private suspend fun seedData(
        pubkey: String,
        name: String,
        userProfileDao: UserProfileDao,
        productDao: ProductDao,
        reviewDao: ReviewDao
    ) {
        val now = System.currentTimeMillis() / 1000
        
        // 1. Seed Profile
        val profile = Profile(
            pubkey = pubkey,
            name = name.lowercase().replace(" ", "_"),
            displayName = name,
            about = when(name) {
                "Aria Bakery" -> "Fresh organic bread, handmade pastries, and traditional desserts. We use only the finest local ingredients to bring you the taste of home. 🥖🥐"
                "Tech Service" -> "Professional gadget repair and premium electronics. We specialize in smartphone accessories, smart home devices, and custom PC builds. 💻📱"
                else -> "Your urban oasis specialist. From exotic indoor plants to sustainable garden tools, we help you cultivate peace and beauty in your living space. 🌿🍃"
            },
            picture = when(name) {
                "Aria Bakery" -> "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400"
                "Tech Service" -> "https://images.unsplash.com/photo-1519389950473-47ba0277781c?w=400"
                else -> "https://images.unsplash.com/photo-1466692476868-aef1dfb1e735?w=400"
            },
            banner = when(name) {
                "Aria Bakery" -> "https://images.unsplash.com/photo-1517433447744-d49970977f6b?w=1000"
                "Tech Service" -> "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=1000"
                else -> "https://images.unsplash.com/photo-1470058869958-2a77a617123f?w=1000"
            },
            website = "https://kian.social/traders/${name.lowercase().replace(" ", "")}",
            nip05 = "${name.lowercase().replace(" ", "")}@kian.social",
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
                "Aria Bakery" -> "Bakery & Cafe"
                "Tech Service" -> "Gadgets & Hardware"
                else -> "Plants & Decor"
            },
            parentId = null,
            level = 1,
            createdAt = now
        )
        productDao.upsertCategory(category)

        // 3. Seed Products
        val products = when(name) {
            "Aria Bakery" -> listOf(
                ProductData("Golden Croissant", "Extra buttery, 24-layered French classic. Perfect with morning coffee.", 
                    listOf("https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=800", "https://images.unsplash.com/photo-1530610476181-d83430b64dcd?w=800")),
                ProductData("Artisan Sourdough", "Wild-yeast fermented for 36 hours. Thick crust and airy interior.", 
                    listOf("https://images.unsplash.com/photo-1585478259715-876a6a81fc08?w=800")),
                ProductData("Velvet Chocolate Cake", "Triple-layer dark chocolate cake with ganache frosting and sea salt flakes.", 
                    listOf("https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=800")),
                ProductData("Cinnamon Roll", "Soft dough swirled with spicy cinnamon and topped with cream cheese frosting.", 
                    listOf("https://images.unsplash.com/photo-1509365465985-25d11c17e812?w=800")),
                ProductData("Blueberry Muffin", "Bursting with fresh berries and finished with a crunchy streusel topping.", 
                    listOf("https://images.unsplash.com/photo-1558401391-7899b4bd5bbf?w=800"))
            )
            "Tech Service" -> listOf(
                ProductData("Kian Pro Watch", "OLED display, 7-day battery life, and advanced health monitoring sensors.", 
                    listOf("https://images.unsplash.com/photo-1546868891-75e373d3f940?w=800", "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800")),
                ProductData("Sonic Buds", "Active noise cancellation and spatial audio support. 24h total playtime.", 
                    listOf("https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=800")),
                ProductData("Volt Power Pack", "20000mAh capacity with PD 3.0 fast charging. Charge 3 devices at once.", 
                    listOf("https://images.unsplash.com/photo-1609592424109-dd032e30776a?w=800")),
                ProductData("Mechanical Keyboard", "RGB backlit, hot-swappable switches, and ultra-low latency wireless connection.", 
                    listOf("https://images.unsplash.com/photo-1511467687858-23d96c32e4ae?w=800")),
                ProductData("Curved Monitor 34\"", "Ultrawide QHD resolution with 144Hz refresh rate for professional workflows.", 
                    listOf("https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?w=800"))
            )
            else -> listOf(
                ProductData("Monstera Deliciosa", "Beautiful, healthy Swiss cheese plant in a 12-inch terracotta pot.", 
                    listOf("https://images.unsplash.com/photo-1614594975525-e45190c55d0b?w=800", "https://images.unsplash.com/photo-1597072634461-ca73f702330a?w=800")),
                ProductData("Snake Plant Black", "The ultimate air purifier. Low light tolerant and very easy to maintain.", 
                    listOf("https://images.unsplash.com/photo-1593482892290-f54927ae1bf7?w=800")),
                ProductData("Succulent Trio", "A curated set of 3 unique miniature desert plants for your desk.", 
                    listOf("https://images.unsplash.com/photo-1520302630591-fd1c66ed11a3?w=800")),
                ProductData("Self-Watering Pot", "Smart irrigation system that keeps your plants hydrated for up to 2 weeks.", 
                    listOf("https://images.unsplash.com/photo-1485955900006-10f4d324d411?w=800")),
                ProductData("Fiddle Leaf Fig", "Elegant tall plant that adds a architectural touch to any modern living room.", 
                    listOf("https://images.unsplash.com/photo-1542608131-41fa98748360?w=800"))
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

        // 4. Seed Reviews
        val reviewData = listOf(
            Pair("Sina", "The quality exceeded my expectations. Will definitely order again!"),
            Pair("Sarah", "Fast shipping and excellent customer service. Highly recommended."),
            Pair("Alex", "Great value for the price. The attention to detail is amazing.")
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
    }

    private data class ProductData(val name: String, val description: String, val images: List<String>)
}
