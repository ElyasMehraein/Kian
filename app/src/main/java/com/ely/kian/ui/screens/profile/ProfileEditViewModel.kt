package com.ely.kian.ui.screens.profile

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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class ProfileEditViewModel(
    private val keyDao: KeyDao,
    private val userProfileDao: UserProfileDao,
    private val nostrSyncManager: NostrSyncManager,
    private val secureStorage: SecureStorage
) : ViewModel() {

    var name by mutableStateOf("")
    var displayName by mutableStateOf("")
    var about by mutableStateOf("")
    var picture by mutableStateOf("")
    var banner by mutableStateOf("")
    var website by mutableStateOf("")
    var nip05 by mutableStateOf("")
    
    var isSaving by mutableStateOf(false)
    var pubkey by mutableStateOf<String?>(null)
    
    private var existingProfile: Profile? = null

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val key = keyDao.getKey()
            pubkey = key?.pubkey
            if (pubkey != null) {
                val profile = userProfileDao.getProfile(pubkey!!)
                existingProfile = profile
                name = profile?.name ?: ""
                displayName = profile?.displayName ?: ""
                about = profile?.about ?: ""
                picture = profile?.picture ?: ""
                banner = profile?.banner ?: ""
                website = profile?.website ?: ""
                nip05 = profile?.nip05 ?: ""
            }
        }
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val currentPubkey = pubkey ?: return
        if (isSaving) return
        
        isSaving = true
        viewModelScope.launch {
            val now = System.currentTimeMillis() / 1000
            val profile = Profile(
                pubkey = currentPubkey,
                name = name.ifBlank { null },
                displayName = displayName.ifBlank { null },
                about = about.ifBlank { null },
                picture = picture.ifBlank { null },
                banner = banner.ifBlank { null },
                website = website.ifBlank { null },
                nip05 = nip05.ifBlank { null },
                geohash = existingProfile?.geohash,
                rawJson = existingProfile?.rawJson ?: "{}",
                isTrader = existingProfile?.isTrader ?: false,
                createdAt = existingProfile?.createdAt ?: now,
                updatedAt = now
            )
            
            userProfileDao.upsert(profile)
            
            // Publish to Nostr
            val tags = if (profile.isTrader) listOf(listOf("t", "trader")) else emptyList()
            
            val contentObj = buildJsonObject {
                put("name", profile.name ?: "")
                put("display_name", profile.displayName ?: "")
                put("about", profile.about ?: "")
                put("picture", profile.picture ?: "")
                put("banner", profile.banner ?: "")
                put("website", profile.website ?: "")
                put("nip05", profile.nip05 ?: "")
            }
            val content = contentObj.toString()
            
            val id = KianKeys.computeEventId(currentPubkey, now, 0, tags, content)
            val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return@launch
            val privKey = KianKeys.hexToBytes(privKeyHex)
            val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), privKey))
            
            val event = NostrEvent(
                id = id,
                pubkey = currentPubkey,
                createdAt = now,
                kind = 0,
                tags = tags,
                content = content,
                sig = sig
            )
            nostrSyncManager.publishEvent(event)
            
            isSaving = false
            onSuccess()
        }
    }

    companion object {
        fun provideFactory(
            keyDao: KeyDao, 
            userProfileDao: UserProfileDao,
            nostrSyncManager: NostrSyncManager,
            secureStorage: SecureStorage
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProfileEditViewModel(keyDao, userProfileDao, nostrSyncManager, secureStorage) as T
            }
        }
    }
}
