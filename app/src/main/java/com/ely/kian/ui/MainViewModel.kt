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
import com.ely.kian.data.local.DemoDataSeeder
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class MainViewModel(
    private val keyDao: KeyDao,
    private val userProfileDao: UserProfileDao,
    private val nostrSyncManager: NostrSyncManager,
    private val secureStorage: SecureStorage,
    private val database: com.ely.kian.data.local.KianDatabase
) : ViewModel() {

    // Wrap in catch to prevent crashes during DB integrity issues
    val totalUnreadCount: StateFlow<Int> = try {
        database.chatDao().getTotalUnreadCount()
            .map { it ?: 0 }
            .catch { emit(0) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    } catch (e: Exception) {
        MutableStateFlow(0)
    }

    val userProfile: StateFlow<Profile?> = try {
        keyDao.getKeyFlow()
            .flatMapLatest { key ->
                if (key != null) {
                    userProfileDao.getProfileFlow(key.pubkey)
                } else {
                    flowOf(null)
                }
            }
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    } catch (e: Exception) {
        MutableStateFlow(null)
    }

    companion object {
        fun provideFactory(
            keyDao: KeyDao, 
            userProfileDao: UserProfileDao, 
            nostrSyncManager: NostrSyncManager,
            secureStorage: SecureStorage,
            database: com.ely.kian.data.local.KianDatabase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(keyDao, userProfileDao, nostrSyncManager, secureStorage, database) as T
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
                keyDao.getKeyFlow().collectLatest { key ->
                    isLoggedIn = key != null
                    ownPubkey = key?.pubkey
                    if (key != null) {
                        nostrSyncManager.startSyncing(key.pubkey)
                        // Seed demo data if it's a test account (Optional check)
                        DemoDataSeeder.seedIfTestAccount(key.pubkey, userProfileDao, database.productDao())
                    }
                }
            } catch (e: Exception) {
                isLoggedIn = false
                Log.e("MainViewModel", "Auth flow failed", e)
            }
        }
        
        viewModelScope.launch {
            userProfile.collectLatest { profile ->
                if (profile != null) {
                    accountMode = if (profile.isTrader) "merchant" else "business"
                }
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
                    banner = null,
                    website = null,
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
            
            val contentObj = buildJsonObject {
                put("name", updatedProfile.name ?: "")
                put("display_name", updatedProfile.displayName ?: "")
                put("about", updatedProfile.about ?: "")
                put("picture", updatedProfile.picture ?: "")
                put("banner", updatedProfile.banner ?: "")
                put("website", updatedProfile.website ?: "")
                put("nip05", updatedProfile.nip05 ?: "")
            }
            val content = contentObj.toString()
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

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                try {
                    nostrSyncManager.stopSyncing()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to stop syncing", e)
                }

                try {
                    secureStorage.clearAll()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to clear secure storage", e)
                }
                
                try {
                    database.clearAllTables()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to clear database", e)
                }
                
                try {
                    keyDao.clearKeys()
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to clear keys", e)
                }
                
                Log.d("MainViewModel", "User logged out and local data wiped.")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Unexpected error during logout", e)
            } finally {
                onComplete()
            }
        }
    }

    fun backupDatabase(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val dbFile = context.getDatabasePath("kian_db")
                if (dbFile.exists()) {
                    val backupFile = java.io.File(context.cacheDir, "kian_backup_${System.currentTimeMillis()}.db")
                    dbFile.inputStream().use { input ->
                        backupFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val authority = "${context.packageName}.fileprovider"
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        authority,
                        backupFile
                    )
                    
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = android.content.ClipData.newRawUri("", uri)
                    }
                    
                    context.startActivity(android.content.Intent.createChooser(intent, "Share Backup"))
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Backup failed", e)
            }
        }
    }
}
