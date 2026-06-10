package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    primaryKeys = ["id", "pubkey"],
    indices = [Index(value = ["pubkey"])]
)
data class Product(
    val id: String,
    val pubkey: String,
    val name: String,
    val description: String?,
    val images: String, // JSON array
    val categories: String, // JSON array
    val geohash: String?,
    val eventId: String,
    val createdAt: Long
)

@Entity(
    tableName = "product_categories",
    primaryKeys = ["id", "pubkey"],
    indices = [
        Index(value = ["pubkey"]),
        Index(value = ["pubkey", "parentId"])
    ]
)
data class ProductCategory(
    val id: String,
    val pubkey: String,
    val name: String,
    val parentId: String?,
    val level: Int,
    val createdAt: Long
)
