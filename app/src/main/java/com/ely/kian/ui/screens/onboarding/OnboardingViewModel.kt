package com.ely.kian.ui.screens.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.KeyDao
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.dao.ProductDao
import com.ely.kian.data.local.dao.ReviewDao
import com.ely.kian.data.local.dao.TokenDao
import com.ely.kian.data.local.DemoDataSeeder
import com.ely.kian.data.local.entities.Key
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class GeneratedKeys(
    val mnemonic: String,
    val pubkey: String, // Hex
    val npub: String,   // npub1...
    val nsec: String,   // nsec1...
    val privKey: ByteArray
)

class OnboardingViewModel(
    private val keyDao: KeyDao,
    private val userProfileDao: UserProfileDao,
    private val productDao: ProductDao,
    private val tokenDao: TokenDao,
    private val reviewDao: ReviewDao,
    private val secureStorage: SecureStorage
) : ViewModel() {

    companion object {
        fun provideFactory(
            keyDao: KeyDao,
            userProfileDao: UserProfileDao,
            productDao: ProductDao,
            tokenDao: TokenDao,
            reviewDao: ReviewDao,
            secureStorage: SecureStorage
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OnboardingViewModel(keyDao, userProfileDao, productDao, tokenDao, reviewDao, secureStorage) as T
            }
        }
    }

    var generatedKeys by mutableStateOf<GeneratedKeys?>(null)
        private set

    var mnemonicInput by mutableStateOf("")
    var privateKeyInput by mutableStateOf("")
    
    var isSaving by mutableStateOf(false)
        private set

    var savedKey by mutableStateOf<Key?>(null)
        private set

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            savedKey = keyDao.getKey()
        }
    }

    fun handleGenerate() {
        viewModelScope.launch {
            try {
                val mnemonic = KianKeys.generateMnemonic()
                val privKey = KianKeys.derivePrivKey(mnemonic)
                val pubkeyBytes = KianKeys.getPubKey(privKey)
                val pubkeyHex = KianKeys.bytesToHex(pubkeyBytes)
                
                generatedKeys = GeneratedKeys(
                    mnemonic = mnemonic,
                    pubkey = pubkeyHex,
                    npub = KianKeys.toNpub(pubkeyBytes),
                    nsec = KianKeys.toNsec(privKey),
                    privKey = privKey
                )
                mnemonicInput = ""
            } catch (e: Exception) {
                android.util.Log.e("OnboardingViewModel", "Generation failed", e)
                _events.emit(OnboardingEvent.Error("Generation failed: ${e.message}"))
            }
        }
    }

    fun handleRestore() {
        val trimmed = mnemonicInput.trim().replace("\\s+".toRegex(), " ")
        if (trimmed.isEmpty() || isSaving) return

        viewModelScope.launch {
            try {
                val privKey = KianKeys.derivePrivKey(trimmed)
                val pubkeyBytes = KianKeys.getPubKey(privKey)
                val pubkeyHex = KianKeys.bytesToHex(pubkeyBytes)
                
                secureStorage.saveSecret(SecureStorage.MNEMONIC, trimmed)
                secureStorage.saveSecret(SecureStorage.PRIVATE_KEY, KianKeys.bytesToHex(privKey))
                
                persistKeyPair(pubkeyHex, "Your wallet was restored securely.")
            } catch (e: Exception) {
                _events.emit(OnboardingEvent.Error("Restore failed: ${e.message}"))
            }
        }
    }

    fun handleRestoreFromPrivateKey() {
        val input = privateKeyInput.trim()
        if (input.isEmpty() || isSaving) return

        viewModelScope.launch {
            try {
                val privKey = if (input.startsWith("nsec")) {
                    KianKeys.nsecToPrivKey(input)
                } else {
                    KianKeys.hexToBytes(input)
                }

                if (privKey.size != 32) {
                    throw Exception("Invalid private key length")
                }

                val pubkeyBytes = KianKeys.getPubKey(privKey)
                val pubkeyHex = KianKeys.bytesToHex(pubkeyBytes)

                // Note: Restoring from private key means we don't have the mnemonic.
                secureStorage.saveSecret(SecureStorage.PRIVATE_KEY, KianKeys.bytesToHex(privKey))
                
                persistKeyPair(pubkeyHex, "Logged in with private key.")
            } catch (e: Exception) {
                _events.emit(OnboardingEvent.Error("Invalid private key: ${e.message}"))
            }
        }
    }

    fun handleLogBackIn() {
        val key = savedKey ?: return
        if (isSaving) return

        viewModelScope.launch {
            persistKeyPair(key.pubkey, "Logged back in with your saved keypair.")
        }
    }

    fun saveGeneratedKeys() {
        val keys = generatedKeys ?: return
        viewModelScope.launch {
            secureStorage.saveSecret(SecureStorage.MNEMONIC, keys.mnemonic)
            secureStorage.saveSecret(SecureStorage.PRIVATE_KEY, KianKeys.bytesToHex(keys.privKey))
            persistKeyPair(keys.pubkey, "Your keys were stored securely.")
        }
    }

    fun handleDemoLogin(index: Int) {
        if (isSaving) return
        val demoKeys = listOf(
            "990429f579124430f8810901e18d667c48f860162590240d8927e8a93946a48f",
            "1b3c95e1e0d37e69f82d2f7035f2d65604921665a382e887e8346e279313936a",
            "6e8e89f77349141019a16f9f9586737402636250796f7572206f776e20746573"
        )
        val privKeyHex = demoKeys.getOrNull(index) ?: return
        
        viewModelScope.launch {
            try {
                val privKey = KianKeys.hexToBytes(privKeyHex)
                val pubkeyBytes = KianKeys.getPubKey(privKey)
                val pubkeyHex = KianKeys.bytesToHex(pubkeyBytes)
                
                // Inject data immediately
                DemoDataSeeder.forceSeed(pubkeyHex, index, userProfileDao, productDao, tokenDao, reviewDao)
                
                secureStorage.saveSecret(SecureStorage.PRIVATE_KEY, privKeyHex)
                persistKeyPair(pubkeyHex, "Logged in as Demo Account ${index + 1}.")
            } catch (e: Exception) {
                _events.emit(OnboardingEvent.Error("Demo login failed: ${e.message}"))
            }
        }
    }

    private suspend fun persistKeyPair(pubkeyHex: String, message: String) {
        isSaving = true
        try {
            val pubkeyBytes = KianKeys.hexToBytes(pubkeyHex)
            val npub = KianKeys.toNpub(pubkeyBytes)
            
            val key = Key(
                pubkey = pubkeyHex,
                npub = npub,
                createdAt = System.currentTimeMillis() / 1000
            )
            keyDao.saveKeyPair(key)
            _events.emit(OnboardingEvent.Success(message))
        } catch (e: Exception) {
            _events.emit(OnboardingEvent.Error("Save failed: ${e.message}"))
        } finally {
            isSaving = false
        }
    }
}

sealed class OnboardingEvent {
    data class Success(val message: String) : OnboardingEvent()
    data class Error(val message: String) : OnboardingEvent()
}
