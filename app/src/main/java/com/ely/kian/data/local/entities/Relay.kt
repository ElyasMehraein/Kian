package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "relays")
data class Relay(
    @PrimaryKey val url: String,
    val readEnabled: Boolean = true,
    val writeEnabled: Boolean = true,
    val isActive: Boolean = true
)

@Entity(
    tableName = "dm_inbox_relays",
    primaryKeys = ["pubkey", "relayUrl"],
    indices = [Index(value = ["pubkey"])]
)
data class DmInboxRelay(
    val pubkey: String,
    val relayUrl: String,
    val createdAt: Long
)
