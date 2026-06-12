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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: Conversation)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE contactPubkey = :contactPubkey")
    suspend fun markAsRead(contactPubkey: String)

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getMessageById(id: String): ChatMessage?

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("UPDATE chat_messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("SELECT * FROM chat_messages WHERE contactPubkey = :contactPubkey AND isMine = 0 AND status != 'read'")
    suspend fun getUnreadMessagesFrom(contactPubkey: String): List<ChatMessage>
}
