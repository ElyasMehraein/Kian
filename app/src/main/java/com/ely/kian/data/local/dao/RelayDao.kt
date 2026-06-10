package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.DmInboxRelay
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

    // DM Inbox Relays
    @Query("DELETE FROM dm_inbox_relays WHERE pubkey = :pubkey")
    suspend fun deleteDmInboxRelays(pubkey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDmInboxRelay(relay: DmInboxRelay)

    @Transaction
    suspend fun replaceDmInboxRelays(pubkey: String, relays: List<DmInboxRelay>) {
        deleteDmInboxRelays(pubkey)
        relays.forEach { insertDmInboxRelay(it) }
    }

    @Query("SELECT * FROM dm_inbox_relays WHERE pubkey = :pubkey ORDER BY relayUrl ASC")
    fun getDmInboxRelays(pubkey: String): Flow<List<DmInboxRelay>>

    @Query("SELECT relayUrl FROM dm_inbox_relays WHERE pubkey = :pubkey")
    suspend fun getDmInboxRelayUrls(pubkey: String): List<String>

    @Query("SELECT * FROM dm_inbox_relays ORDER BY pubkey ASC, relayUrl ASC")
    fun getAllDmInboxRelays(): Flow<List<DmInboxRelay>>
}
