package com.ely.kian.data.remote

import android.util.Log
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.Nip59
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.remote.model.NostrEvent
import com.ely.kian.data.repository.VoucherRepository
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.Profile
import kotlinx.serialization.json.*

class EventProcessor(
    private val secureStorage: SecureStorage,
    private val voucherRepository: VoucherRepository,
    private val userProfileDao: UserProfileDao,
    private val reviewDao: com.ely.kian.data.local.dao.ReviewDao,
    val chatRepository: com.ely.kian.data.repository.ChatRepository,
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
                7 -> {
                    Log.i(TAG, "Processing Reaction (Kind 7)")
                    chatRepository.handleReaction(event)
                }
                20001, 20002 -> chatRepository.handleReceipt(event)
                31999 -> handleSocialRating(event)
                1059 -> {
                    Log.i(TAG, "Processing GiftWrap (Kind 1059)")
                    handleGiftWrap(event)
                }
                35001, 35002 -> {
                    Log.i(TAG, "Processing Voucher Event (Kind ${event.kind})")
                    voucherRepository.handleTokenEvent(event)
                    
                    if (event.kind == 35002) {
                        try {
                            val contentObj = json.parseToJsonElement(event.content).jsonObject
                            val prevUtxoId = contentObj["previous_utxo"]?.jsonPrimitive?.content
                            if (prevUtxoId != null) {
                                chatRepository.updateMessageStatusByMetadata(prevUtxoId, "delivered")
                            }
                        } catch (e: Exception) {}
                    }
                }
                1050 -> {
                    Log.i(TAG, "Processing Voucher Transfer Request (Kind 1050)")
                    voucherRepository.handleTokenEvent(event)
                }
                1051 -> {
                    Log.i(TAG, "Processing Receipt Confirmation (Kind 1051)")
                    voucherRepository.handleTokenEvent(event)
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

    private suspend fun handleDeletion(event: NostrEvent) {
        val targetIds = event.tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
        targetIds.forEach { id ->
            // Logic for deletion
        }
    }

    private suspend fun handleGiftWrap(wrap: NostrEvent) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val privKey = KianKeys.hexToBytes(privKeyHex)
        
        try {
            val rumor = Nip59.unwrap(wrap, privKey)
            if (rumor != null) {
                Log.d(TAG, "Unwrapped GiftWrap: kind=${rumor.kind} id=${rumor.id.take(8)}")
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

    private suspend fun handleSocialRating(event: NostrEvent) {
        try {
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
}
