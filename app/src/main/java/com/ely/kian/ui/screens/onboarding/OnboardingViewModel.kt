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
import com.ely.kian.data.local.dao.VoucherDao
import com.ely.kian.data.local.dao.ReviewDao
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
    private val voucherDao: VoucherDao,
    private val reviewDao: ReviewDao,
    private val secureStorage: SecureStorage
) : ViewModel() {

    companion object {
        fun provideFactory(
            keyDao: KeyDao,
            userProfileDao: UserProfileDao,
            voucherDao: VoucherDao,
            reviewDao: ReviewDao,
            secureStorage: SecureStorage
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OnboardingViewModel(keyDao, userProfileDao, voucherDao, reviewDao, secureStorage) as T
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
                
                persistKeyPair(pubkeyHex, "Your account was restored securely.")
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
