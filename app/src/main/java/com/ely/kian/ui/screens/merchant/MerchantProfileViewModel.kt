package com.ely.kian.ui.screens.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MerchantProfileViewModel(
    private val pubkey: String,
    private val ownPubkey: String?,
    private val userProfileDao: UserProfileDao
) : ViewModel() {

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile

    val isOwnProfile: Boolean = pubkey == ownPubkey

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _profile.value = userProfileDao.getProfile(pubkey)
        }
    }

    companion object {
        fun provideFactory(pubkey: String, ownPubkey: String?, userProfileDao: UserProfileDao): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MerchantProfileViewModel(pubkey, ownPubkey, userProfileDao) as T
            }
        }
    }
}
