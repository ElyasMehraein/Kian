package com.ely.kian.data.local.dao

import androidx.room.*
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.data.local.entities.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE contactPubkey = :contactPubkey ORDER BY createdAt ASC")
    fun getMessagesForContact(contactPubkey: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM conversations ORDER BY lastTimestamp DESC")
    fun getConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE contactPubkey = :contactPubkey")
    suspend fun getConversation(contactPubkey: String): Conversation?

    @Query("UPDATE conversations SET lastMessage = :lastMessage, lastTimestamp = :lastTimestamp, unreadCount = unreadCount + :unreadIncrement WHERE contactPubkey = :contactPubkey")
    suspend fun updateConversationLastMessage(contactPubkey: String, lastMessage: String, lastTimestamp: Long, unreadIncrement: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversationInitial(conversation: Conversation)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE contactPubkey = :contactPubkey")
    suspend fun markAsRead(contactPubkey: String)

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getMessageById(id: String): ChatMessage?

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("UPDATE chat_messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("UPDATE chat_messages SET reactions = :reactions WHERE id = :id")
    suspend fun updateMessageReactions(id: String, reactions: String?)

    @Query("SELECT * FROM chat_messages WHERE contactPubkey = :contactPubkey AND isMine = 0 AND status != 'read'")
    suspend fun getUnreadMessagesFrom(contactPubkey: String): List<ChatMessage>

    @Query("DELETE FROM chat_messages WHERE contactPubkey = :contactPubkey")
    suspend fun deleteMessagesForContact(contactPubkey: String)

    @Query("DELETE FROM conversations WHERE contactPubkey = :contactPubkey")
    suspend fun deleteConversation(contactPubkey: String)

    @Query("SELECT id FROM chat_messages WHERE contactPubkey = :contactPubkey AND pubkey = :myPubkey")
    suspend fun getOwnMessageIdsForContact(contactPubkey: String, myPubkey: String): List<String>

    @Query("UPDATE chat_messages SET status = :status WHERE metadata LIKE '%' || :metadataPart || '%'")
    suspend fun updateMessageStatusByMetadata(metadataPart: String, status: String)

    @Query("SELECT * FROM chat_messages WHERE metadata LIKE '%' || :metadataPart || '%' LIMIT 1")
    suspend fun getMessageByMetadata(metadataPart: String): ChatMessage?

    @Query("SELECT * FROM chat_messages WHERE status = 'pending' ORDER BY createdAt ASC")
    suspend fun getPendingMessages(): List<ChatMessage>

    @Query("SELECT SUM(unreadCount) FROM conversations")
    fun getTotalUnreadCount(): Flow<Int?>
}
