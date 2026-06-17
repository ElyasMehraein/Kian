package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VoucherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDefinition(definition: VoucherDefinition)

    @Query("SELECT * FROM voucher_definitions WHERE assetId = :assetId AND pubkey = :pubkey LIMIT 1")
    suspend fun getDefinition(assetId: String, pubkey: String): VoucherDefinition?

    @Query("SELECT * FROM voucher_definitions WHERE pubkey = :pubkey ORDER BY createdAt DESC")
    fun getDefinitionsByProducer(pubkey: String): Flow<List<VoucherDefinition>>

    // UTXOs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUtxo(utxo: VoucherUtxo)

    @Query("UPDATE voucher_utxos SET spent = 1 WHERE utxoId = :utxoId")
    suspend fun markSpent(utxoId: String)

    @Query("SELECT * FROM voucher_utxos WHERE owner = :pubkey ORDER BY createdAt DESC")
    fun getUtxosByOwner(pubkey: String): Flow<List<VoucherUtxo>>

    @Query("SELECT * FROM voucher_utxos WHERE owner = :pubkey OR (producer = :pubkey AND owner != :pubkey) ORDER BY createdAt DESC")
    fun getAllActivityUtxos(pubkey: String): Flow<List<VoucherUtxo>>

    @Query("SELECT * FROM voucher_utxos WHERE owner = :pubkey AND spent = 0 ORDER BY createdAt DESC")
    fun getUnspentUtxosByOwner(pubkey: String): Flow<List<VoucherUtxo>>

    @Query("SELECT * FROM voucher_utxos WHERE utxoId = :utxoId LIMIT 1")
    suspend fun getUtxo(utxoId: String): VoucherUtxo?

    // Categories
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: VoucherCategory)

    @Query("SELECT * FROM voucher_categories WHERE pubkey = :pubkey ORDER BY level ASC, createdAt ASC, name COLLATE NOCASE ASC")
    fun listCategoriesByPubkey(pubkey: String): Flow<List<VoucherCategory>>

    @Query("DELETE FROM voucher_categories WHERE pubkey = :pubkey AND id IN (:ids)")
    suspend fun deleteCategories(pubkey: String, ids: List<String>)

    @Query("UPDATE voucher_categories SET isShowcase = :isShowcase WHERE id = :id AND pubkey = :pubkey")
    suspend fun updateCategoryShowcase(id: String, pubkey: String, isShowcase: Boolean)

    // Category Mappings (Local)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapping(mapping: VoucherCategoryMapping)

    @Query("DELETE FROM voucher_category_mappings WHERE pubkey = :pubkey AND assetRef = :assetRef")
    suspend fun deleteMappingsForAsset(pubkey: String, assetRef: String)

    @Query("SELECT categoryId FROM voucher_category_mappings WHERE pubkey = :pubkey AND assetRef = :assetRef")
    suspend fun getCategoryIdsForAsset(pubkey: String, assetRef: String): List<String>

    @Query("SELECT assetRef FROM voucher_category_mappings WHERE pubkey = :pubkey AND categoryId = :categoryId")
    suspend fun getAssetRefsForCategory(pubkey: String, categoryId: String): List<String>

    @Query("SELECT COUNT(*) FROM voucher_category_mappings WHERE pubkey = :pubkey AND categoryId IN (:ids)")
    suspend fun countMappingsForCategories(pubkey: String, ids: List<String>): Int

    // Asset Settings (Showcase)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssetSettings(settings: VoucherAssetSettings)

    @Query("SELECT isShowcase FROM voucher_asset_settings WHERE pubkey = :pubkey AND assetRef = :assetRef")
    fun isAssetShowcasedFlow(pubkey: String, assetRef: String): Flow<Boolean?>

    @Query("SELECT isShowcase FROM voucher_asset_settings WHERE pubkey = :pubkey AND assetRef = :assetRef")
    suspend fun isAssetShowcased(pubkey: String, assetRef: String): Boolean?

    @Query("UPDATE voucher_asset_settings SET isShowcase = :isShowcase WHERE pubkey = :pubkey AND assetRef = :assetRef")
    suspend fun updateAssetShowcase(pubkey: String, assetRef: String, isShowcase: Boolean)

    @Query("SELECT * FROM voucher_asset_settings WHERE pubkey = :pubkey")
    fun getAssetSettingsByPubkey(pubkey: String): Flow<List<VoucherAssetSettings>>

    @Query("DELETE FROM voucher_category_mappings WHERE pubkey = :pubkey")
    suspend fun deleteMappingsByPubkey(pubkey: String)

    @Query("DELETE FROM voucher_categories WHERE pubkey = :pubkey")
    suspend fun deleteCategoriesByPubkey(pubkey: String)

    @Query("DELETE FROM voucher_definitions WHERE pubkey = :pubkey")
    suspend fun deleteDefinitionsByPubkey(pubkey: String)

    @Query("DELETE FROM voucher_utxos WHERE owner = :pubkey")
    suspend fun deleteUtxosByPubkey(pubkey: String)
}
