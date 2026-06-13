package com.ely.kian.data.local.entities

import androidx.room.Entity

@Entity(tableName = "reviews", primaryKeys = ["pubkey", "targetPubkey"])
data class Review(
    val pubkey: String,
    val targetPubkey: String,
    val authorName: String?,
    val rating: Int,
    val comment: String?,
    val pageIndex: Int = 0,
    val createdAt: Long
)
