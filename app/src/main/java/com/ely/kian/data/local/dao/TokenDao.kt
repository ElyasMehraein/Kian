package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.TokenDefinition
import com.ely.kian.data.local.entities.TokenUtxo
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDefinition(definition: TokenDefinition)

    @Query("SELECT * FROM token_definitions WHERE assetId = :assetId AND pubkey = :pubkey LIMIT 1")
    suspend fun getDefinition(assetId: String, pubkey: String): TokenDefinition?

    @Query("SELECT * FROM token_definitions WHERE pubkey = :pubkey ORDER BY createdAt DESC")
    fun getDefinitionsByProducer(pubkey: String): Flow<List<TokenDefinition>>

    // UTXOs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUtxo(utxo: TokenUtxo)

    @Query("UPDATE token_utxos SET spent = 1 WHERE utxoId = :utxoId")
    suspend fun markSpent(utxoId: String)

    @Query("SELECT * FROM token_utxos WHERE owner = :pubkey ORDER BY createdAt DESC")
    fun getUtxosByOwner(pubkey: String): Flow<List<TokenUtxo>>

    @Query("SELECT * FROM token_utxos WHERE owner = :pubkey OR (producer = :pubkey AND owner != :pubkey) ORDER BY createdAt DESC")
    fun getAllActivityUtxos(pubkey: String): Flow<List<TokenUtxo>>

    @Query("SELECT * FROM token_utxos WHERE owner = :pubkey AND spent = 0 ORDER BY createdAt DESC")
    fun getUnspentUtxosByOwner(pubkey: String): Flow<List<TokenUtxo>>

    @Query("SELECT * FROM token_utxos WHERE utxoId = :utxoId LIMIT 1")
    suspend fun getUtxo(utxoId: String): TokenUtxo?

    @Query("UPDATE token_definitions SET isShowcase = :isShowcase WHERE assetId = :assetId AND pubkey = :pubkey")
    suspend fun updateShowcase(assetId: String, pubkey: String, isShowcase: Boolean)
}
