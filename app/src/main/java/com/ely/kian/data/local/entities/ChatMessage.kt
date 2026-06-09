package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String,
    val senderPubkey: String,
    val receiverPubkey: String,
    val content: String,
    val timestamp: Long,
    val status: String, // e.g., PENDING, SENT, DELIVERED, READ
    val isGiftWrap: Boolean = true
)
