package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.Key
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyDao {
    @Query("SELECT * FROM keys LIMIT 1")
    suspend fun getKey(): Key?

    @Query("SELECT * FROM keys LIMIT 1")
    fun getKeyFlow(): Flow<Key?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: Key)

    @Query("DELETE FROM keys")
    suspend fun clearKeys()

    @Transaction
    suspend fun saveKeyPair(key: Key) {
        clearKeys()
        insertKey(key)
    }
}
