package com.ely.kian.ui.screens.products

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProductViewModel(
    private val productRepository: ProductRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    companion object {
        fun provideFactory(
            productRepository: ProductRepository,
            secureStorage: SecureStorage
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProductViewModel(productRepository, secureStorage) as T
            }
        }
    }

    var pubkey by mutableStateOf<String?>(null)
        private set

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products

    private val _categories = MutableStateFlow<List<ProductCategory>>(emptyList())
    val categories: StateFlow<List<ProductCategory>> = _categories

    var selectedFilterPath by mutableStateOf<List<ProductCategory>>(emptyList())

    init {
        loadData()
    }

    private fun loadData() {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY)
        if (privKeyHex != null) {
            val pk = KianKeys.getPubKey(KianKeys.hexToBytes(privKeyHex))
            pubkey = KianKeys.bytesToHex(pk)
            
            viewModelScope.launch {
                productRepository.getProducts(pubkey!!).collectLatest {
                    _products.value = it
                }
            }
            viewModelScope.launch {
                productRepository.getCategories(pubkey!!).collectLatest {
                    _categories.value = it
                }
            }
        }
    }

    fun saveProduct(
        id: String?,
        name: String,
        description: String,
        imageUrls: String,
        categoryIds: List<String>
    ) {
        viewModelScope.launch {
            val images = imageUrls.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            productRepository.saveProduct(id, name, description, images, categoryIds)
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            productRepository.deleteProduct(product.id, product.eventId)
        }
    }

    fun saveCategory(name: String, parent: ProductCategory?) {
        viewModelScope.launch {
            productRepository.saveCategory(name, parent?.id, (parent?.level ?: 0) + 1)
        }
    }

    fun deleteCategory(category: ProductCategory) {
        viewModelScope.launch {
            val allIds = getBranchIds(category.id)
            productRepository.deleteCategoryBranch(allIds)
        }
    }

    private fun getBranchIds(rootId: String): List<String> {
        val ids = mutableListOf(rootId)
        val children = _categories.value.filter { it.parentId == rootId }
        for (child in children) {
            ids.addAll(getBranchIds(child.id))
        }
        return ids
    }
}
