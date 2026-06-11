package com.ely.kian.data.remote

import android.util.Log
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class NostrSyncManager(
    private val relayPool: RelayPoolManager,
    private val userProfileDao: UserProfileDao,
    private val relayDao: com.ely.kian.data.local.dao.RelayDao? = null,
    private val chatRepository: com.ely.kian.data.repository.ChatRepository? = null,
    private val productRepository: com.ely.kian.data.repository.ProductRepository? = null,
    private val tokenRepository: com.ely.kian.data.repository.TokenRepository? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val TAG = "NostrSyncManager"
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val defaultRelays = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.nostr.band"
    )

    fun startSyncing(myPubkey: String? = null) {
        stopSyncing() // Ensure everything is stopped first
        
        defaultRelays.forEach { url ->
            relayPool.connect(url, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Connected to $url")
                    val traderFilter = """{"kinds": [0, 10050], "#t": ["trader"], "limit": 100}"""
                    relayPool.subscribe(url, "trader_sync", traderFilter)
                    
                    if (myPubkey != null) {
                        val dmFilter = """{"kinds": [4, 14, 1059, 20001, 20002], "#p": ["$myPubkey"], "limit": 50}"""
                        relayPool.subscribe(url, "dm_recv_sync", dmFilter)
                        
                        val dmSentFilter = """{"kinds": [4, 14, 1059, 20001, 20002], "authors": ["$myPubkey"], "limit": 50}"""
                        relayPool.subscribe(url, "dm_sent_sync", dmSentFilter)
                        
                        val inboxRelayFilter = """{"kinds": [10050], "authors": ["$myPubkey"], "limit": 1}"""
                        relayPool.subscribe(url, "my_inbox_relays", inboxRelayFilter)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "Error on $url: ${t.message}")
                }
            })
        }
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
        when (event.kind) {
            0 -> handleMetadata(event)
            3 -> handleFollowList(event)
            10050 -> handleInboxRelays(event)
            4, 14, 1050, 1059, 15001, 20001, 20002 -> handleChatEvent(event)
            30017, 30018 -> handleProductEvent(event)
            31999 -> handleReviewEvent(event)
            35001, 35002 -> handleTokenEvent(event)
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

    private fun handleChatEvent(event: NostrEvent) {
        syncScope.launch {
            chatRepository?.handleIncomingEvent(event)
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

    private fun handleProductEvent(event: NostrEvent) {
        syncScope.launch {
            productRepository?.handleProductEvent(event)
        }
    }

    private fun handleReviewEvent(event: NostrEvent) {
        // Kind 31999: All-in-One reviews
        syncScope.launch {
            try {
                val reviewsJson = json.parseToJsonElement(event.content).jsonObject
                // TODO: Implement parsing and saving reviews to reviewDao
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse reviews", e)
            }
        }
    }

    private fun handleTokenEvent(event: NostrEvent) {
        syncScope.launch {
            tokenRepository?.handleTokenEvent(event)
        }
    }

    private fun handleMetadata(event: NostrEvent) {
        val isTrader = event.tags.any { it.size >= 2 && it[0] == "t" && it[1] == "trader" }
        
        try {
            val content = json.parseToJsonElement(event.content).jsonObject
            val profile = Profile(
                pubkey = event.pubkey,
                name = content["name"]?.jsonPrimitive?.contentOrNull,
                displayName = content["display_name"]?.jsonPrimitive?.contentOrNull ?: content["name"]?.jsonPrimitive?.contentOrNull,
                about = content["about"]?.jsonPrimitive?.contentOrNull,
                picture = content["picture"]?.jsonPrimitive?.contentOrNull,
                nip05 = content["nip05"]?.jsonPrimitive?.contentOrNull,
                geohash = content["geohash"]?.jsonPrimitive?.contentOrNull,
                rawJson = event.content,
                isTrader = isTrader,
                createdAt = event.createdAt,
                updatedAt = System.currentTimeMillis() / 1000
            )

            syncScope.launch {
                // Throttle updates slightly to prevent DB/UI pressure
                delay(50) 
                userProfileDao.upsert(profile)
                Log.d(TAG, "Saved profile for ${profile.pubkey}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse metadata content", e)
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
