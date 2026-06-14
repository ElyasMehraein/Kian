package com.ely.kian.data.remote

import android.util.Log
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.Nip59
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.remote.model.NostrEvent
import com.ely.kian.data.repository.ProductRepository
import com.ely.kian.data.repository.TokenRepository
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.Profile
import kotlinx.serialization.json.*

class EventProcessor(
    private val secureStorage: SecureStorage,
    private val productRepository: ProductRepository,
    private val tokenRepository: TokenRepository,
    private val userProfileDao: UserProfileDao,
    private val reviewDao: com.ely.kian.data.local.dao.ReviewDao,
    private val chatRepository: com.ely.kian.data.repository.ChatRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val TAG = "EventProcessor"

    suspend fun process(event: NostrEvent) {
        Log.d(TAG, "Processing event: kind=${event.kind} id=${event.id.take(8)} author=${event.pubkey.take(8)}")
        
        try {
            when (event.kind) {
                0 -> handleMetadata(event)
                5 -> {
                    handleDeletion(event)
                    chatRepository.handleDeletion(event)
                }
                14 -> {
                    Log.i(TAG, "Processing Chat Message (Kind 14)")
                    chatRepository.handleChatMessage(event)
                }
                20001, 20002 -> chatRepository.handleReceipt(event)
                62 -> handleVanish(event)
                1984, 1985 -> handleReview(event)
                31999 -> handleSocialRating(event)
                30019 -> handleReviewBundle(event)
                1059 -> {
                    Log.i(TAG, "Processing GiftWrap (Kind 1059)")
                    handleGiftWrap(event)
                }
                30017, 30018 -> productRepository.handleProductEvent(event)
                35001, 35002 -> {
                    Log.i(TAG, "Processing Token Event (Kind ${event.kind})")
                    tokenRepository.handleTokenEvent(event)
                    
                    if (event.kind == 35002) {
                        // Update chat message status if it was a remint from a transfer
                        try {
                            val contentObj = json.parseToJsonElement(event.content).jsonObject
                            val prevUtxoId = contentObj["previous_utxo"]?.jsonPrimitive?.content
                            if (prevUtxoId != null) {
                                // If I am the sender, my message status becomes 'delivered' (verified)
                                // If I am the recipient, this helps the UI 'isConfirmed' check
                                chatRepository.updateMessageStatusByMetadata(prevUtxoId, "delivered")
                            }
                        } catch (e: Exception) {}
                    }
                }
                1050 -> {
                    Log.i(TAG, "Processing Token Transfer Request (Kind 1050)")
                    tokenRepository.handleTokenEvent(event)
                }
                1051 -> {
                    Log.i(TAG, "Processing Receipt Confirmation (Kind 1051)")
                    tokenRepository.handleTokenEvent(event)
                    val targetId = event.tags.find { it.size >= 2 && it[0] == "e" }?.get(1)
                    if (targetId != null) {
                        chatRepository.updateMessageStatusByMetadata(targetId, "received")
                    }
                }
                else -> Log.w(TAG, "Unhandled event kind=${event.kind}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing event kind=${event.kind}", e)
        }
    }

    private fun handleVanish(event: NostrEvent) {
        // NIP-62 Vanish: Logic for non-commerce entities if needed
        Log.i(TAG, "Author ${event.pubkey.take(8)} is vanishing.")
    }

    private suspend fun handleDeletion(event: NostrEvent) {
        // Standard Kind 5 Deletion
        val targetIds = event.tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
        targetIds.forEach { id ->
            // If it's a Kind 0, 30017, 30018, 35001, or 35002 - we do NOTHING.
            // These records stay in Kian's DB even if requested to be deleted.
        }
    }

    private suspend fun handleGiftWrap(wrap: NostrEvent) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val privKey = KianKeys.hexToBytes(privKeyHex)
        
        try {
            val rumor = Nip59.unwrap(wrap, privKey)
            if (rumor != null) {
                Log.d(TAG, "Unwrapped GiftWrap: kind=${rumor.kind} id=${rumor.id.take(8)}")
                // Recursive call to process the inner rumor
                process(rumor)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unwrap gift wrap", e)
        }
    }

    private suspend fun handleMetadata(event: NostrEvent) {
        val isTrader = event.tags.any { it.size >= 2 && it[0] == "t" && it[1] == "trader" }
        try {
            val content = json.parseToJsonElement(event.content).jsonObject
            val profile = Profile(
                pubkey = event.pubkey,
                name = content["name"]?.jsonPrimitive?.contentOrNull,
                displayName = content["display_name"]?.jsonPrimitive?.contentOrNull ?: content["name"]?.jsonPrimitive?.contentOrNull,
                about = content["about"]?.jsonPrimitive?.contentOrNull,
                picture = content["picture"]?.jsonPrimitive?.contentOrNull,
                banner = content["banner"]?.jsonPrimitive?.contentOrNull,
                website = content["website"]?.jsonPrimitive?.contentOrNull,
                nip05 = content["nip05"]?.jsonPrimitive?.contentOrNull,
                location = content["location"]?.jsonPrimitive?.contentOrNull,
                geohash = content["geohash"]?.jsonPrimitive?.contentOrNull,
                rawJson = event.content,
                isTrader = isTrader,
                createdAt = event.createdAt,
                updatedAt = System.currentTimeMillis() / 1000
            )
            userProfileDao.upsert(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse metadata", e)
        }
    }

    private suspend fun handleReview(event: NostrEvent) {
        val targetPubkey = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1) ?: return
        val rating = event.tags.find { it.size >= 2 && (it[0] == "rating" || (it[0] == "l" && it.size >= 3 && it[2] == "rating")) }?.get(1)?.toIntOrNull() ?: 5
        
        val authorProfile = userProfileDao.getProfile(event.pubkey)
        
        val review = com.ely.kian.data.local.entities.Review(
            pubkey = event.pubkey,
            targetPubkey = targetPubkey,
            authorName = authorProfile?.displayName ?: authorProfile?.name,
            rating = rating,
            comment = event.content,
            createdAt = event.createdAt
        )
        reviewDao.upsertReview(review)
    }

    private suspend fun handleSocialRating(event: NostrEvent) {
        // Kind 31999: Personal Rating File (Reviewer's Account File)
        try {
            // Expecting tags like ["p", "target_pubkey", "rating", "comment"]
            event.tags.filter { it.size >= 3 && it[0] == "p" }.forEach { tag ->
                val targetPubkey = tag[1]
                val rating = tag[2].toIntOrNull() ?: 5
                val comment = if (tag.size >= 4) tag[3] else null
                
                val authorProfile = userProfileDao.getProfile(event.pubkey)
                val review = com.ely.kian.data.local.entities.Review(
                    pubkey = event.pubkey,
                    targetPubkey = targetPubkey,
                    authorName = authorProfile?.displayName ?: authorProfile?.name,
                    rating = rating,
                    comment = comment,
                    createdAt = event.createdAt
                )
                reviewDao.upsertReview(review)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse social rating bundle (31999)", e)
        }
    }

    private suspend fun handleReviewBundle(event: NostrEvent) {
        // Kind 30019: Merchant's Review Bundle (Merchant's Account File)
        try {
            val reviewsArray = json.parseToJsonElement(event.content).jsonArray
            reviewsArray.forEach { reviewElement ->
                val reviewEvent = json.decodeFromJsonElement<NostrEvent>(reviewElement)
                handleReview(reviewEvent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse review bundle (30019)", e)
        }
    }
}
