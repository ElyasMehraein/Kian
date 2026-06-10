package com.ely.kian.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.services.MerchantInfo
import com.ely.kian.services.MerchantRankingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(private val userProfileDao: UserProfileDao) : ViewModel() {

    private val _merchants = MutableStateFlow<List<MerchantInfo>>(emptyList())
    val merchants: StateFlow<List<MerchantInfo>> = _merchants.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadMerchants()
    }

    private fun loadMerchants() {
        viewModelScope.launch {
            try {
                userProfileDao.listProfiles()
                    .conflate()
                    .map { profiles ->
                        profiles.filter { it.isTrader }
                    }
                    .flowOn(Dispatchers.Default)
                    .collect { merchantProfiles ->
                        val ranked = MerchantRankingEngine.rankMerchants(
                            currentPubkey = null,
                            currentGeohash = null,
                            merchants = merchantProfiles,
                            followings = emptySet(),
                            mutualFollowsMap = emptyMap(),
                            socialRatingsMap = emptyMap()
                        )
                        _merchants.value = ranked
                        _isLoading.value = false
                    }
            } catch (t: Throwable) {
                Log.e("HomeViewModel", "Failed to load merchants", t)
                _isLoading.value = false
            }
        }
    }

    companion object {
        fun provideFactory(userProfileDao: UserProfileDao): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(userProfileDao) as T
            }
        }
    }
}
