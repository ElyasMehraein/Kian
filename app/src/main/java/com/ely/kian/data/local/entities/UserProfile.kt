package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val pubkey: String,
    val name: String?,
    val bio: String?,
    val picture: String?,
    val nip05: String?,
    val lud16: String?,
    val banner: String?,
    val website: String?,
    val about: String?,
    val displayName: String?,
    val createdAt: Long,
    val isMerchant: Boolean = false,
    val rating: Float = 0f,
    val distance: Float? = null
)
