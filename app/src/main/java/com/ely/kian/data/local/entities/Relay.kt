package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "relays")
data class Relay(
    @PrimaryKey val url: String,
    val readEnabled: Boolean = true,
    val writeEnabled: Boolean = true
)

@Entity(tableName = "dm_inbox_relays", primaryKeys = ["pubkey", "relayUrl"])
data class DmInboxRelay(
    val pubkey: String,
    val relayUrl: String,
    val createdAt: Long
)
