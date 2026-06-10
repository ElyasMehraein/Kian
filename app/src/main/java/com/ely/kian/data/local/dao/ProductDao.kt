package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.ProductCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: Product)

    @Query("SELECT * FROM products WHERE pubkey = :pubkey ORDER BY createdAt DESC")
    fun getProductsByProducer(pubkey: String): Flow<List<Product>>

    @Query("SELECT DISTINCT pubkey FROM products ORDER BY pubkey ASC")
    suspend fun listProducerPubkeys(): List<String>

    @Query("SELECT * FROM products WHERE id = :productId AND pubkey = :pubkey LIMIT 1")
    suspend fun getProduct(productId: String, pubkey: String): Product?

    @Query("DELETE FROM products WHERE pubkey = :pubkey AND eventId IN (:eventIds)")
    suspend fun deleteProductsByEventIds(pubkey: String, eventIds: List<String>)

    // Categories
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: ProductCategory)

    @Query("SELECT * FROM product_categories WHERE pubkey = :pubkey ORDER BY level ASC, createdAt ASC, name COLLATE NOCASE ASC")
    fun listCategoriesByPubkey(pubkey: String): Flow<List<ProductCategory>>

    @Query("DELETE FROM product_categories WHERE pubkey = :pubkey AND id IN (:ids)")
    suspend fun deleteCategories(pubkey: String, ids: List<String>)
}
