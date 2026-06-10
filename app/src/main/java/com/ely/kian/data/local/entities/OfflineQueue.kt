package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_queue")
data class OfflineQueue(
    @PrimaryKey val eventId: String,
    val cborPayload: ByteArray,
    val relayUrls: String, // JSON array
    val queueScope: String = "generic",
    val peerPubkey: String?,
    val createdAt: Long,
    val retryCount: Int = 0
)
