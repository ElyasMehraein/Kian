package com.ely.kian.data.remote

import android.util.Log
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class NostrSyncManager(
    private val relayPool: RelayPoolManager,
    private val userProfileDao: UserProfileDao,
    private val chatRepository: com.ely.kian.data.repository.ChatRepository? = null,
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
        defaultRelays.forEach { url ->
            relayPool.connect(url, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Connected to $url")
                    val traderFilter = """{"kinds": [0], "#t": ["trader"], "limit": 100}"""
                    relayPool.subscribe(url, "trader_sync", traderFilter)
                    
                    if (myPubkey != null) {
                        val dmFilter = """{"kinds": [4], "#p": ["$myPubkey"], "limit": 50}"""
                        relayPool.subscribe(url, "dm_recv_sync", dmFilter)
                        
                        val dmSentFilter = """{"kinds": [4], "authors": ["$myPubkey"], "limit": 50}"""
                        relayPool.subscribe(url, "dm_sent_sync", dmSentFilter)
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
            4 -> handleDirectMessage(event)
        }
    }

    private fun handleDirectMessage(event: NostrEvent) {
        syncScope.launch {
            chatRepository?.handleIncomingEvent(event)
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
