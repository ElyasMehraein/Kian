package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String,
    val merchantPubkey: String,
    val name: String,
    val description: String?,
    val price: Long,
    val currency: String,
    val category: String?,
    val image: String?,
    val kind: Int = 30018
)
