package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM profiles ORDER BY updatedAt DESC")
    fun listProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE pubkey = :pubkey")
    suspend fun getProfile(pubkey: String): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)
}
