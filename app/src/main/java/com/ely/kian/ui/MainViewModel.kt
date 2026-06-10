package com.ely.kian.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.KeyDao
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.model.NostrEvent
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.SecureStorage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    private val keyDao: KeyDao,
    private val userProfileDao: UserProfileDao,
    private val nostrSyncManager: NostrSyncManager,
    private val secureStorage: SecureStorage
) : ViewModel() {

    companion object {
        fun provideFactory(
            keyDao: KeyDao, 
            userProfileDao: UserProfileDao, 
            nostrSyncManager: NostrSyncManager,
            secureStorage: SecureStorage
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(keyDao, userProfileDao, nostrSyncManager, secureStorage) as T
            }
        }
    }

    var isLoggedIn by mutableStateOf<Boolean?>(null)
        private set

    var ownPubkey by mutableStateOf<String?>(null)
        private set

    var accountMode by mutableStateOf("business")
        private set

    init {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.delay(500) // Small delay to let DB/UI stabilize
                keyDao.getKeyFlow().collectLatest { key ->
                    isLoggedIn = key != null
                    ownPubkey = key?.pubkey
                    if (key != null) {
                        try {
                            val profile = userProfileDao.getProfile(key.pubkey)
                            accountMode = if (profile?.isTrader == true) "merchant" else "business"
                        } catch (e: Exception) {
                            android.util.Log.e("MainViewModel", "Profile fetch failed", e)
                        }
                    }
                }
            } catch (e: Exception) {
                isLoggedIn = false
                android.util.Log.e("MainViewModel", "Auth flow failed", e)
            }
        }
    }

    fun updateAccountMode(mode: String) {
        if (accountMode == mode) return
        
        viewModelScope.launch {
            val key = keyDao.getKey() ?: return@launch
            val pubkey = key.pubkey
            val profile = userProfileDao.getProfile(pubkey)
            
            val updatedProfile = if (profile != null) {
                profile.copy(
                    isTrader = mode == "merchant",
                    updatedAt = System.currentTimeMillis() / 1000
                )
            } else {
                Profile(
                    pubkey = pubkey,
                    name = null,
                    displayName = null,
                    about = null,
                    picture = null,
                    nip05 = null,
                    geohash = null,
                    rawJson = "{}",
                    isTrader = mode == "merchant",
                    createdAt = System.currentTimeMillis() / 1000,
                    updatedAt = System.currentTimeMillis() / 1000
                )
            }
            
            userProfileDao.upsert(updatedProfile)
            accountMode = mode
            
            // Publish metadata event to Nostr
            val tags = if (mode == "merchant") listOf(listOf("t", "trader")) else emptyList()
            val content = """{"display_name": "${updatedProfile.displayName ?: ""}", "about": "${updatedProfile.about ?: ""}"}"""
            val createdAt = System.currentTimeMillis() / 1000
            
            val id = KianKeys.computeEventId(pubkey, createdAt, 0, tags, content)
            val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return@launch
            val privKey = KianKeys.hexToBytes(privKeyHex)
            val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), privKey))
            
            val event = NostrEvent(
                id = id,
                pubkey = pubkey,
                createdAt = createdAt,
                kind = 0,
                tags = tags,
                content = content,
                sig = sig
            )
            nostrSyncManager.publishEvent(event)
        }
    }

    fun logout() {
        viewModelScope.launch {
            keyDao.clearKeys()
        }
    }
}
