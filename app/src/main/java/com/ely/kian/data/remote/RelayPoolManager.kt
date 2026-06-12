package com.ely.kian.data.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _connectionStatesFlow = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStatesFlow: StateFlow<Map<String, ConnectionState>> = _connectionStatesFlow.asStateFlow()

    enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

    val eventChannel = Channel<Pair<String, String>>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun updateState(url: String, state: ConnectionState) {
        synchronized(connectionStates) {
            connectionStates[url] = state
            _connectionStatesFlow.value = connectionStates.toMap()
        }
    }

    fun connect(url: String, listener: WebSocketListener) {
        android.util.Log.d("RelayPoolManager", "Attempting to connect to $url")
        listeners.getOrPut(url) { mutableSetOf() }.add(listener)
        
        if (connectionStates[url] == ConnectionState.CONNECTED || connectionStates[url] == ConnectionState.CONNECTING) {
            android.util.Log.d("RelayPoolManager", "Already connected or connecting to $url")
            return 
        }

        updateState(url, ConnectionState.CONNECTING)
        val request = Request.Builder().url(url).build()
        
        val socketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                android.util.Log.i("RelayPoolManager", "Connected successfully to $url")
                updateState(url, ConnectionState.CONNECTED)
                
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
                updateState(url, ConnectionState.DISCONNECTED)
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
                updateState(url, ConnectionState.DISCONNECTED)
                sockets.remove(url)
                listeners[url]?.forEach { it.onClosing(webSocket, code, reason) }
            }
        }
        
        val webSocket = client.newWebSocket(request, socketListener)
        sockets[url] = webSocket
    }

    fun disconnect(url: String) {
        updateState(url, ConnectionState.DISCONNECTED)
        sockets[url]?.close(1000, "User requested disconnect")
        sockets.remove(url)
        listeners.remove(url)
    }

    fun disconnectAll() {
        synchronized(connectionStates) {
            sockets.forEach { (url, socket) ->
                socket.close(1000, "Disconnecting all")
            }
            sockets.clear()
            listeners.clear()
            connectionStates.clear()
            _connectionStatesFlow.value = emptyMap()
        }
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
