package com.ely.kian.ui.screens.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.data.repository.ProductRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MerchantProfileViewModel(
    private val pubkey: String,
    private val ownPubkey: String?,
    private val userProfileDao: UserProfileDao,
    private val productRepository: ProductRepository
) : ViewModel() {

    val profile: StateFlow<Profile?> = userProfileDao.getProfileFlow(pubkey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val products: StateFlow<List<Product>> = productRepository.getProducts(pubkey)
        .map { list -> list.filter { it.isShowcase } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<ProductCategory>> = productRepository.getCategories(pubkey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOwnProfile: Boolean = pubkey == ownPubkey

    companion object {
        fun provideFactory(pubkey: String, ownPubkey: String?, userProfileDao: UserProfileDao, productRepository: ProductRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MerchantProfileViewModel(pubkey, ownPubkey, userProfileDao, productRepository) as T
            }
        }
    }
}
