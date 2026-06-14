package com.ely.kian.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.services.MerchantInfo
import com.ely.kian.services.MerchantRankingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val userProfileDao: UserProfileDao,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _merchants = MutableStateFlow<List<MerchantInfo>>(emptyList())
    val merchants: StateFlow<List<MerchantInfo>> = _merchants.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedSort = MutableStateFlow("All")
    val selectedSort: StateFlow<String> = _selectedSort.asStateFlow()

    init {
        loadMerchants()
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
                
                // Fetch mutual follows map
                val mutualFollowsMap = if (ownPubkey != null) {
                    val myFollows = userProfileDao.getFollowingPubkeys(ownPubkey)
                    val followersToQuery = myFollows + ownPubkey
                    userProfileDao.getMutualFollowCounts(followersToQuery).associate { 
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
                            currentGeohash = null,
                            merchants = merchantProfiles,
                            followings = emptySet(),
                            mutualFollowsMap = mutualFollowsMap,
                            socialRatingsMap = emptyMap()
                        )
                        
                        val sorted = when (_selectedSort.value) {
                            "Verified" -> ranked.sortedByDescending { it.mutualFollows }
                            "Top Rated" -> ranked.sortedByDescending { it.socialRating }
                            // "Nearest" would need location, skipping for now as per current engine
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
        fun provideFactory(userProfileDao: UserProfileDao, secureStorage: SecureStorage): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(userProfileDao, secureStorage) as T
            }
        }
    }
}
