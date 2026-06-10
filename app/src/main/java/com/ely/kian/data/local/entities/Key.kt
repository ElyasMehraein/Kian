package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keys")
data class Key(
    @PrimaryKey val pubkey: String,
    val npub: String,
    val createdAt: Long
)
