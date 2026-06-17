package com.ely.kian.services

import com.ely.kian.data.local.entities.Profile
import com.ely.kian.util.Geohash
import kotlin.math.*

data class MerchantInfo(
    val pubkey: String,
    val profile: Profile,
    val score: Float,
    val title: String,
    val mutualFollows: Int,
    val distanceKm: Float?,
    val socialRating: Float,
    val isOnline: Boolean = false
)

object MerchantRankingEngine {
    
    fun rankMerchants(
        currentPubkey: String?,
        currentGeohash: String?,
        merchants: List<Profile>,
        followings: Set<String>,
        mutualFollowsMap: Map<String, Int>,
        socialRatingsMap: Map<String, Float>
    ): List<MerchantInfo> {
        val now = System.currentTimeMillis() / 1000
        val onlineThreshold = 2 * 3600 // 2 hours
        
        return merchants.map { merchant ->
            val mutualFollows = mutualFollowsMap[merchant.pubkey] ?: 0
            val socialRating = socialRatingsMap[merchant.pubkey] ?: 0f
            val distanceKm = if (currentGeohash != null && merchant.geohash != null) {
                calculateGeohashDistance(currentGeohash, merchant.geohash)
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
                socialRating = socialRating,
                isOnline = (now - merchant.updatedAt) < onlineThreshold
            )
        }.sortedByDescending { it.score }
    }

    private fun getTitle(score: Float): String {
        return when {
            score >= 10 -> "Guardian"
            score >= 5 -> "Market Maker"
            else -> "Merchant"
        }
    }

    // Improved geohash distance calculation
    private fun calculateGeohashDistance(geohash1: String, geohash2: String): Float {
        try {
            val (lat1, lon1) = Geohash.decode(geohash1)
            val (lat2, lon2) = Geohash.decode(geohash2)
            
            val earthRadius = 6371.0 // kilometers
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (earthRadius * c).toFloat()
        } catch (e: Exception) {
            return 999f
        }
    }
}
