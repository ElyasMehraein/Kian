package com.ely.kian.data.remote

import okhttp3.*
import java.util.concurrent.TimeUnit

class RelayPoolManager {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private val sockets = mutableMapOf<String, WebSocket>()

    fun connect(url: String, listener: WebSocketListener) {
        // Close existing connection if any
        sockets[url]?.close(1000, "Reconnecting")
        
        val request = Request.Builder().url(url).build()
        val webSocket = client.newWebSocket(request, listener)
        sockets[url] = webSocket
    }

    fun disconnect(url: String) {
        sockets[url]?.close(1000, "User requested disconnect")
        sockets.remove(url)
    }

    fun disconnectAll() {
        sockets.forEach { (url, socket) ->
            socket.close(1000, "Disconnecting all")
        }
        sockets.clear()
    }

    fun publish(url: String, eventJson: String) {
        sockets[url]?.send(eventJson)
    }

    fun subscribe(url: String, subscriptionId: String, filtersJson: String) {
        val message = "[\"REQ\", \"$subscriptionId\", $filtersJson]"
        sockets[url]?.send(message)
    }
}
