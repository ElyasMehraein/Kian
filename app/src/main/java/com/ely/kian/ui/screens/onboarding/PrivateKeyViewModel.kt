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
import kotlinx.coroutines.launch

class PrivateKeyViewModel(
    private val keyDao: KeyDao,
    private val secureStorage: SecureStorage
) : ViewModel() {

    var pubkey by mutableStateOf<String?>(null)
        private set
    var privateKey by mutableStateOf<String?>(null)
        private set
    var mnemonic by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val savedKey = keyDao.getKey()
            pubkey = savedKey?.npub ?: savedKey?.pubkey
            
            val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY)
            privateKey = privKeyHex?.let {
                try {
                    KianKeys.toNsec(KianKeys.hexToBytes(it))
                } catch (e: Exception) {
                    it
                }
            }
            
            mnemonic = secureStorage.getSecret(SecureStorage.MNEMONIC)
        }
    }

    companion object {
        fun provideFactory(keyDao: KeyDao, secureStorage: SecureStorage): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PrivateKeyViewModel(keyDao, secureStorage) as T
            }
        }
    }
}
