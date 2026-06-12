package com.ely.kian.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.data.local.entities.Conversation
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.repository.ChatRepository
import com.ely.kian.data.repository.ProductRepository
import com.ely.kian.data.repository.TokenRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val userProfileDao: UserProfileDao,
    private val productRepository: ProductRepository,
    private val tokenRepository: TokenRepository
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

    fun getMyProducts(): Flow<List<Product>> {
        return flow {
            val key = repository.getOwnPubkey()
            if (key != null) {
                emitAll(productRepository.getProducts(key))
            } else {
                emit(emptyList())
            }
        }
    }

    fun sendProductAsToken(contactPubkey: String, productId: String, quantity: Long) {
        viewModelScope.launch {
            try {
                tokenRepository.mintProduct(contactPubkey, productId, quantity)
                
                // Fetch the product name to include in the notification message
                val ownPubkey = repository.getOwnPubkey() ?: return@launch
                val products = productRepository.getProducts(ownPubkey).first()
                val product = products.find { it.id == productId }
                val productName = product?.name ?: "a product"

                repository.sendMessage(contactPubkey, "🎁 Sent you $quantity tokens for: $productName")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to mint product", e)
            }
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
            userProfileDao: UserProfileDao,
            productRepository: ProductRepository,
            tokenRepository: TokenRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(repository, userProfileDao, productRepository, tokenRepository) as T
            }
        }
    }
}
