package com.ely.kian.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NostrEvent(
    val id: String,
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
)

@Serializable
data class NostrFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    @SerialName("#e") val e: List<String>? = null,
    @SerialName("#p") val p: List<String>? = null,
    @SerialName("#d") val d: List<String>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null
)
