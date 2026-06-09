package com.ely.kian.services

import com.ely.kian.data.local.entities.UserProfile
import kotlin.math.*

data class MerchantInfo(
    val pubkey: String,
    val profile: UserProfile,
    val score: Float,
    val title: String,
    val mutualFollows: Int,
    val distanceKm: Float?,
    val socialRating: Float
)

object MerchantRankingEngine {
    
    fun rankMerchants(
        currentPubkey: String?,
        currentGeohash: String?,
        merchants: List<UserProfile>,
        followings: Set<String>,
        mutualFollowsMap: Map<String, Int>,
        socialRatingsMap: Map<String, Float>
    ): List<MerchantInfo> {
        return merchants.map { merchant ->
            val mutualFollows = mutualFollowsMap[merchant.pubkey] ?: 0
            val socialRating = socialRatingsMap[merchant.pubkey] ?: 0f
            val distanceKm = if (currentGeohash != null && merchant.nip05 != null) { // Assuming geohash is in nip05 for now or another field
                calculateGeohashDistance(currentGeohash, merchant.nip05!!) 
            } else null
            
            val distanceFactor = if (distanceKm != null && distanceKm <= 10) 1f else 0f
            val score = mutualFollows.toFloat() + socialRating + distanceFactor
            
            MerchantInfo(
                pubkey = merchant.pubkey,
                profile = merchant,
                score = score,
                title = getTitle(score),
                mutualFollows = mutualFollows,
                distanceKm = distanceKm,
                socialRating = socialRating
            )
        }.sortedByDescending { it.score }
    }

    private fun getTitle(score: Float): String {
        return when {
            score >= 10 -> "KianBan"
            score >= 5 -> "BazarGardan"
            else -> "Tajer"
        }
    }

    // Simplified geohash distance for now
    private fun calculateGeohashDistance(geohash1: String, geohash2: String): Float {
        // Implementation of geohash distance
        return 5.0f // Placeholder
    }
}
