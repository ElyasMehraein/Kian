package com.ely.kian.data.repository

import android.util.Log
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.Nip59
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.ChatDao
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.data.local.dao.RelayDao
import com.ely.kian.data.local.entities.Conversation
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.model.NostrEvent
import com.ely.kian.data.local.dao.OfflineQueueDao
import com.ely.kian.data.local.entities.OfflineQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class ChatRepository(
    private val chatDao: ChatDao,
    private val relayDao: RelayDao,
    private val offlineQueueDao: OfflineQueueDao,
    private val secureStorage: SecureStorage,
    private val nostrSyncManager: NostrSyncManager,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val TAG = "ChatRepository"
    private val repoMutex = Mutex()

    fun getMessages(contactPubkey: String): Flow<List<ChatMessage>> = 
        chatDao.getMessagesForContact(contactPubkey)

    fun getConversations(): Flow<List<Conversation>> = 
        chatDao.getConversations()

    suspend fun sendMessage(contactPubkey: String, content: String) {
        val myPrivKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val myPrivKey = KianKeys.hexToBytes(myPrivKeyHex)
        val myPubKey = KianKeys.bytesToHex(KianKeys.getPubKey(myPrivKey))
        
        val createdAt = System.currentTimeMillis() / 1000
        val kind = 14 // Chat Message Rumor
        
        // 1. Create the Rumor
        val tags = listOf(listOf("p", contactPubkey))
        val id = KianKeys.computeEventId(myPubKey, createdAt, kind, tags, content)
        
        val rumor = NostrEvent(
            id = id,
            pubkey = myPubKey,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            sig = "" // Rumors are unsigned or signed in Seal
        )

        val rumorJson = json.encodeToString(rumor)

        // 2. Wrap for RECIPIENT
        val giftWrapToRecipient = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = myPrivKey,
            recipientPubKey = KianKeys.hexToBytes(contactPubkey),
            innerEventPubkey = myPubKey
        )
        
        // 3. Wrap for SENDER (Self-Sync like Amethyst)
        val giftWrapToSelf = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = myPrivKey,
            recipientPubKey = KianKeys.hexToBytes(myPubKey),
            innerEventPubkey = myPubKey
        )
        
        // 4. Save locally
        val message = ChatMessage(
            id = id,
            pubkey = myPubKey,
            contactPubkey = contactPubkey,
            createdAt = createdAt,
            content = content,
            kind = kind,
            isMine = true,
            status = "pending" // Show as pending until confirmed by a relay
        )
        chatDao.insertMessage(message)
        updateConversation(contactPubkey, content, createdAt)

        // 5. Publish to Recipient's Inbox + My Outbox
        val recipientInbox = relayDao.getDmInboxRelayUrls(contactPubkey)
        val myOutbox = relayDao.getDmInboxRelayUrls(myPubKey) // Kind 10050/10002
        
        val targetRelays = (recipientInbox + myOutbox).distinct()
        
        nostrSyncManager.publishEvent(giftWrapToRecipient, targetRelays)
        nostrSyncManager.publishEvent(giftWrapToSelf, myOutbox)
    }

    suspend fun handleChatMessage(event: NostrEvent) {
        repoMutex.withLock {
            // Check if message already exists
            val existing = chatDao.getMessageById(event.id)
            if (existing != null) {
                // If it was pending, mark as sent now that we see it on a relay
                if (existing.status == "pending") {
                    chatDao.updateMessageStatus(event.id, "sent")
                }
                return@withLock
            }

            val myPrivKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return@withLock
            val myPubKey = KianKeys.bytesToHex(KianKeys.getPubKey(KianKeys.hexToBytes(myPrivKeyHex)))
            
            // The contact is the person I'm talking to. 
            // If I sent it, the contact is the 'p' tag. 
            // If I received it, the contact is the sender (event.pubkey).
            
            val isMine = event.pubkey == myPubKey
            val contactPubkey = if (isMine) {
                event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1) ?: return@withLock
            } else {
                event.pubkey
            }

            val message = ChatMessage(
                id = event.id,
                pubkey = event.pubkey,
                contactPubkey = contactPubkey,
                createdAt = event.createdAt,
                content = event.content,
                kind = event.kind,
                isMine = isMine
            )
            
            chatDao.insertMessage(message)
            updateConversation(contactPubkey, event.content, event.createdAt, !isMine)

            if (!isMine) {
                sendReceipt(contactPubkey, listOf(event.id), 20001) // Delivered
            }
        }
    }

    private suspend fun updateConversation(contactPubkey: String, lastMessage: String, timestamp: Long, incrementUnread: Boolean = false) {
        val existing = chatDao.getConversation(contactPubkey)
        if (existing == null) {
            val conversation = Conversation(
                contactPubkey = contactPubkey,
                lastMessage = lastMessage,
                lastTimestamp = timestamp,
                unreadCount = if (incrementUnread) 1 else 0
            )
            chatDao.insertConversationInitial(conversation)
        } else {
            chatDao.updateConversationLastMessage(
                contactPubkey = contactPubkey,
                lastMessage = lastMessage,
                lastTimestamp = timestamp,
                unreadIncrement = if (incrementUnread) 1 else 0
            )
        }
    }

    suspend fun markAsRead(contactPubkey: String) {
        chatDao.markAsRead(contactPubkey)
        
        // Send Read Receipts
        val unread = chatDao.getUnreadMessagesFrom(contactPubkey)
        if (unread.isNotEmpty()) {
            sendReceipt(contactPubkey, unread.map { it.id }, 20002)
        }
    }

    private suspend fun sendReceipt(contactPubkey: String, eventIds: List<String>, kind: Int) {
        val myPrivKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val myPrivKey = KianKeys.hexToBytes(myPrivKeyHex)
        val myPubKey = KianKeys.bytesToHex(KianKeys.getPubKey(myPrivKey))
        
        val createdAt = System.currentTimeMillis() / 1000
        val tags = eventIds.map { listOf("e", it) } + listOf(listOf("p", contactPubkey))
        val content = ""
        
        val id = KianKeys.computeEventId(myPubKey, createdAt, kind, tags, content)
        val rumor = NostrEvent(id, myPubKey, createdAt, kind, tags, content, "")
        
        val giftWrap = Nip59.giftWrap(json.encodeToString(rumor), myPrivKey, KianKeys.hexToBytes(contactPubkey), myPubKey)
        nostrSyncManager.publishEvent(giftWrap)
        
        // Update local status if it was about our own messages (not applicable here, but for consistency)
    }

    suspend fun handleReceipt(event: NostrEvent) {
        val status = if (event.kind == 20001) "delivered" else "read"
        event.tags.filter { it.size >= 2 && it[0] == "e" }.forEach { tag ->
            chatDao.updateMessageStatus(tag[1], status)
        }
    }

    suspend fun deleteMessage(messageId: String) {
        val message = chatDao.getMessageById(messageId) ?: return
        val myPrivKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val myPrivKey = KianKeys.hexToBytes(myPrivKeyHex)
        val myPubKey = KianKeys.bytesToHex(KianKeys.getPubKey(myPrivKey))

        if (message.pubkey != myPubKey) {
            // Can only delete own messages on relays, but can remove locally
            chatDao.deleteMessageById(messageId)
            return
        }

        // 1. Create Kind 5 Deletion Event
        val createdAt = System.currentTimeMillis() / 1000
        val tags = listOf(listOf("e", messageId))
        val content = "Deleting message"
        val id = KianKeys.computeEventId(myPubKey, createdAt, 5, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), myPrivKey))

        val deletionEvent = NostrEvent(id, myPubKey, createdAt, 5, tags, content, sig)

        // 2. Publish
        nostrSyncManager.publishEvent(deletionEvent)

        // 3. Delete locally
        chatDao.deleteMessageById(messageId)
    }

    suspend fun deleteConversationFull(contactPubkey: String) {
        val myPrivKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val myPrivKey = KianKeys.hexToBytes(myPrivKeyHex)
        val myPubKey = KianKeys.bytesToHex(KianKeys.getPubKey(myPrivKey))

        // 1. Get all own message IDs for this contact
        val targetIds = chatDao.getOwnMessageIdsForContact(contactPubkey, myPubKey)
        
        if (targetIds.isNotEmpty()) {
            // 2. Create a single Kind 5 Deletion Event for ALL messages
            val createdAt = System.currentTimeMillis() / 1000
            val tags = targetIds.map { listOf("e", it) }
            val content = "Deleting conversation"
            val id = KianKeys.computeEventId(myPubKey, createdAt, 5, tags, content)
            val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), myPrivKey))

            val deletionEvent = NostrEvent(id, myPubKey, createdAt, 5, tags, content, sig)

            // 3. Publish
            nostrSyncManager.publishEvent(deletionEvent)
        }

        // 4. Delete everything locally
        chatDao.deleteMessagesForContact(contactPubkey)
        chatDao.deleteConversation(contactPubkey)
    }

    suspend fun handleDeletion(event: NostrEvent) {
        val targetIds = event.tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
        targetIds.forEach { id ->
            val message = chatDao.getMessageById(id)
            if (message != null && message.pubkey == event.pubkey) {
                chatDao.deleteMessageById(id)
            }
        }
    }
}
