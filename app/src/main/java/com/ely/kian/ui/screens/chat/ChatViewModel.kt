package com.ely.kian.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.data.local.entities.Conversation
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.data.repository.ChatRepository
import com.ely.kian.data.repository.VoucherRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class ChatViewModel(
    private val repository: ChatRepository,
    private val userProfileDao: UserProfileDao,
    val voucherRepository: VoucherRepository
) : ViewModel() {

    fun getUtxos() = voucherRepository.getUtxos()

    val conversations: StateFlow<List<Conversation>> = repository.getConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val messagesCache = mutableMapOf<String, StateFlow<List<ChatMessage>>>()

    fun getMessages(contactPubkey: String): StateFlow<List<ChatMessage>> {
        return messagesCache.getOrPut(contactPubkey) {
            repository.getMessages(contactPubkey)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    suspend fun getMessageById(id: String) = repository.getMessageById(id)

    fun sendToken(contactPubkey: String, utxoId: String, amount: Long) {
        viewModelScope.launch {
            try {
                val allUtxos = voucherRepository.getUtxos().first()
                val utxo = allUtxos.find { it.utxoId == utxoId } ?: return@launch
                
                val balances = voucherRepository.getBalances().first()
                val balance = balances.find { it.assetRef == utxo.assetRef }
                val assetName = balance?.name ?: "Voucher"
                
                voucherRepository.sendTokenTransfer(utxoId, amount, contactPubkey)
                
                val isToProducer = contactPubkey == utxo.producer
                
                val metadata = buildJsonObject {
                    put("type", if (isToProducer) "token_redemption" else "token_transfer")
                    put("utxo_id", utxoId) 
                    put("asset_name", assetName)
                    put("amount", amount)
                    put("producer", utxo.producer)
                }.toString()

                val summary = if (isToProducer) "🛒 $amount x $assetName" else "💸 $amount x $assetName"
                repository.sendMessage(contactPubkey, summary, metadata)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to send", e)
            }
        }
    }

    fun confirmProductReceipt(messageId: String, contactPubkey: String, transferEventId: String) {
        viewModelScope.launch {
            try {
                voucherRepository.confirmReceipt(transferEventId, contactPubkey)
                repository.updateMessageStatus(messageId, "received")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to confirm receipt", e)
            }
        }
    }

    fun getBalances(): StateFlow<List<BalanceItem>> {
        return voucherRepository.getBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun rejectPurchaseRequest(messageId: String, contactPubkey: String, localizedContent: String) {
        viewModelScope.launch {
            try {
                // 1. Update original message status locally
                repository.updateMessageStatus(messageId, "rejected")
                
                // 2. Send a rejection message with metadata
                val metadata = buildJsonObject {
                    put("type", "purchase_rejection")
                    put("target_id", messageId)
                }.toString()
                
                repository.sendMessage(contactPubkey, localizedContent, metadata)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to reject", e)
            }
        }
    }


    fun sendMessage(contactPubkey: String, content: String, replyToId: String? = null) {
        viewModelScope.launch {
            repository.sendMessage(contactPubkey, content, replyToId = replyToId)
            repository.retryPendingMessages()
        }
    }

    fun retryPending() {
        viewModelScope.launch {
            repository.retryPendingMessages()
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

    fun toggleReaction(messageId: String, contactPubkey: String, emoji: String) {
        viewModelScope.launch {
            repository.sendReaction(messageId, contactPubkey, emoji)
        }
    }

    suspend fun getProfile(pubkey: String): Profile? {
        return userProfileDao.getProfile(pubkey)
    }

    companion object {
        fun provideFactory(
            repository: ChatRepository,
            userProfileDao: UserProfileDao,
            voucherRepository: VoucherRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(repository, userProfileDao, voucherRepository) as T
            }
        }
    }
}
