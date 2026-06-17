package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "voucher_categories")
data class VoucherCategory(
    @PrimaryKey val id: String,
    val pubkey: String,
    val name: String,
    val parentId: String?,
    val level: Int,
    val isShowcase: Boolean = false,
    val createdAt: Long
)

@Entity(
    tableName = "voucher_category_mappings",
    primaryKeys = ["pubkey", "assetRef", "categoryId"],
    indices = [Index("assetRef"), Index("categoryId")]
)
data class VoucherCategoryMapping(
    val pubkey: String,
    val assetRef: String,
    val categoryId: String
)
