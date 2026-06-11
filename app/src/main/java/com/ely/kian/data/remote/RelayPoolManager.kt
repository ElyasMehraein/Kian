package com.ely.kian.data.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import java.util.concurrent.TimeUnit

class RelayPoolManager {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private val sockets = mutableMapOf<String, WebSocket>()
    private val listeners = mutableMapOf<String, MutableSet<WebSocketListener>>()
    
    // Message Queue for each relay (Amethyst style logic)
    private val pendingMessages = mutableMapOf<String, MutableList<String>>()
    private val connectionStates = mutableMapOf<String, ConnectionState>()

    enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

    val eventChannel = Channel<Pair<String, String>>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(url: String, listener: WebSocketListener) {
        listeners.getOrPut(url) { mutableSetOf() }.add(listener)
        
        if (connectionStates[url] == ConnectionState.CONNECTED || connectionStates[url] == ConnectionState.CONNECTING) return 

        connectionStates[url] = ConnectionState.CONNECTING
        val request = Request.Builder().url(url).build()
        
        val socketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                android.util.Log.d("RelayPoolManager", "Connected to $url")
                connectionStates[url] = ConnectionState.CONNECTED
                
                // Flush pending messages
                val queue = synchronized(pendingMessages) { pendingMessages[url]?.toList() ?: emptyList() }
                queue.forEach { webSocket.send(it) }
                synchronized(pendingMessages) { pendingMessages[url]?.clear() }
                
                listeners[url]?.forEach { it.onOpen(webSocket, response) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { eventChannel.send(url to text) }
                listeners[url]?.forEach { it.onMessage(webSocket, text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.e("RelayPoolManager", "Failure on $url: ${t.message}")
                connectionStates[url] = ConnectionState.DISCONNECTED
                sockets.remove(url)
                listeners[url]?.forEach { it.onFailure(webSocket, t, response) }
                
                // Automatic reconnection
                scope.launch {
                    delay(10000)
                    if (connectionStates[url] == ConnectionState.DISCONNECTED) {
                        connect(url, object : WebSocketListener() {})
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connectionStates[url] = ConnectionState.DISCONNECTED
                sockets.remove(url)
                listeners[url]?.forEach { it.onClosing(webSocket, code, reason) }
            }
        }
        
        val webSocket = client.newWebSocket(request, socketListener)
        sockets[url] = webSocket
    }

    fun disconnect(url: String) {
        connectionStates[url] = ConnectionState.DISCONNECTED
        sockets[url]?.close(1000, "User requested disconnect")
        sockets.remove(url)
        listeners.remove(url)
    }

    fun disconnectAll() {
        sockets.forEach { (url, socket) ->
            connectionStates[url] = ConnectionState.DISCONNECTED
            socket.close(1000, "Disconnecting all")
        }
        sockets.clear()
        listeners.clear()
    }

    fun publish(url: String, eventJson: String) {
        val socket = sockets[url]
        if (connectionStates[url] == ConnectionState.CONNECTED && socket != null) {
            val success = socket.send(eventJson)
            if (!success) {
                queueMessage(url, eventJson)
            }
        } else {
            queueMessage(url, eventJson)
            if (connectionStates[url] != ConnectionState.CONNECTING) {
                connect(url, object : WebSocketListener() {})
            }
        }
    }

    private fun queueMessage(url: String, message: String) {
        synchronized(pendingMessages) {
            val queue = pendingMessages.getOrPut(url) { mutableListOf() }
            if (queue.size < 100) { 
                queue.add(message)
            }
        }
    }

    fun subscribe(url: String, subscriptionId: String, filtersJson: String) {
        val message = "[\"REQ\", \"$subscriptionId\", $filtersJson]"
        publish(url, message)
    }
}
