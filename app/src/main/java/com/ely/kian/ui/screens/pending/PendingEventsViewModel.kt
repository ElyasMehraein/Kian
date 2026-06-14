package com.ely.kian.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.RelayPoolManager
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class PendingEventItem(
    val id: String,
    val relayUrl: String,
    val kind: Int,
    val category: String,
    val content: String,
    val rawJson: String,
    val createdAt: Long
)

class PendingEventsViewModel(
    private val relayPool: RelayPoolManager,
    private val syncManager: NostrSyncManager,
    private val offlineQueueDao: com.ely.kian.data.local.dao.OfflineQueueDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ViewModel() {

    private val memoryEvents = relayPool.pendingMessagesFlow
        .map { map ->
            map.flatMap { (url, messages) ->
                messages.mapNotNull { message ->
                    parseToItem(url, message)
                }
            }
        }

    private val dbEvents = offlineQueueDao.getAll()
        .map { queue ->
            queue.map { item ->
                PendingEventItem(
                    id = item.eventId,
                    relayUrl = "Offline Storage",
                    kind = -1, // We don't know the kind easily from CBOR here without decoding
                    category = "Persistent: ${item.queueScope}",
                    content = "Event stored for later retry",
                    rawJson = "CBOR data (internal)",
                    createdAt = item.createdAt
                )
            }
        }

    val pendingEvents: StateFlow<List<PendingEventItem>> = combine(memoryEvents, dbEvents) { mem, db ->
        (mem + db).sortedByDescending { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun parseToItem(url: String, message: String): PendingEventItem? {
        return try {
            val array = json.parseToJsonElement(message).jsonArray
            if (array[0].jsonPrimitive.content != "EVENT") return null
            
            val eventJson = array[2].toString()
            val event = json.decodeFromString<NostrEvent>(eventJson)
            
            val category = getCategory(event)
            var displayContent = event.content
            
            if (event.kind == 1059) {
                val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1)
                displayContent = if (pTag != null) "Encrypted message for ${pTag.take(8)}..." else "Encrypted message"
            }
            
            PendingEventItem(
                id = event.id,
                relayUrl = url,
                kind = event.kind,
                category = category,
                content = displayContent,
                rawJson = eventJson,
                createdAt = event.createdAt
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getCategory(event: NostrEvent): String {
        return when (event.kind) {
            14 -> "Chat Message"
            0 -> "Profile Update"
            1050 -> "Token Transfer"
            1051 -> "Receipt Confirmation"
            35001 -> "Token Mint (Genesis)"
            35002 -> "Token Remint"
            1059 -> "Encrypted Message (GiftWrap)"
            5 -> "Deletion"
            else -> "Event (Kind ${event.kind})"
        }
    }

    fun processManualEvent(input: String) {
        viewModelScope.launch {
            try {
                val trimmed = input.trim()
                if (trimmed.isBlank()) return@launch
                
                if (trimmed.startsWith("[")) {
                    syncManager.processExternalEvent(trimmed)
                } else if (trimmed.startsWith("{")) {
                    // Wrap it as a Nostr protocol message
                    syncManager.processExternalEvent("[\"EVENT\", $trimmed]")
                }
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    companion object {
        fun provideFactory(
            relayPool: RelayPoolManager, 
            syncManager: NostrSyncManager,
            offlineQueueDao: com.ely.kian.data.local.dao.OfflineQueueDao
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PendingEventsViewModel(relayPool, syncManager, offlineQueueDao) as T
                }
            }
    }
}
