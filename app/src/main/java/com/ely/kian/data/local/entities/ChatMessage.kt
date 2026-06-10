package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val peerPubkey: String,
    val lastMessage: String?,
    val lastMessageAt: Long?,
    val unreadCount: Int = 0
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["peerPubkey"],
            childColumns = ["conversationPubkey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversationPubkey", "createdAt"])
    ]
)
data class Message(
    @PrimaryKey val id: String,
    val conversationPubkey: String,
    val sender: String,
    val content: String,
    val messageType: String = "text",
    val createdAt: Long,
    val status: String = "sending",
    val rawJson: String?
)

@Entity(tableName = "message_receipts", primaryKeys = ["messageId", "receiptType"])
data class MessageReceipt(
    val messageId: String,
    val receiptType: String,
    val createdAt: Long
)
