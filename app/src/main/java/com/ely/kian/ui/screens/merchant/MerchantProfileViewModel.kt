package com.ely.kian.ui.screens.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.dao.ReviewDao
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.local.entities.UserFollow
import com.ely.kian.data.local.entities.Review
import com.ely.kian.data.local.entities.VoucherCategory
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.model.NostrEvent
import com.ely.kian.data.repository.VoucherRepository
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.crypto.KianKeys
import com.ely.kian.data.repository.ChatRepository
import com.ely.kian.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MerchantProfileViewModel(
    pubkey: String,
    ownPubkey: String?,
    private val userProfileDao: UserProfileDao,
    private val voucherRepository: VoucherRepository,
    private val chatRepository: ChatRepository,
    private val reviewDao: ReviewDao,
    private val nostrSyncManager: NostrSyncManager,
    private val secureStorage: SecureStorage
) : ViewModel() {
    private val pubkey = KianKeys.normalizePubkey(pubkey)
    private val ownPubkey = ownPubkey?.let { KianKeys.normalizePubkey(it) }
    val isOwnProfile: Boolean = this.pubkey == this.ownPubkey

    init {
        nostrSyncManager.requestMerchantData(this.pubkey)
        if (isOwnProfile) {
            viewModelScope.launch {
                try {
                    voucherRepository.republishDefinitions()
                } catch (e: Exception) {
                    android.util.Log.e("MerchantProfileVM", "Failed to republish definitions", e)
                }
            }
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _syncErrorResId = MutableStateFlow<Int?>(null)
    val syncErrorResId: StateFlow<Int?> = _syncErrorResId

    private val _syncErrorArgs = MutableStateFlow<Array<out Any>>(emptyArray())
    val syncErrorArgs: StateFlow<Array<out Any>> = _syncErrorArgs

    val profile: Flow<Profile?> = userProfileDao.getProfileFlow(this.pubkey)

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    val categories: Flow<List<VoucherCategory>> = voucherRepository.getCategories(this.pubkey)

    val showcaseTokens: Flow<List<BalanceItem>> = combine(
        voucherRepository.getBalancesForPubkey(this.pubkey),
        selectedCategoryId
    ) { balances, selectedId ->
        balances.filter { it.isShowcase && (selectedId == null || it.categories.contains(selectedId)) }
    }

    val reviews: Flow<List<Review>> = reviewDao.getReviewsForTarget(this.pubkey)

    val userReview: Flow<Review?> = if (ownPubkey != null) {
        reviewDao.getReviewFlow(ownPubkey, pubkey)
    } else {
        flowOf(null)
    }

    val isFollowing: Flow<Boolean> = if (ownPubkey != null) {
        userProfileDao.listFollows(ownPubkey).map { follows ->
            follows.any { it.followsPubkey == this.pubkey }
        }
    } else {
        flowOf(false)
    }

    val followerCount: Flow<Int> = userProfileDao.countFollowers(this.pubkey)

    fun toggleFollow() {
        val follower = ownPubkey ?: return
        viewModelScope.launch {
            if (userProfileDao.isFollowing(follower, pubkey)) {
                userProfileDao.deleteFollow(follower, pubkey)
            } else {
                userProfileDao.upsertFollow(UserFollow(follower, pubkey, null, null, System.currentTimeMillis() / 1000))
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _syncErrorResId.value = null
            _isRefreshing.value = true
            
            // Check for relay connectivity
            val connectedRelays = nostrSyncManager.getConnectedRelayCount()
            if (connectedRelays == 0) {
                _syncErrorResId.value = R.string.failed // Or a more specific connection error if available
                _isRefreshing.value = false
                return@launch
            }

            nostrSyncManager.requestMerchantData(pubkey)
            delay(3000)
            _isRefreshing.value = false
        }
    }

    fun clearSyncError() {
        _syncErrorResId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        nostrSyncManager.stopRequestingMerchantData(pubkey)
    }

    fun sendPurchaseRequest(token: BalanceItem, quantity: Long, spendingSummary: String, buySummary: String) {
        viewModelScope.launch {
            try {
                val utxos = voucherRepository.getUtxos().first()
                val availableUtxos = utxos.filter { it.assetRef == token.assetRef && !it.spent }
                val totalAvailable = availableUtxos.sumOf { it.amount }

                if (totalAvailable > 0) {
                    if (totalAvailable < quantity) {
                        _syncErrorResId.value = R.string.insufficient_balance_total
                        _syncErrorArgs.value = arrayOf(totalAvailable)
                        return@launch
                    }

                    val suitableUtxo = availableUtxos.find { it.amount >= quantity }
                    if (suitableUtxo == null) {
                        _syncErrorResId.value = R.string.balance_fragmented
                        return@launch
                    }

                    val transferRequestId = voucherRepository.lockTokenForPurchase(suitableUtxo.utxoId, quantity, pubkey)

                    val metadata = buildJsonObject {
                        put("type", "purchase_request")
                        put("asset_name", token.name)
                        put("asset_description", token.description ?: "")
                        put("asset_images", JsonArray(token.images.map { JsonPrimitive(it) }))
                        put("amount", quantity)
                        put("token_id", token.assetRef)
                        put("utxo_id", suitableUtxo.utxoId)
                        put("transfer_request_id", transferRequestId)
                        put("producer", token.producer)
                    }.toString()

                    chatRepository.sendMessage(pubkey, spendingSummary, metadata)
                } else {
                    val metadata = buildJsonObject {
                        put("type", "purchase_request")
                        put("asset_name", token.name)
                        put("asset_description", token.description ?: "")
                        put("asset_images", JsonArray(token.images.map { JsonPrimitive(it) }))
                        put("amount", quantity)
                        put("token_id", token.assetRef)
                        put("producer", token.producer)
                    }.toString()

                    chatRepository.sendMessage(pubkey, buySummary, metadata)
                }
            } catch (e: Exception) {
                android.util.Log.e("MerchantProfileVM", "Failed to send purchase request", e)
            }
        }
    }

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
            voucherRepository: VoucherRepository,
            chatRepository: ChatRepository,
            reviewDao: ReviewDao,
            nostrSyncManager: NostrSyncManager,
            secureStorage: SecureStorage
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MerchantProfileViewModel(pubkey, ownPubkey, userProfileDao, voucherRepository, chatRepository, reviewDao, nostrSyncManager, secureStorage) as T
            }
        }
    }
}
