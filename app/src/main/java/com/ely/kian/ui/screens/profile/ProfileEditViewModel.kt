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
import com.ely.kian.util.Geohash
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
    var location by mutableStateOf("")
    var geohash by mutableStateOf("")
    
    var latitude by mutableStateOf<Double?>(null)
    var longitude by mutableStateOf<Double?>(null)
    
    var isSaving by mutableStateOf(false)
    var pubkey by mutableStateOf<String?>(null)
    
    var isUploadingPicture by mutableStateOf(false)
    var isUploadingBanner by mutableStateOf(false)
    
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
                location = profile?.location ?: ""
                geohash = profile?.geohash ?: ""
                
                if (geohash.isNotBlank()) {
                    try {
                        val coords = Geohash.decode(geohash)
                        latitude = coords.first
                        longitude = coords.second
                    } catch (e: Exception) {
                        // Ignore malformed geohash
                    }
                }
            }
        }
    }

    fun updateLocation(lat: Double, lon: Double) {
        latitude = lat
        longitude = lon
        geohash = Geohash.encode(lat, lon)
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val currentPubkey = pubkey ?: return
        if (isSaving) return
        
        isSaving = true
        viewModelScope.launch {
            val now = System.currentTimeMillis() / 1000
            val cleanName = name.trim().ifBlank { null }
            val cleanDisplayName = displayName.trim().ifBlank { cleanName }

            val profile = Profile(
                pubkey = currentPubkey,
                name = cleanName,
                displayName = cleanDisplayName,
                about = about.trim().ifBlank { null },
                picture = picture.trim().ifBlank { null },
                banner = banner.trim().ifBlank { null },
                website = website.trim().ifBlank { null },
                nip05 = nip05.trim().ifBlank { null },
                location = location.trim().ifBlank { null },
                geohash = geohash.trim().ifBlank { null },
                rawJson = existingProfile?.rawJson ?: "{}",
                isTrader = existingProfile?.isTrader ?: false,
                createdAt = now,
                updatedAt = now
            )
            
            userProfileDao.upsert(profile)
            
            // Publish to Nostr
            val tags = mutableListOf<List<String>>()
            if (profile.isTrader) tags.add(listOf("t", "trader"))
            profile.location?.let { tags.add(listOf("location", it)) }
            profile.geohash?.let { tags.add(listOf("g", it)) }
            
            val contentObj = buildJsonObject {
                profile.name?.let { if (it.isNotBlank()) put("name", it) }
                profile.displayName?.let { if (it.isNotBlank()) put("display_name", it) }
                profile.about?.let { if (it.isNotBlank()) put("about", it) }
                profile.picture?.let { if (it.isNotBlank()) put("picture", it) }
                profile.banner?.let { if (it.isNotBlank()) put("banner", it) }
                profile.website?.let { if (it.isNotBlank()) put("website", it) }
                profile.nip05?.let { if (it.isNotBlank()) put("nip05", it) }
                profile.location?.let { if (it.isNotBlank()) put("location", it) }
                profile.geohash?.let { if (it.isNotBlank()) put("geohash", it) }
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

    fun uploadImage(context: android.content.Context, uri: android.net.Uri, isBanner: Boolean) {
        val currentPubkey = pubkey ?: return
        viewModelScope.launch {
            if (isBanner) isUploadingBanner = true else isUploadingPicture = true
            try {
                val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: throw Exception("Private key not found")
                val privKey = KianKeys.hexToBytes(privKeyHex)
                val url = com.ely.kian.services.BlossomUploader.uploadImage(context, uri, privKey, currentPubkey)
                if (isBanner) banner = url else picture = url
            } catch (e: Exception) {
                android.util.Log.e("ProfileEditViewModel", "Upload failed", e)
            } finally {
                if (isBanner) isUploadingBanner = false else isUploadingPicture = false
            }
        }
    }
}
