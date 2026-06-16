package com.ely.kian.ui.screens.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.Profile
import kotlinx.coroutines.flow.Flow

class FollowersViewModel(
    private val userProfileDao: UserProfileDao,
    val pubkey: String
) : ViewModel() {
    val followers: Flow<List<Profile>> = userProfileDao.getFollowers(pubkey)

    companion object {
        fun provideFactory(userProfileDao: UserProfileDao, pubkey: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FollowersViewModel(userProfileDao, pubkey) as T
                }
            }
    }
}
