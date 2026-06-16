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
import com.ely.kian.util.NotificationHelper
import com.ely.kian.data.local.dao.UserProfileDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class ChatRepository(
    private val chatDao: ChatDao,
    private val relayDao: RelayDao,
    private val offlineQueueDao: OfflineQueueDao,
    private val userProfileDao: UserProfileDao,
    private val secureStorage: SecureStorage,
    private val nostrSyncManager: NostrSyncManager,
    private val notificationHelper: NotificationHelper,
    private val isAppInForeground: () -> Boolean,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val TAG = "ChatRepository"
    private val repoMutex = Mutex()

    fun getMessages(contactPubkey: String): Flow<List<ChatMessage>> = 
        chatDao.getMessagesForContact(contactPubkey)

    fun getConversations(): Flow<List<Conversation>> = 
        chatDao.getConversations()

    suspend fun getMessageById(id: String): ChatMessage? = chatDao.getMessageById(id)

    suspend fun getOwnPubkey(): String? {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return null
        return KianKeys.bytesToHex(KianKeys.getPubKey(KianKeys.hexToBytes(privKeyHex)))
    }

    suspend fun sendMessage(contactPubkey: String, content: String, metadata: String? = null, replyToId: String? = null) {
        val myPrivKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val myPrivKey = KianKeys.hexToBytes(myPrivKeyHex)
        val myPubKey = KianKeys.bytesToHex(KianKeys.getPubKey(myPrivKey))
        
        val createdAt = System.currentTimeMillis() / 1000
        val kind = 14 // Chat Message Rumor
        
        // 1. Create the Rumor
        val tags = mutableListOf(listOf("p", contactPubkey))
        if (metadata != null) {
            tags.add(listOf("metadata", metadata))
        }
        if (replyToId != null) {
            tags.add(listOf("e", replyToId, "", "reply"))
        }
        
        val id = KianKeys.computeEventId(myPubKey, createdAt, kind, tags, content)
        
        val rumor = NostrEvent(
            id = id,
            pubkey = myPubKey,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            sig = "" 
        )

        val rumorJson = json.encodeToString(rumor)

        // 2. Wrap for RECIPIENT ... (rest same)
        val giftWrapToRecipient = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = myPrivKey,
            recipientPubKey = KianKeys.hexToBytes(contactPubkey),
            innerEventPubkey = myPubKey
        )
        
        // 3. Wrap for SENDER
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
            status = "pending",
            metadata = metadata,
            replyTo = replyToId
        )
        chatDao.insertMessage(message)
        updateConversation(contactPubkey, content, createdAt)

        // ... publish logic
        val recipientInbox = relayDao.getDmInboxRelayUrls(contactPubkey)
        val myOutbox = relayDao.getDmInboxRelayUrls(myPubKey)
        val targetRelays = (recipientInbox + myOutbox).distinct()
        nostrSyncManager.publishEvent(giftWrapToRecipient, targetRelays)
        nostrSyncManager.publishEvent(giftWrapToSelf, myOutbox)
    }

    suspend fun sendReaction(messageId: String, contactPubkey: String, emoji: String) {
        val myPrivKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val myPrivKey = KianKeys.hexToBytes(myPrivKeyHex)
        val myPubKey = KianKeys.bytesToHex(KianKeys.getPubKey(myPrivKey))
        
        val createdAt = System.currentTimeMillis() / 1000
        val kind = 7 // Reaction
        
        val tags = listOf(
            listOf("e", messageId),
            listOf("p", contactPubkey)
        )
        
        val id = KianKeys.computeEventId(myPubKey, createdAt, kind, tags, emoji)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), myPrivKey))
        
        val reactionEvent = NostrEvent(id, myPubKey, createdAt, kind, tags, emoji, sig)
        
        // 1. Update locally first
        updateLocalReactions(messageId, myPubKey, emoji)
        
        // 2. Publish to relays (gift wrapped)
        val giftWrap = Nip59.giftWrap(json.encodeToString(reactionEvent), myPrivKey, KianKeys.hexToBytes(contactPubkey), myPubKey)
        nostrSyncManager.publishEvent(giftWrap)
    }

    private suspend fun updateLocalReactions(messageId: String, senderPubkey: String, emoji: String) {
        val message = chatDao.getMessageById(messageId) ?: return
        val existingReactionsJson = message.reactions
        
        val reactionsMap = if (existingReactionsJson != null) {
            try {
                json.decodeFromString<Map<String, List<String>>>(existingReactionsJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }
        
        val pubkeys = reactionsMap[emoji]?.toMutableList() ?: mutableListOf()
        if (pubkeys.contains(senderPubkey)) {
            pubkeys.remove(senderPubkey)
        } else {
            pubkeys.add(senderPubkey)
        }
        
        if (pubkeys.isEmpty()) {
            reactionsMap.remove(emoji)
        } else {
            reactionsMap[emoji] = pubkeys
        }
        
        val newJson = if (reactionsMap.isEmpty()) null else json.encodeToString(reactionsMap)
        chatDao.updateMessageReactions(messageId, newJson)
    }

    suspend fun handleReaction(event: NostrEvent) {
        val targetMessageId = event.tags.find { it.size >= 2 && it[0] == "e" }?.get(1) ?: return
        updateLocalReactions(targetMessageId, event.pubkey, event.content)
    }

    suspend fun handleChatMessage(event: NostrEvent) {
        repoMutex.withLock {
            val existing = chatDao.getMessageById(event.id)
            if (existing != null) {
                if (existing.status == "pending") {
                    chatDao.updateMessageStatus(event.id, "sent")
                }
                return@withLock
            }

            val myPrivKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return@withLock
            val myPubKey = KianKeys.bytesToHex(KianKeys.getPubKey(KianKeys.hexToBytes(myPrivKeyHex)))
            
            val isMine = event.pubkey == myPubKey
            val contactPubkey = if (isMine) {
                event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1) ?: return@withLock
            } else {
                event.pubkey
            }

            val metadata = event.tags.find { it.size >= 2 && it[0] == "metadata" }?.get(1)
            val replyToId = event.tags.find { it.size >= 2 && it[0] == "e" }?.get(1)

            val message = ChatMessage(
                id = event.id,
                pubkey = event.pubkey,
                contactPubkey = contactPubkey,
                createdAt = event.createdAt,
                content = event.content,
                kind = event.kind,
                isMine = isMine,
                metadata = metadata,
                replyTo = replyToId
            )
            
            chatDao.insertMessage(message)
            updateConversation(contactPubkey, event.content, event.createdAt, !isMine)

            if (!isMine) {
                sendReceipt(contactPubkey, listOf(event.id), 20001) // Delivered
                
                // Show notification only if app is in background
                if (!isAppInForeground()) {
                    val profile = userProfileDao.getProfile(contactPubkey)
                    notificationHelper.showChatNotification(
                        senderPubkey = contactPubkey,
                        senderName = profile?.displayName ?: profile?.name,
                        message = event.content
                    )
                }
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

    suspend fun updateMessageStatus(messageId: String, status: String) {
        chatDao.updateMessageStatus(messageId, status)
    }

    suspend fun updateMessageStatusByMetadata(metadataPart: String, status: String) {
        chatDao.updateMessageStatusByMetadata(metadataPart, status)
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
