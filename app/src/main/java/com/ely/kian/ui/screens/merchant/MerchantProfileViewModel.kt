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
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError

    fun refresh() {
        viewModelScope.launch {
            _syncError.value = null
            _isRefreshing.value = true
            
            // Check for relay connectivity
            val connectedRelays = nostrSyncManager.getConnectedRelayCount()
            if (connectedRelays == 0) {
                _syncError.value = "عدم اتصال به شبکه. لطفا اینترنت خود را بررسی کنید."
                _isRefreshing.value = false
                return@launch
            }

            nostrSyncManager.requestMerchantData(pubkey)
            delay(3000) // Increased delay for better sync
            _isRefreshing.value = false
        }
    }

    fun clearSyncError() {
        _syncError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        nostrSyncManager.stopRequestingMerchantData(pubkey)
    }

    val profile: StateFlow<Profile?> = userProfileDao.getProfileFlow(pubkey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<List<VoucherCategory>> = voucherRepository.getCategories(pubkey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId

    val showcaseTokens: StateFlow<List<BalanceItem>> = combine(
        voucherRepository.getBalancesForPubkey(pubkey),
        _selectedCategoryId
    ) { list, selectedId ->
        if (isOwnProfile) {
            // For me: everything in the showcase
            list.filter { it.isShowcase && (selectedId == null || it.categories.contains(selectedId)) }
        } else {
            // For customers: only what is marked for public showcase AND has a category
            list.filter { 
                it.isShowcase && it.categories.isNotEmpty() && (selectedId == null || it.categories.contains(selectedId))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(categoryId: String?) {
        if (categoryId == null) {
            _selectedCategoryId.value = null
        } else {
            _selectedCategoryId.value = if (_selectedCategoryId.value == categoryId) null else categoryId
        }
    }

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

                val currentFollows = userProfileDao.listFollows(followerPubkey).first()

                val updatedFollows = if (currentlyFollowing) {
                    currentFollows.filter { it.followsPubkey != pubkey }
                } else {
                    currentFollows + UserFollow(followerPubkey, pubkey, null, null, now)
                }

                if (currentlyFollowing) {
                    userProfileDao.deleteFollow(followerPubkey, pubkey)
                } else {
                    userProfileDao.upsertFollow(UserFollow(followerPubkey, pubkey, null, null, now))
                }

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

    fun sendPurchaseRequest(token: BalanceItem, quantity: Long) {
        viewModelScope.launch {
            try {
                // 1. Protocol Level Lock: Find suitable UTXO and publish Kind 1050
                val utxos = voucherRepository.getUtxos().first()
                val suitableUtxo = utxos.find { it.assetRef == token.assetRef && !it.spent && it.amount >= quantity }
                
                if (suitableUtxo == null) {
                    _syncError.value = "موجودی کافی برای رزرو این حواله یافت نشد."
                    return@launch
                }

                // This publishes a signed Kind 1050 (Transfer Request) to the Producer/Relays
                // making it a protocol-level commitment that cannot be easily double-spent.
                val transferRequestId = voucherRepository.lockTokenForPurchase(suitableUtxo.utxoId, quantity, pubkey)

                // 2. Chat UI Notification
                val metadata = buildJsonObject {
                    put("type", "purchase_request")
                    put("asset_name", token.name)
                    put("amount", quantity)
                    put("token_id", token.assetRef)
                    put("utxo_id", suitableUtxo.utxoId)
                    put("transfer_request_id", transferRequestId)
                    put("producer", token.producer)
                    token.images.firstOrNull()?.let { put("image", it) }
                }.toString()

                val summary = "🛍️ $quantity x ${token.name}"
                chatRepository.sendMessage(pubkey, summary, metadata)
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
