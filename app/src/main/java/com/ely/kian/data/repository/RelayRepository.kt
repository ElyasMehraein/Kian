package com.ely.kian.data.repository

import com.ely.kian.data.local.dao.RelayDao
import com.ely.kian.data.local.entities.Relay
import kotlinx.coroutines.flow.Flow

class RelayRepository(private val relayDao: RelayDao) {
    fun getAllRelays(): Flow<List<Relay>> = relayDao.getAllRelays()

    suspend fun addRelay(url: String) {
        relayDao.insertRelay(Relay(url = url))
    }

    suspend fun removeRelay(url: String) {
        relayDao.deleteRelay(Relay(url = url))
    }

    suspend fun updateRelaySettings(url: String, read: Boolean, write: Boolean) {
        relayDao.updateRelaySettings(url, read, write)
    }
}
