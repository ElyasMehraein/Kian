package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voucher_categories")
data class VoucherCategory(
    @PrimaryKey val id: String,
    val pubkey: String,
    val name: String,
    val parentId: String?,
    val level: Int,
    val createdAt: Long
)
