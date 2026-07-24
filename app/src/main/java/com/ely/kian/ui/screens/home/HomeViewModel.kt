package com.ely.kian.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.dao.ReviewDao
import com.ely.kian.services.MerchantInfo
import com.ely.kian.services.MerchantRankingEngine
import com.ely.kian.data.local.entities.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val userProfileDao: UserProfileDao,
    private val reviewDao: ReviewDao,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _merchants = MutableStateFlow<List<MerchantInfo>>(emptyList())
    val merchants: StateFlow<List<MerchantInfo>> = _merchants.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedSort = MutableStateFlow("Nearest")
    val selectedSort: StateFlow<String> = _selectedSort.asStateFlow()

    private val _userGeohash = MutableStateFlow<String?>(null)
    val userGeohash: StateFlow<String?> = _userGeohash.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<List<Profile>?> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(null)
            } else {
                var actualQuery = query.trim()
                if (actualQuery.startsWith("npub")) {
                    actualQuery = KianKeys.normalizePubkey(actualQuery)
                }
                userProfileDao.searchProfiles("%$actualQuery%")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadMerchants()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setIsSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }

    fun setSort(sort: String) {
        _selectedSort.value = sort
        loadMerchants()
    }

    private fun loadMerchants() {
        viewModelScope.launch {
            try {
                val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY)
                val ownPubkey = privKeyHex?.let {
                    KianKeys.bytesToHex(KianKeys.getPubKey(KianKeys.hexToBytes(it)))
                }

                val ownProfile = ownPubkey?.let { userProfileDao.getProfile(it) }
                val currentGeohash = ownProfile?.geohash
                _userGeohash.value = currentGeohash
                
                // Fetch mutual follows map
                val mutualFollowsMap = if (ownPubkey != null) {
                    val myFollows = userProfileDao.getFollowingPubkeys(ownPubkey)
                    val followersToQuery = myFollows + ownPubkey
                    userProfileDao.getMutualFollowCounts(followersToQuery).associate { 
                        it.pubkey to it.count.toInt() 
                    }
                } else {
                    emptyMap()
                }

                // Fetch social ratings map (Top Rated logic)
                val socialRatingsMap = if (ownPubkey != null) {
                    val myFollows = userProfileDao.getFollowingPubkeys(ownPubkey)
                    val authorsToQuery = myFollows + ownPubkey
                    reviewDao.getAverageRatingsByAuthors(authorsToQuery).associate {
                        it.pubkey to it.count
                    }
                } else {
                    emptyMap()
                }

                userProfileDao.listProfiles()
                    .conflate()
                    .map { profiles ->
                        profiles.filter { it.isTrader }
                    }
                    .flowOn(Dispatchers.Default)
                    .collect { merchantProfiles ->
                        val ranked = MerchantRankingEngine.rankMerchants(
                            currentPubkey = ownPubkey,
                            currentGeohash = currentGeohash,
                            merchants = merchantProfiles,
                            followings = emptySet(),
                            mutualFollowsMap = mutualFollowsMap,
                            socialRatingsMap = socialRatingsMap
                        )
                        
                        val sorted = when (_selectedSort.value) {
                            "Verified" -> ranked.sortedByDescending { it.mutualFollows }
                            "Top Rated" -> ranked.sortedByDescending { it.socialRating }
                            "Nearest" -> ranked.sortedBy { it.distanceKm ?: Float.MAX_VALUE }
                            "Online" -> ranked.sortedWith(compareByDescending<MerchantInfo> { it.isOnline }.thenByDescending { it.profile.updatedAt })
                            else -> ranked
                        }

                        _merchants.value = sorted
                        _isLoading.value = false
                    }
            } catch (t: Throwable) {
                Log.e("HomeViewModel", "Failed to load merchants", t)
                _isLoading.value = false
            }
        }
    }

    companion object {
        fun provideFactory(userProfileDao: UserProfileDao, reviewDao: ReviewDao, secureStorage: SecureStorage): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(userProfileDao, reviewDao, secureStorage) as T
            }
        }
    }
}
