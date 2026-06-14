package com.ely.kian.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.data.local.entities.Conversation
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.data.repository.ChatRepository
import com.ely.kian.data.repository.ProductRepository
import com.ely.kian.data.repository.TokenRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class ChatViewModel(
    private val repository: ChatRepository,
    private val userProfileDao: UserProfileDao,
    private val productRepository: ProductRepository,
    val tokenRepository: TokenRepository
) : ViewModel() {

    fun getUtxos() = tokenRepository.getUtxos()

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
                val ownPubkey = repository.getOwnPubkey() ?: return@launch
                val products = productRepository.getProducts(ownPubkey).first()
                val product = products.find { it.id == productId } ?: return@launch
                val productName = product.name

                tokenRepository.mintProduct(contactPubkey, productId, quantity)

                val metadata = buildJsonObject {
                    put("type", "token_mint")
                    put("product_id", productId)
                    put("product_name", productName)
                    put("amount", quantity)
                }.toString()

                repository.sendMessage(contactPubkey, "📦 $quantity x $productName", metadata)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to mint product", e)
            }
        }
    }

    fun sendToken(contactPubkey: String, utxoId: String, amount: Long) {
        viewModelScope.launch {
            try {
                val allUtxos = tokenRepository.getUtxos().first()
                val utxo = allUtxos.find { it.utxoId == utxoId } ?: return@launch
                
                val balances = tokenRepository.getBalances().first()
                val balance = balances.find { it.assetRef == utxo.assetRef }
                val assetName = balance?.name ?: "Tokens"
                
                tokenRepository.sendTokenTransfer(utxoId, amount, contactPubkey)
                
                val isToProducer = contactPubkey == utxo.producer
                
                val metadata = buildJsonObject {
                    put("type", if (isToProducer) "token_redemption" else "token_transfer")
                    put("utxo_id", utxoId) // Keep original UTXO ID for confirmation tracking
                    put("asset_name", assetName)
                    put("amount", amount)
                    put("producer", utxo.producer)
                }.toString()

                val summary = if (isToProducer) "🛒 $amount x $assetName" else "💸 $amount x $assetName"
                repository.sendMessage(contactPubkey, summary, metadata)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to send token", e)
            }
        }
    }

    fun confirmProductReceipt(messageId: String, contactPubkey: String, transferEventId: String) {
        viewModelScope.launch {
            try {
                tokenRepository.confirmReceipt(transferEventId, contactPubkey)
                // Update local message status
                repository.updateMessageStatus(messageId, "received")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to confirm receipt", e)
            }
        }
    }

    fun getBalances(): StateFlow<List<BalanceItem>> {
        return tokenRepository.getBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
