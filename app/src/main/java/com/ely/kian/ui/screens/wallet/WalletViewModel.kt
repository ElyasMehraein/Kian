package com.ely.kian.ui.screens.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.KeyDao
import com.ely.kian.data.local.entities.TokenUtxo
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.data.repository.PendingItem
import com.ely.kian.data.repository.TokenRepository
import com.ely.kian.data.repository.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WalletViewModel(
    private val tokenRepository: TokenRepository,
    private val productRepository: ProductRepository,
    private val keyDao: KeyDao
) : ViewModel() {

    companion object {
        fun provideFactory(
            tokenRepository: TokenRepository,
            productRepository: ProductRepository,
            keyDao: KeyDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WalletViewModel(tokenRepository, productRepository, keyDao) as T
            }
        }
    }

    val balances: StateFlow<List<BalanceItem>> = tokenRepository.getBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val utxos: StateFlow<List<TokenUtxo>> = tokenRepository.getUtxos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pending: StateFlow<List<PendingItem>> = tokenRepository.getPendingConfirmations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val myCategories: StateFlow<List<ProductCategory>> = keyDao.getKeyFlow()
        .flatMapLatest { key ->
            if (key != null) productRepository.getCategories(key.pubkey)
            else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var activityFilter by mutableStateOf("all")
        private set

    fun setFilter(filter: String) {
        activityFilter = filter
    }

    fun toggleShowcase(assetRef: String, isShowcase: Boolean) {
        viewModelScope.launch {
            tokenRepository.updateShowcase(assetRef, isShowcase)
        }
    }

    fun updateTokenDetails(assetRef: String, name: String, description: String, categories: List<String>) {
        viewModelScope.launch {
            tokenRepository.updateTokenDetails(assetRef, name, description, categories)
        }
    }

    fun formatAssetRef(assetRef: String): String {
        if (assetRef.length < 16) return assetRef
        return "${assetRef.take(10)}...${assetRef.takeLast(6)}"
    }
}
