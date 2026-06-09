package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "token_utxos")
data class TokenUtxo(
    @PrimaryKey val id: String,
    val ownerPubkey: String,
    val amount: Long,
    val eventId: String,
    val isSpent: Boolean = false
)
