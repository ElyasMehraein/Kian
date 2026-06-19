package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["contactPubkey"])]
)
data class ChatMessage(
    @PrimaryKey val id: String,
    val pubkey: String, // Sender
    val contactPubkey: String, // The other person in the DM
    val createdAt: Long,
    val content: String,
    val kind: Int,
    val isMine: Boolean,
    val status: String = "sent", // sent, delivered, read, pending, waiting_auth, semi_burnt, received
    val metadata: String? = null, // For tokens, products, etc.
    val replyTo: String? = null, // ID of the message being replied to
    val reactions: String? = null // JSON string of emoji to list of pubkeys mapping
)

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val contactPubkey: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0,
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0
)

@Entity(tableName = "deleted_events")
data class DeletedEvent(
    @PrimaryKey val id: String,
    val deletedAt: Long
)
