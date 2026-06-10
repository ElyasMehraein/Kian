package com.ely.kian.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.Conversation
import com.ely.kian.data.local.entities.Message
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConversationUiModel(
    val peerPubkey: String,
    val peerName: String,
    val peerPicture: String?,
    val lastMessage: String?,
    val lastMessageAt: Long?,
    val unreadCount: Int
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userProfileDao: UserProfileDao
) : ViewModel() {

    val conversations: StateFlow<List<ConversationUiModel>> = combine(
        chatRepository.getConversations(),
        userProfileDao.listProfiles()
    ) { convs, profiles ->
        convs.map { conv ->
            val profile = profiles.find { it.pubkey == conv.peerPubkey }
            ConversationUiModel(
                peerPubkey = conv.peerPubkey,
                peerName = profile?.displayName ?: profile?.name ?: conv.peerPubkey.take(8),
                peerPicture = profile?.picture,
                lastMessage = conv.lastMessage,
                lastMessageAt = conv.lastMessageAt,
                unreadCount = conv.unreadCount
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getMessages(peerPubkey: String): Flow<List<Message>> {
        return chatRepository.getMessages(peerPubkey)
    }

    suspend fun getProfile(pubkey: String): Profile? {
        return userProfileDao.getProfile(pubkey)
    }

    fun sendMessage(peerPubkey: String, content: String) {
        viewModelScope.launch {
            chatRepository.sendMessage(peerPubkey, content)
        }
    }

    fun markAsRead(peerPubkey: String) {
        viewModelScope.launch {
            chatRepository.markAsRead(peerPubkey)
        }
    }

    fun markMessageAsRead(peerPubkey: String, messageId: String) {
        viewModelScope.launch {
            chatRepository.markMessageRead(peerPubkey, messageId)
        }
    }

    companion object {
        fun provideFactory(chatRepository: ChatRepository, userProfileDao: UserProfileDao): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(chatRepository, userProfileDao) as T
            }
        }
    }
}
