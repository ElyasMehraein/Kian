package com.ely.kian.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.R
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.RelayPoolManager
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

enum class PendingCategoryType(val labelResId: Int, val icon: String) {
    ALL(R.string.cat_all, "📋"),
    TRANSFERS(R.string.cat_transfers, "💸"),
    REQUESTS(R.string.cat_requests, "💬"),
    PROFILE(R.string.cat_profile, "👤"),
    OTHER(R.string.cat_other, "⚙️")
}

data class PendingEventItem(
    val id: String,
    val relayUrl: String,
    val kind: Int,
    val category: String,
    val categoryType: PendingCategoryType,
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

    val selectedCategory = MutableStateFlow(PendingCategoryType.ALL)

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
                    kind = -1,
                    category = "Persistent: ${item.queueScope}",
                    categoryType = if (item.queueScope.contains("transfer")) PendingCategoryType.TRANSFERS else PendingCategoryType.OTHER,
                    content = "Event stored for later retry",
                    rawJson = "CBOR data (internal)",
                    createdAt = item.createdAt
                )
            }
        }

    val pendingEvents: StateFlow<List<PendingEventItem>> = combine(memoryEvents, dbEvents) { mem, db ->
        (mem + db).distinctBy { "${it.id}_${it.relayUrl}" }.sortedByDescending { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredEvents: StateFlow<List<PendingEventItem>> = combine(pendingEvents, selectedCategory) { events, cat ->
        if (cat == PendingCategoryType.ALL) events
        else events.filter { it.categoryType == cat }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryCounts: StateFlow<Map<PendingCategoryType, Int>> = pendingEvents.map { list ->
        val counts = mutableMapOf<PendingCategoryType, Int>()
        counts[PendingCategoryType.ALL] = list.size
        PendingCategoryType.entries.filter { it != PendingCategoryType.ALL }.forEach { type ->
            counts[type] = list.count { it.categoryType == type }
        }
        counts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun selectCategory(category: PendingCategoryType) {
        selectedCategory.value = category
    }

    private fun parseToItem(url: String, message: String): PendingEventItem? {
        return try {
            val element = json.parseToJsonElement(message)
            if (element !is JsonArray || element.isEmpty()) return null
            if (element[0].jsonPrimitive.content != "EVENT") return null
            
            val eventElement = if (element.size >= 3) element[2] else element[1]
            val eventJson = if (eventElement is JsonPrimitive && eventElement.isString) {
                eventElement.content
            } else {
                eventElement.toString()
            }
            
            val event = json.decodeFromString<NostrEvent>(eventJson)
            val categoryType = getCategoryType(event.kind)
            val categoryName = getCategoryName(event)
            var displayContent = event.content
            
            if (event.kind == 1059) {
                val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1)
                displayContent = if (pTag != null) "Encrypted message for ${pTag.take(8)}..." else "Encrypted message"
            }
            
            PendingEventItem(
                id = event.id,
                relayUrl = url,
                kind = event.kind,
                category = categoryName,
                categoryType = categoryType,
                content = displayContent,
                rawJson = eventJson,
                createdAt = event.createdAt
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getCategoryType(kind: Int): PendingCategoryType {
        return when (kind) {
            0 -> PendingCategoryType.PROFILE
            1050, 1051, 35001, 35002 -> PendingCategoryType.TRANSFERS
            14, 1059, 1 -> PendingCategoryType.REQUESTS
            else -> PendingCategoryType.OTHER
        }
    }

    private fun getCategoryName(event: NostrEvent): String {
        return when (event.kind) {
            14, 1 -> "Chat Message"
            0 -> "Profile Update"
            1050 -> "Token Transfer"
            1051 -> "Receipt Confirmation"
            35001 -> "Token Mint (Genesis)"
            35002 -> "Token Remint"
            1059 -> "Encrypted Request/Message"
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
                    syncManager.processExternalEvent("[\"EVENT\", $trimmed]")
                }
            } catch (e: Exception) {
                // Ignore
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
