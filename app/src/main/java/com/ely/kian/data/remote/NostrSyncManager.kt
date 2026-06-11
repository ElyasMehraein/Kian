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
    private val eventProcessor: EventProcessor,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val TAG = "NostrSyncManager"
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val defaultRelays = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.nostr.band",
        "wss://relay.snort.social",
        "wss://offchain.pub",
        "wss://atlas.nostr.land"
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
            val allRelays = defaultRelays.toMutableSet()
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
                val traderFilter = """{"kinds": [0, 10050], "#t": ["trader"], "limit": 100}"""
                relayPool.subscribe(url, "trader_sync", traderFilter)
                
                if (myPubkey != null) {
                    // 2. Metadata and Relay lists
                    val inboxRelayFilter = """{"kinds": [0, 3, 10002, 10050], "authors": ["$myPubkey"], "limit": 5}"""
                    relayPool.subscribe(url, "my_meta_sync", inboxRelayFilter)
                    
                    // 3. Product/Token events (Self)
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
            10050 -> handleInboxRelays(event)
        }
    }

    private fun handleInboxRelays(event: NostrEvent) {
        val relayUrls = event.tags.filter { it.size >= 2 && it[0] == "relay" }.map { it[1] }
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
                Log.d(TAG, "Saved ${relayUrls.size} inbox relays for ${event.pubkey}")
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

    fun publishEvent(event: NostrEvent) {
        val eventJson = json.encodeToString(NostrEvent.serializer(), event)
        val message = "[\"EVENT\", $eventJson]"
        defaultRelays.forEach { url ->
            relayPool.publish(url, message)
        }
    }
}
