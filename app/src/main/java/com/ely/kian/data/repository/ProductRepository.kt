package com.ely.kian.data.repository

import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.ProductDao
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.RelayPoolManager
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class ProductRepository(
    private val productDao: ProductDao,
    private val secureStorage: SecureStorage,
    private val syncManagerProvider: () -> NostrSyncManager,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val syncManager by lazy { syncManagerProvider() }

    fun getProducts(pubkey: String): Flow<List<Product>> = productDao.getProductsByProducer(pubkey)

    fun getCategories(pubkey: String): Flow<List<ProductCategory>> = productDao.listCategoriesByPubkey(pubkey)

    suspend fun saveProduct(
        id: String?,
        name: String,
        description: String,
        images: List<String>,
        categories: List<String>,
        isShowcase: Boolean? = null
    ) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val privKey = KianKeys.hexToBytes(privKeyHex)
        val pubKeyHex = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))

        val productId = id ?: "product-${System.currentTimeMillis()}"
        val createdAt = System.currentTimeMillis() / 1000
        
        val finalIsShowcase = isShowcase ?: id?.let { productDao.getProduct(it, pubKeyHex)?.isShowcase } ?: false

        val content = buildJsonObject {
            put("name", name)
            put("description", description)
            put("showcase", finalIsShowcase)
            putJsonArray("images") { images.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("categories") { categories.forEach { add(JsonPrimitive(it)) } }
        }.toString()

        val tags = listOf(listOf("d", productId))

        val eventId = KianKeys.computeEventId(pubKeyHex, createdAt, 30018, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(eventId), privKey))

        val event = NostrEvent(
            id = eventId,
            pubkey = pubKeyHex,
            createdAt = createdAt,
            kind = 30018,
            tags = tags,
            content = content,
            sig = sig
        )

        val product = Product(
            id = productId,
            pubkey = pubKeyHex,
            name = name,
            description = description,
            images = json.encodeToString(images),
            categories = json.encodeToString(categories),
            geohash = null,
            eventId = eventId,
            isShowcase = finalIsShowcase,
            createdAt = createdAt
        )

        productDao.upsertProduct(product)
        publishEvent(event)
    }

    suspend fun deleteProduct(productId: String, eventId: String) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val privKey = KianKeys.hexToBytes(privKeyHex)
        val pubKeyHex = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))

        val createdAt = System.currentTimeMillis() / 1000
        val tags = listOf(listOf("e", eventId))
        val deleteEventId = KianKeys.computeEventId(pubKeyHex, createdAt, 5, tags, "Delete product")
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(deleteEventId), privKey))

        val event = NostrEvent(
            id = deleteEventId,
            pubkey = pubKeyHex,
            createdAt = createdAt,
            kind = 5,
            tags = tags,
            content = "Delete product",
            sig = sig
        )

        productDao.deleteProductsByEventIds(pubKeyHex, listOf(eventId))
        publishEvent(event)
    }

    suspend fun saveCategory(name: String, parentId: String?, level: Int) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val privKey = KianKeys.hexToBytes(privKeyHex)
        val pubKeyHex = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))

        val categoryId = "category-${System.currentTimeMillis()}"
        val createdAt = System.currentTimeMillis() / 1000

        val content = buildJsonObject {
            put("name", name)
            put("parent", parentId)
            put("level", level)
        }.toString()

        val tags = listOf(listOf("d", categoryId))

        val eventId = KianKeys.computeEventId(pubKeyHex, createdAt, 30017, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(eventId), privKey))

        val event = NostrEvent(
            id = eventId,
            pubkey = pubKeyHex,
            createdAt = createdAt,
            kind = 30017,
            tags = tags,
            content = content,
            sig = sig
        )

        val category = ProductCategory(
            id = categoryId,
            pubkey = pubKeyHex,
            name = name,
            parentId = parentId,
            level = level,
            createdAt = createdAt
        )

        productDao.upsertCategory(category)
        publishEvent(event)
    }

    suspend fun deleteCategoryBranch(ids: List<String>) {
        val pubKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY)?.let {
            KianKeys.bytesToHex(KianKeys.getPubKey(KianKeys.hexToBytes(it)))
        } ?: return

        productDao.deleteCategories(pubKeyHex, ids)
    }

    suspend fun updateShowcase(productId: String, isShowcase: Boolean) {
        val pubKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY)?.let {
            KianKeys.bytesToHex(KianKeys.getPubKey(KianKeys.hexToBytes(it)))
        } ?: return

        val existing = productDao.getProduct(productId, pubKeyHex) ?: return
        saveProduct(
            id = existing.id,
            name = existing.name,
            description = existing.description ?: "",
            images = json.decodeFromString(existing.images),
            categories = json.decodeFromString(existing.categories),
            isShowcase = isShowcase
        )
    }

    private fun publishEvent(event: NostrEvent) {
        syncManager.publishEvent(event)
    }

    suspend fun handleProductEvent(event: NostrEvent) {
        when (event.kind) {
            30018 -> handleProductKind18(event)
            30017 -> handleCategoryKind17(event)
        }
    }

    private suspend fun handleProductKind18(event: NostrEvent) {
        try {
            val dTag = event.tags.find { it.size >= 2 && it[0] == "d" }?.get(1) ?: event.id
            val content = json.parseToJsonElement(event.content).jsonObject
            
            val product = Product(
                id = dTag,
                pubkey = event.pubkey,
                name = content["name"]?.jsonPrimitive?.content ?: "Unknown Product",
                description = content["description"]?.jsonPrimitive?.content ?: "",
                images = content["images"]?.toString() ?: "[]",
                categories = content["categories"]?.toString() ?: "[]",
                geohash = null,
                eventId = event.id,
                isShowcase = content["showcase"]?.jsonPrimitive?.boolean ?: false,
                createdAt = event.createdAt
            )
            productDao.upsertProduct(product)
        } catch (e: Exception) {
            // Log error
        }
    }

    private suspend fun handleCategoryKind17(event: NostrEvent) {
        try {
            val dTag = event.tags.find { it.size >= 2 && it[0] == "d" }?.get(1) ?: event.id
            val content = json.parseToJsonElement(event.content).jsonObject
            
            val category = ProductCategory(
                id = dTag,
                pubkey = event.pubkey,
                name = content["name"]?.jsonPrimitive?.content ?: "Unknown Category",
                parentId = content["parent"]?.jsonPrimitive?.contentOrNull,
                level = content["level"]?.jsonPrimitive?.intOrNull ?: 1,
                createdAt = event.createdAt
            )
            productDao.upsertCategory(category)
        } catch (e: Exception) {
            // Log error
        }
    }
}
