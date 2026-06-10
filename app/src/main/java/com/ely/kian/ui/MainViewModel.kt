package com.ely.kian.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.KeyDao
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    private val keyDao: KeyDao
) : ViewModel() {

    companion object {
        fun provideFactory(keyDao: KeyDao): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(keyDao) as T
            }
        }
    }

    var isLoggedIn by mutableStateOf<Boolean?>(null)
        private set

    init {
        viewModelScope.launch {
            keyDao.getKeyFlow().collectLatest { key ->
                isLoggedIn = key != null
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            keyDao.clearKeys()
        }
    }
}
