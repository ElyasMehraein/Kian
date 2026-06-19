package com.ely.kian.ui.screens.vouchers

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import com.ely.kian.R
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

    var searchQuery by mutableStateOf("")
        private set

    var selectedCategoryId by mutableStateOf<String?>(null)
        private set

    val myPubkey: StateFlow<String?> = keyDao.getKeyFlow()
        .map { it?.pubkey }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    sealed class UiEvent {
        data class Alert(val title: String, val message: String) : UiEvent()
        data class AlertRes(@StringRes val title: Int, @StringRes val message: Int) : UiEvent()
    }

    fun setSearch(query: String) {
        searchQuery = query
    }

    fun selectCategory(categoryId: String?) {
        if (categoryId == null) {
            selectedCategoryId = null
        } else {
            selectedCategoryId = if (selectedCategoryId == categoryId) null else categoryId
        }
    }

    fun setFilter(filter: String) {
        activityFilter = filter
    }

    fun toggleCategoryShowcase(categoryId: String, isShowcase: Boolean) {
        viewModelScope.launch {
            voucherRepository.updateCategoryShowcase(categoryId, isShowcase)
        }
    }

    fun toggleAssetShowcase(assetRef: String, isShowcase: Boolean) {
        viewModelScope.launch {
            if (isShowcase) {
                val currentBalance = balances.value.find { it.assetRef == assetRef }
                if (currentBalance == null || currentBalance.categories.isEmpty()) {
                    _uiEvent.emit(UiEvent.AlertRes(
                        R.string.showcase_error, 
                        R.string.showcase_no_category_desc
                    ))
                    return@launch
                }
            }
            voucherRepository.updateAssetShowcase(assetRef, isShowcase)
        }
    }

    fun updateVoucherCategories(assetRef: String, categoryIds: List<String>) {
        viewModelScope.launch {
            voucherRepository.updateVoucherCategories(assetRef, categoryIds)
        }
    }

    fun formatAssetRef(assetRef: String): String {
        if (assetRef.length < 16) return assetRef
        return "${assetRef.take(10)}...${assetRef.takeLast(6)}"
    }

    fun mintToken(name: String, description: String, imageUrls: String, quantity: Long) {
        viewModelScope.launch {
            val images = imageUrls.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            voucherRepository.mintToken(name, description, images, quantity)
        }
    }

    fun burnToken(assetRef: String) {
        viewModelScope.launch {
            voucherRepository.burnToken(assetRef)
        }
    }

    fun saveCategory(name: String, parent: VoucherCategory?) {
        viewModelScope.launch {
            val pubkey = keyDao.getKey()?.pubkey ?: return@launch
            val level = (parent?.level ?: 0) + 1
            if (level > 5) {
                _uiEvent.emit(UiEvent.AlertRes(R.string.depth_limit_reached, R.string.depth_limit_desc))
                return@launch
            }
            voucherRepository.saveCategory(name, parent?.id, level, pubkey)
        }
    }

    fun deleteCategory(category: VoucherCategory) {
        viewModelScope.launch {
            val pubkey = keyDao.getKey()?.pubkey ?: return@launch
            val allIds = getBranchIds(category.id)
            
            if (voucherRepository.isCategoryInUse(allIds, pubkey)) {
                _uiEvent.emit(UiEvent.AlertRes(R.string.category_in_use, R.string.category_in_use_desc))
                return@launch
            }

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
