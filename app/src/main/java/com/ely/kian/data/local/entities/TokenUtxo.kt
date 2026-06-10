package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "token_definitions", primaryKeys = ["assetId", "pubkey"])
data class TokenDefinition(
    val assetId: String,
    val pubkey: String,
    val productId: String?,
    val name: String,
    val description: String?,
    val images: String, // JSON array
    val categories: String, // JSON array
    val unit: String = "unit",
    val eventId: String,
    val createdAt: Long
)

@Entity(tableName = "token_utxos")
data class TokenUtxo(
    @PrimaryKey val utxoId: String,
    val assetRef: String,
    val producer: String,
    val owner: String,
    val amount: Long,
    val prevUtxoId: String?,
    val createdAt: Long,
    val spent: Boolean = false
)
