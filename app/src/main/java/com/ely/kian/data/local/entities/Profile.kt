package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val pubkey: String,
    val name: String?,
    val displayName: String?,
    val about: String?,
    val picture: String?,
    val nip05: String?,
    val geohash: String?,
    val rawJson: String,
    val isTrader: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "user_follows",
    primaryKeys = ["pubkey", "followsPubkey"],
    indices = [
        Index(value = ["pubkey"]),
        Index(value = ["followsPubkey"])
    ]
)
data class UserFollow(
    val pubkey: String,
    val followsPubkey: String,
    val petName: String?,
    val relayHint: String?,
    val createdAt: Long
)
