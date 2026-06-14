package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.local.entities.UserFollow
import com.ely.kian.data.local.entities.PubkeyCount
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM profiles ORDER BY updatedAt DESC")
    fun listProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE pubkey = :pubkey")
    suspend fun getProfile(pubkey: String): Profile?

    @Query("SELECT * FROM profiles WHERE pubkey = :pubkey")
    fun getProfileFlow(pubkey: String): Flow<Profile?>

    @Query("SELECT * FROM profiles WHERE pubkey IN (:pubkeys)")
    suspend fun getProfiles(pubkeys: List<String>): List<Profile>

    @Query("SELECT * FROM profiles WHERE displayName LIKE :query OR name LIKE :query ORDER BY updatedAt DESC")
    fun searchProfiles(query: String): Flow<List<Profile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile)

    @Transaction
    suspend fun upsert(profile: Profile) {
        val existing = getProfile(profile.pubkey)
        if (existing == null || profile.createdAt >= existing.createdAt) {
            insert(profile)
        }
    }

    @Delete
    suspend fun delete(profile: Profile)

    // Follows
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollow(follow: UserFollow)

    @Query("SELECT * FROM user_follows WHERE pubkey = :pubkey")
    fun listFollows(pubkey: String): Flow<List<UserFollow>>

    @Query("SELECT COUNT(*) FROM user_follows WHERE followsPubkey = :pubkey")
    fun countFollowers(pubkey: String): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM user_follows WHERE pubkey = :pubkey AND followsPubkey = :targetPubkey)")
    suspend fun isFollowing(pubkey: String, targetPubkey: String): Boolean

    @Query("DELETE FROM user_follows WHERE pubkey = :pubkey AND followsPubkey = :targetPubkey")
    suspend fun deleteFollow(pubkey: String, targetPubkey: String)

    @Query("DELETE FROM user_follows WHERE pubkey = :pubkey")
    suspend fun clearFollows(pubkey: String)

    @Query("SELECT followsPubkey FROM user_follows WHERE pubkey = :pubkey")
    suspend fun getFollowingPubkeys(pubkey: String): List<String>

    @Query("""
        SELECT followsPubkey as pubkey, COUNT(*) as count 
        FROM user_follows 
        WHERE pubkey IN (:followerPubkeys)
        GROUP BY followsPubkey
    """)
    suspend fun getMutualFollowCounts(followerPubkeys: List<String>): List<PubkeyCount>

    @Transaction
    suspend fun replaceFollows(pubkey: String, follows: List<UserFollow>) {
        clearFollows(pubkey)
        follows.forEach { upsertFollow(it) }
    }
}
