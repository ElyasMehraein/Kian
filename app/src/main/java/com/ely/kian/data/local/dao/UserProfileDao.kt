package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE isMerchant = 1 ORDER BY rating DESC")
    fun listMerchants(): Flow<List<UserProfile>>

    @Query("SELECT * FROM user_profiles WHERE pubkey = :pubkey")
    suspend fun getProfile(pubkey: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)

    @Delete
    suspend fun delete(profile: UserProfile)
}
