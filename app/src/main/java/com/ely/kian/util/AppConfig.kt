package com.ely.kian.util

import com.ely.kian.BuildConfig

object AppConfig {
    private val RELEASE_RELAYS = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.snort.social",
        "wss://nostr.mom",
        "wss://relay.primal.net",
        "wss://relay.snort.social"
    )

    private val DEBUG_RELAYS = listOf(
        "ws://192.168.1.14:8080", // Local test relay
        "wss://relay.damus.io"    // One stable public relay for testing
    )

    val defaultRelays: List<String>
        get() = if (BuildConfig.DEBUG) DEBUG_RELAYS else RELEASE_RELAYS
}
