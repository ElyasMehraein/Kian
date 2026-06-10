package com.ely.kian.ui.screens.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.crypto.KianKeys
import com.ely.kian.data.local.dao.KeyDao
import com.ely.kian.data.local.entities.Key
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class GeneratedKeys(
    val mnemonic: String,
    val pubkey: String,
    val privKey: ByteArray
)

class OnboardingViewModel(
    private val keyDao: KeyDao
) : ViewModel() {

    companion object {
        fun provideFactory(keyDao: KeyDao): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OnboardingViewModel(keyDao) as T
            }
        }
    }

    var generatedKeys by mutableStateOf<GeneratedKeys?>(null)
        private set

    var mnemonicInput by mutableStateOf("")
    
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
                val pubkey = KianKeys.getPubKey(privKey)
                generatedKeys = GeneratedKeys(
                    mnemonic = mnemonic,
                    pubkey = KianKeys.bytesToHex(pubkey),
                    privKey = privKey
                )
                mnemonicInput = ""
            } catch (e: Exception) {
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
                val pubkey = KianKeys.getPubKey(privKey)
                val pubkeyHex = KianKeys.bytesToHex(pubkey)
                
                persistKeyPair(pubkeyHex, "Your wallet was restored securely.")
            } catch (e: Exception) {
                _events.emit(OnboardingEvent.Error("Restore failed: ${e.message}"))
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
            persistKeyPair(keys.pubkey, "Your keys were stored securely.")
        }
    }

    private suspend fun persistKeyPair(pubkey: String, message: String) {
        isSaving = true
        try {
            val key = Key(
                pubkey = pubkey,
                npub = pubkey, // Should convert to npub properly later
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
