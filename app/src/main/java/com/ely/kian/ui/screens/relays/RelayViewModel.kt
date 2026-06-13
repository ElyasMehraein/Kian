package com.ely.kian.ui.screens.relays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.RelayDao
import com.ely.kian.data.local.entities.Relay
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.RelayPoolManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RelayViewModel(
    private val relayDao: RelayDao,
    private val relayPoolManager: RelayPoolManager,
    private val nostrSyncManager: NostrSyncManager
) : ViewModel() {

    val relays: StateFlow<List<Relay>> = relayDao.getAllRelays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionStates: StateFlow<Map<String, RelayPoolManager.ConnectionState>> = 
        relayPoolManager.connectionStatesFlow

    fun addRelay(url: String) {
        viewModelScope.launch {
            val relay = Relay(url, readEnabled = true, writeEnabled = true, isActive = true)
            relayDao.insertRelay(relay)
            // Re-start syncing to include new relay
            nostrSyncManager.startSyncing()
        }
    }

    fun toggleRelay(relay: Relay, active: Boolean) {
        viewModelScope.launch {
            relayDao.updateRelayActiveState(relay.url, active)
            if (active) {
                nostrSyncManager.startSyncing() // Re-connect if activated
            } else {
                relayPoolManager.disconnect(relay.url) // Disconnect if deactivated
            }
        }
    }

    fun removeRelay(relay: Relay) {
        viewModelScope.launch {
            relayDao.deleteRelay(relay)
            relayPoolManager.disconnect(relay.url)
        }
    }

    fun reconnectAll() {
        nostrSyncManager.startSyncing()
    }

    companion object {
        fun provideFactory(
            relayDao: RelayDao,
            relayPoolManager: RelayPoolManager,
            nostrSyncManager: NostrSyncManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RelayViewModel(relayDao, relayPoolManager, nostrSyncManager) as T
            }
        }
    }
}
