package com.ely.kian.ui.screens.vouchers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.KeyDao
import com.ely.kian.data.local.entities.VoucherUtxo
import com.ely.kian.data.local.entities.VoucherCategory
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.data.repository.PendingItem
import com.ely.kian.data.repository.VoucherRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VoucherViewModel(
    private val voucherRepository: VoucherRepository,
    private val keyDao: KeyDao
) : ViewModel() {

    companion object {
        fun provideFactory(
            voucherRepository: VoucherRepository,
            keyDao: KeyDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VoucherViewModel(voucherRepository, keyDao) as T
            }
        }
    }

    val balances: StateFlow<List<BalanceItem>> = voucherRepository.getBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val utxos: StateFlow<List<VoucherUtxo>> = voucherRepository.getUtxos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pending: StateFlow<List<PendingItem>> = voucherRepository.getPendingConfirmations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val myCategories: StateFlow<List<VoucherCategory>> = keyDao.getKeyFlow()
        .flatMapLatest { key ->
            if (key != null) voucherRepository.getCategories(key.pubkey)
            else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var activityFilter by mutableStateOf("all")
        private set

    fun setFilter(filter: String) {
        activityFilter = filter
    }

    fun toggleShowcase(assetRef: String, isShowcase: Boolean) {
        viewModelScope.launch {
            try {
                voucherRepository.updateShowcase(assetRef, isShowcase)
            } catch (e: Exception) {
                // Show error
            }
        }
    }

    fun updateTokenDetails(assetRef: String, name: String, description: String, categories: List<String>) {
        viewModelScope.launch {
            voucherRepository.updateTokenDetails(assetRef, name, description, categories)
        }
    }

    fun formatAssetRef(assetRef: String): String {
        if (assetRef.length < 16) return assetRef
        return "${assetRef.take(10)}...${assetRef.takeLast(6)}"
    }

    fun mintToken(name: String, description: String, imageUrls: String, quantity: Long, unit: String) {
        viewModelScope.launch {
            val images = imageUrls.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            voucherRepository.mintToken(name, description, images, quantity, unit)
        }
    }

    fun saveCategory(name: String, parent: VoucherCategory?) {
        viewModelScope.launch {
            val pubkey = keyDao.getKey()?.pubkey ?: return@launch
            voucherRepository.saveCategory(name, parent?.id, (parent?.level ?: 0) + 1, pubkey)
        }
    }

    fun deleteCategory(category: VoucherCategory) {
        viewModelScope.launch {
            val pubkey = keyDao.getKey()?.pubkey ?: return@launch
            val allIds = getBranchIds(category.id)
            voucherRepository.deleteCategoryBranch(allIds, pubkey)
        }
    }

    private fun getBranchIds(rootId: String): List<String> {
        val ids = mutableListOf(rootId)
        val children = myCategories.value.filter { it.parentId == rootId }
        for (child in children) {
            ids.addAll(getBranchIds(child.id))
        }
        return ids
    }
}
