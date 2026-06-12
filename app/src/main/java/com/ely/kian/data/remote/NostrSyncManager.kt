package com.ely.kian.data.remote

import android.util.Log
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class NostrSyncManager(
    private val relayPool: RelayPoolManager,
    private val userProfileDao: UserProfileDao,
    private val relayDao: com.ely.kian.data.local.dao.RelayDao? = null,
    private val eventProcessorProvider: () -> EventProcessor,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val eventProcessor by lazy { eventProcessorProvider() }
    private val TAG = "NostrSyncManager"
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val defaultRelays = listOf(
        "ws://192.168.1.14:8080"
    )

    fun startSyncing(myPubkey: String? = null) {
        stopSyncing()
        
        // Amethyst-style: Message Processor Loop
        syncScope.launch {
            for ((url, message) in relayPool.eventChannel) {
                handleMessage(message)
            }
        }
        
        syncScope.launch {
            val allRelays = mutableSetOf<String>()
            
            // 1. Fetch from Relay DAO (Global list)
            try {
                val savedRelays = relayDao?.getAllRelays()?.first() ?: emptyList()
                if (savedRelays.isEmpty()) {
                    // Seed with local relay if empty
                    defaultRelays.forEach { 
                        relayDao?.insertRelay(com.ely.kian.data.local.entities.Relay(it, true, true))
                    }
                    allRelays.addAll(defaultRelays)
                } else {
                    allRelays.addAll(savedRelays.map { it.url })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch saved relays", e)
                allRelays.addAll(defaultRelays)
            }

            // 2. Fetch Inbox Relays for me
            if (myPubkey != null) {
                try {
                    val inboxUrls = relayDao?.getDmInboxRelayUrls(myPubkey) ?: emptyList()
                    allRelays.addAll(inboxUrls)
                } catch (e: Exception) {
                    Log.e(TAG, "Relay fetch error", e)
                }
            }

            allRelays.forEach { url ->
                connectToRelay(url, myPubkey)
            }
        }
    }

    private fun connectToRelay(url: String, myPubkey: String?) {
        relayPool.connect(url, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to $url")
                
                // 1. Trader sync (Global for market)
                val traderFilter = """{"kinds": [0, 10002, 10050], "#t": ["trader"], "limit": 100}"""
                relayPool.subscribe(url, "trader_sync", traderFilter)
                
                if (myPubkey != null) {
                    // 2. Metadata and Relay lists
                    val inboxRelayFilter = """{"kinds": [0, 3, 10002, 10050], "authors": ["$myPubkey"], "limit": 5}"""
                    relayPool.subscribe(url, "my_meta_sync", inboxRelayFilter)
                    
                    // 3. Direct Messages (GiftWrap)
                    val dmFilter = """{"kinds": [1059], "#p": ["$myPubkey"], "since": ${System.currentTimeMillis() / 1000 - 86400 * 7}}"""
                    relayPool.subscribe(url, "my_dm_sync", dmFilter)

                    // 4. Receipts and Status (Kind 20001, 20002)
                    val receiptFilter = """{"kinds": [20001, 20002], "#p": ["$myPubkey"], "limit": 50}"""
                    relayPool.subscribe(url, "my_receipts_sync", receiptFilter)
                    
                    // 5. Product/Token events (Self)
                    val inventoryFilter = """{"kinds": [30017, 30018, 35001], "authors": ["$myPubkey"]}"""
                    relayPool.subscribe(url, "my_inventory_sync", inventoryFilter)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Error on $url: ${t.message}")
            }
        })
    }

    fun stopSyncing() {
        relayPool.disconnectAll()
        // Cancel any pending work in the sync scope to avoid processing old events
        syncScope.coroutineContext[kotlinx.coroutines.Job]?.cancelChildren()
        Log.d(TAG, "Syncing stopped and pending work cancelled")
    }

    private fun handleMessage(message: String) {
        try {
            val element = json.parseToJsonElement(message)
            if (element !is JsonArray) return
            
            val type = element[0].jsonPrimitive.content
            if (type == "EVENT" && element.size >= 3) {
                val eventJson = element[2].toString()
                val event = json.decodeFromString<NostrEvent>(eventJson)
                handleEvent(event)
            }
        } catch (e: Exception) {
            // Log.e(TAG, "Failed to parse message", e)
        }
    }

    private fun handleEvent(event: NostrEvent) {
        syncScope.launch {
            eventProcessor.process(event)
        }
        
        // Handle sync-manager specific tasks (like updating relay lists)
        when (event.kind) {
            3 -> handleFollowList(event)
            10050, 10002 -> handleInboxRelays(event)
        }
    }

    private fun handleInboxRelays(event: NostrEvent) {
        val relayUrls = if (event.kind == 10050) {
            event.tags.filter { it.size >= 2 && it[0] == "relay" }.map { it[1] }
        } else {
            // NIP-65
            event.tags.filter { it.size >= 2 && it[0] == "r" }.map { it[1] }
        }
        
        if (relayUrls.isNotEmpty()) {
            syncScope.launch {
                val dmInboxRelays = relayUrls.map { url ->
                    com.ely.kian.data.local.entities.DmInboxRelay(
                        pubkey = event.pubkey,
                        relayUrl = url,
                        createdAt = event.createdAt
                    )
                }
                relayDao?.replaceDmInboxRelays(event.pubkey, dmInboxRelays)
                Log.d(TAG, "Saved ${relayUrls.size} relays (kind=${event.kind}) for ${event.pubkey}")
            }
        }
    }

    private fun handleFollowList(event: NostrEvent) {
        val follows = event.tags.filter { it.size >= 2 && it[0] == "p" }.map { tag ->
            com.ely.kian.data.local.entities.UserFollow(
                pubkey = event.pubkey,
                followsPubkey = tag[1],
                petName = if (tag.size >= 4) tag[3] else null,
                relayHint = if (tag.size >= 3) tag[2] else null,
                createdAt = event.createdAt
            )
        }
        syncScope.launch {
            userProfileDao.replaceFollows(event.pubkey, follows)
            Log.d(TAG, "Saved ${follows.size} followings for ${event.pubkey}")
        }
    }

    fun publishEvent(event: NostrEvent, targetRelays: List<String>? = null) {
        val eventJson = json.encodeToString(NostrEvent.serializer(), event)
        val message = "[\"EVENT\", $eventJson]"
        
        syncScope.launch {
            val relaysToUse = if (!targetRelays.isNullOrEmpty()) {
                (defaultRelays + targetRelays).distinct()
            } else {
                try {
                    val saved = relayDao?.getAllRelays()?.first()?.map { it.url } ?: emptyList()
                    if (saved.isEmpty()) defaultRelays else saved
                } catch (e: Exception) {
                    defaultRelays
                }
            }

            Log.d(TAG, "Publishing event kind=${event.kind} to ${relaysToUse.size} relays")
            relaysToUse.forEach { url ->
                relayPool.publish(url, message)
            }
        }
    }
}
