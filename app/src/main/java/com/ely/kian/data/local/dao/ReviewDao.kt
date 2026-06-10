package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.Review
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReview(review: Review)

    @Query("SELECT * FROM reviews WHERE targetPubkey = :targetPubkey ORDER BY createdAt DESC")
    fun getReviewsForTarget(targetPubkey: String): Flow<List<Review>>

    @Query("SELECT * FROM reviews WHERE pubkey = :pubkey AND targetPubkey = :targetPubkey LIMIT 1")
    suspend fun getReview(pubkey: String, targetPubkey: String): Review?
}
