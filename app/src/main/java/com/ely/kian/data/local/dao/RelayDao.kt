package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.Relay
import kotlinx.coroutines.flow.Flow

@Dao
interface RelayDao {
    @Query("SELECT * FROM relays")
    fun getAllRelays(): Flow<List<Relay>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelay(relay: Relay)

    @Delete
    suspend fun deleteRelay(relay: Relay)

    @Query("UPDATE relays SET readEnabled = :read, writeEnabled = :write WHERE url = :url")
    suspend fun updateRelaySettings(url: String, read: Boolean, write: Boolean)
}
