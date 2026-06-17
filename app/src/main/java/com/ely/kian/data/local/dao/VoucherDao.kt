package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.VoucherDefinition
import com.ely.kian.data.local.entities.VoucherUtxo
import com.ely.kian.data.local.entities.VoucherCategory
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

    @Query("UPDATE voucher_definitions SET isShowcase = :isShowcase WHERE assetId = :assetId AND pubkey = :pubkey")
    suspend fun updateShowcase(assetId: String, pubkey: String, isShowcase: Boolean)

    @Query("UPDATE voucher_definitions SET categories = :categories WHERE assetId = :assetId AND pubkey = :pubkey")
    suspend fun updateCategories(assetId: String, pubkey: String, categories: String)

    // Categories
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: VoucherCategory)

    @Query("SELECT * FROM voucher_categories WHERE pubkey = :pubkey ORDER BY level ASC, createdAt ASC, name COLLATE NOCASE ASC")
    fun listCategoriesByPubkey(pubkey: String): Flow<List<VoucherCategory>>

    @Query("DELETE FROM voucher_categories WHERE pubkey = :pubkey AND id IN (:ids)")
    suspend fun deleteCategories(pubkey: String, ids: List<String>)
}
