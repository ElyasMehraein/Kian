package com.ely.kian.ui.screens.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.dao.ReviewDao
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.data.local.entities.UserFollow
import com.ely.kian.data.local.entities.Review
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.model.NostrEvent
import com.ely.kian.data.repository.ProductRepository
import com.ely.kian.data.repository.TokenRepository
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.crypto.KianKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MerchantProfileViewModel(
    private val pubkey: String,
    private val ownPubkey: String?,
    private val userProfileDao: UserProfileDao,
    private val productRepository: ProductRepository,
    private val tokenRepository: TokenRepository,
    private val reviewDao: ReviewDao,
    private val nostrSyncManager: NostrSyncManager,
    private val secureStorage: SecureStorage
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }

    init {
        nostrSyncManager.requestMerchantData(pubkey)
    }

    override fun onCleared() {
        super.onCleared()
        nostrSyncManager.stopRequestingMerchantData(pubkey)
    }

    val profile: StateFlow<Profile?> = userProfileDao.getProfileFlow(pubkey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val products: StateFlow<List<Product>> = productRepository.getProducts(pubkey)
        .map { list -> list.filter { it.isShowcase } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val showcaseTokens: StateFlow<List<BalanceItem>> = tokenRepository.getBalancesForPubkey(pubkey)
        .map { list -> list.filter { it.isShowcase } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<ProductCategory>> = productRepository.getCategories(pubkey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reviews: StateFlow<List<Review>> = reviewDao.getReviewsForTarget(pubkey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isFollowing: StateFlow<Boolean> = if (ownPubkey != null) {
        userProfileDao.listFollows(ownPubkey)
            .map { list -> list.any { it.followsPubkey == pubkey } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    } else {
        MutableStateFlow(false)
    }

    val followerCount: StateFlow<Int> = userProfileDao.countFollowers(pubkey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun toggleFollow() {
        val followerPubkey = ownPubkey ?: return
        viewModelScope.launch {
            try {
                val currentlyFollowing = userProfileDao.isFollowing(followerPubkey, pubkey)
                val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return@launch
                val privKey = KianKeys.hexToBytes(privKeyHex)
                val now = System.currentTimeMillis() / 1000

                // Get current follows from DB snapshot
                val currentFollows = userProfileDao.listFollows(followerPubkey).first()

                val updatedFollows = if (currentlyFollowing) {
                    currentFollows.filter { it.followsPubkey != pubkey }
                } else {
                    currentFollows + UserFollow(followerPubkey, pubkey, null, null, now)
                }

                // Update Local DB immediately for snappy UI
                if (currentlyFollowing) {
                    userProfileDao.deleteFollow(followerPubkey, pubkey)
                } else {
                    userProfileDao.upsertFollow(UserFollow(followerPubkey, pubkey, null, null, now))
                }

                // Create and Publish Kind 3 (NIP-02)
                val tags = updatedFollows.map { listOf("p", it.followsPubkey) }
                val id = KianKeys.computeEventId(followerPubkey, now, 3, tags, "")
                val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), privKey))
                
                val event = NostrEvent(
                    id = id,
                    pubkey = followerPubkey,
                    createdAt = now,
                    kind = 3,
                    tags = tags,
                    content = "",
                    sig = sig
                )
                nostrSyncManager.publishEvent(event)
            } catch (e: Exception) {
                android.util.Log.e("MerchantProfileVM", "Failed to toggle follow", e)
            }
        }
    }

    val userReview: StateFlow<Review?> = if (ownPubkey != null) {
        reviewDao.getReviewsForTarget(pubkey)
            .map { list -> list.find { it.pubkey == ownPubkey } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    } else {
        MutableStateFlow(null)
    }

    val isOwnProfile: Boolean = pubkey == ownPubkey

    fun postReview(rating: Int, comment: String) {
        val reviewerPubkey = ownPubkey ?: return
        viewModelScope.launch {
            val ownProfile = userProfileDao.getProfile(reviewerPubkey)
            val now = System.currentTimeMillis() / 1000
            val review = Review(
                pubkey = reviewerPubkey,
                targetPubkey = pubkey,
                authorName = ownProfile?.displayName ?: ownProfile?.name ?: "User",
                rating = rating,
                comment = comment,
                createdAt = now
            )
            reviewDao.upsertReview(review)

            // 1. Broadcast Individual Review (Standard NIP-32 Kind 1985)
            try {
                val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return@launch
                val privKey = KianKeys.hexToBytes(privKeyHex)
                
                val tags1985 = listOf(
                    listOf("p", pubkey),
                    listOf("l", rating.toString(), "rating"),
                    listOf("L", "reviews")
                )
                
                val id1985 = KianKeys.computeEventId(reviewerPubkey, now, 1985, tags1985, comment)
                val sig1985 = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id1985), privKey))
                
                val event1985 = NostrEvent(
                    id = id1985,
                    pubkey = reviewerPubkey,
                    createdAt = now,
                    kind = 1985,
                    tags = tags1985,
                    content = comment,
                    sig = sig1985
                )
                nostrSyncManager.publishEvent(event1985)

                // 2. Update Personal Rating File (Kind 31999)
                val allMyReviews = reviewDao.getReviewsByAuthor(reviewerPubkey)
                val tags31999 = allMyReviews.map { r ->
                    listOf("p", r.targetPubkey, r.rating.toString(), r.comment ?: "")
                }
                
                val id31999 = KianKeys.computeEventId(reviewerPubkey, now, 31999, tags31999, "Kian Social Ratings")
                val sig31999 = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id31999), privKey))
                
                val event31999 = NostrEvent(
                    id = id31999,
                    pubkey = reviewerPubkey,
                    createdAt = now,
                    kind = 31999,
                    tags = tags31999,
                    content = "Kian Social Ratings",
                    sig = sig31999
                )
                nostrSyncManager.publishEvent(event31999)

            } catch (e: Exception) {
                android.util.Log.e("MerchantProfileVM", "Failed to publish review", e)
            }
        }
    }

    companion object {
        fun provideFactory(
            pubkey: String, 
            ownPubkey: String?, 
            userProfileDao: UserProfileDao, 
            productRepository: ProductRepository,
            tokenRepository: TokenRepository,
            reviewDao: ReviewDao,
            nostrSyncManager: NostrSyncManager,
            secureStorage: SecureStorage
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MerchantProfileViewModel(pubkey, ownPubkey, userProfileDao, productRepository, tokenRepository, reviewDao, nostrSyncManager, secureStorage) as T
            }
        }
    }
}
