package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "voucher_definitions", primaryKeys = ["assetId", "pubkey"])
data class VoucherDefinition(
    val assetId: String,
    val pubkey: String,
    val name: String,
    val description: String?,
    val images: String, // JSON array
    val eventId: String,
    val createdAt: Long
)

@Entity(
    tableName = "voucher_utxos",
    indices = [
        Index(value = ["owner"]),
        Index(value = ["producer"]),
        Index(value = ["assetRef"])
    ]
)
data class VoucherUtxo(
    @PrimaryKey val utxoId: String,
    val assetRef: String,
    val producer: String,
    val owner: String,
    val amount: Long,
    val prevUtxoId: String?,
    val createdAt: Long,
    val spent: Boolean = false
)
