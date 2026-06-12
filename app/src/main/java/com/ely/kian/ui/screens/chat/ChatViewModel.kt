package com.ely.kian.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.data.local.entities.Conversation
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val userProfileDao: UserProfileDao
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = repository.getConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val messagesCache = mutableMapOf<String, StateFlow<List<ChatMessage>>>()

    fun getMessages(contactPubkey: String): StateFlow<List<ChatMessage>> {
        return messagesCache.getOrPut(contactPubkey) {
            repository.getMessages(contactPubkey)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    fun sendMessage(contactPubkey: String, content: String) {
        viewModelScope.launch {
            repository.sendMessage(contactPubkey, content)
        }
    }

    fun markAsRead(contactPubkey: String) {
        viewModelScope.launch {
            repository.markAsRead(contactPubkey)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun deleteConversation(contactPubkey: String) {
        viewModelScope.launch {
            repository.deleteConversationFull(contactPubkey)
        }
    }

    suspend fun getProfile(pubkey: String): Profile? {
        return userProfileDao.getProfile(pubkey)
    }

    companion object {
        fun provideFactory(
            repository: ChatRepository,
            userProfileDao: UserProfileDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(repository, userProfileDao) as T
            }
        }
    }
}
