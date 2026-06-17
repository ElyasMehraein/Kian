package com.ely.kian.ui.screens.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.remote.NostrSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FollowersViewModel(
    private val userProfileDao: UserProfileDao,
    private val nostrSyncManager: NostrSyncManager,
    val pubkey: String
) : ViewModel() {
    val followers: Flow<List<Profile>> = userProfileDao.getFollowers(pubkey)

    init {
        viewModelScope.launch {
            followers.collectLatest { list ->
                val missing = list.filter { it.createdAt == 0L }.map { it.pubkey }
                if (missing.isNotEmpty()) {
                    nostrSyncManager.requestProfiles(missing)
                }
            }
        }
    }

    companion object {
        fun provideFactory(userProfileDao: UserProfileDao, nostrSyncManager: NostrSyncManager, pubkey: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FollowersViewModel(userProfileDao, nostrSyncManager, pubkey) as T
                }
            }
    }
}
