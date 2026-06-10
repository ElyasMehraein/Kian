package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.Conversation
import com.ely.kian.data.local.entities.Message
import com.ely.kian.data.local.entities.MessageReceipt
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // Conversations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversationIgnore(conversation: Conversation)

    @Query("SELECT * FROM conversations WHERE lastMessage IS NULL OR lastMessage != '__deleted_conversation__' ORDER BY CASE WHEN lastMessageAt IS NULL THEN 1 ELSE 0 END, lastMessageAt DESC, peerPubkey ASC")
    fun listConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE peerPubkey = :peerPubkey LIMIT 1")
    suspend fun getConversation(peerPubkey: String): Conversation?

    @Query("UPDATE conversations SET lastMessage = :lastMessage, lastMessageAt = :lastMessageAt WHERE peerPubkey = :peerPubkey")
    suspend fun updateLastMessage(peerPubkey: String, lastMessage: String, lastMessageAt: Long)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE peerPubkey = :peerPubkey")
    suspend fun incrementUnread(peerPubkey: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE peerPubkey = :peerPubkey")
    suspend fun resetUnread(peerPubkey: String)

    @Query("UPDATE conversations SET lastMessage = '__deleted_conversation__', lastMessageAt = :deletedAt, unreadCount = 0 WHERE peerPubkey = :peerPubkey")
    suspend fun markConversationDeleted(peerPubkey: String, deletedAt: Long)

    // Messages
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("SELECT * FROM messages WHERE conversationPubkey = :peerPubkey ORDER BY createdAt ASC")
    fun getMessagesForConversation(peerPubkey: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE messageType = :messageType ORDER BY createdAt ASC")
    fun getMessagesByType(messageType: String): Flow<List<Message>>

    @Query("DELETE FROM messages WHERE conversationPubkey = :peerPubkey AND createdAt <= :createdAt")
    suspend fun deleteMessagesThrough(peerPubkey: String, createdAt: Long)

    @Query("DELETE FROM messages WHERE conversationPubkey = :peerPubkey")
    suspend fun deleteMessagesForConversation(peerPubkey: String)

    @Query("UPDATE messages SET status = :status, rawJson = :rawJson WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String, rawJson: String?)

    @Query("UPDATE messages SET rawJson = :rawJson WHERE id = :id")
    suspend fun updateMessageRawJson(id: String, rawJson: String)

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): Message?

    // Receipts
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReceipt(receipt: MessageReceipt)

    @Query("SELECT * FROM message_receipts WHERE messageId = :messageId")
    suspend fun getReceiptsForMessage(messageId: String): List<MessageReceipt>
}
