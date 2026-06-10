package com.ely.kian.data.repository

import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.Nip44
import com.ely.kian.crypto.Nip59
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.ChatDao
import com.ely.kian.data.local.entities.Conversation
import com.ely.kian.data.local.entities.Message
import com.ely.kian.data.remote.RelayPoolManager
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val relayDao: com.ely.kian.data.local.dao.RelayDao,
    private val relayPool: RelayPoolManager,
    private val secureStorage: SecureStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val defaultRelays = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.nostr.band"
    )

    fun getConversations(): Flow<List<Conversation>> = chatDao.listConversations()

    fun getMessages(peerPubkey: String): Flow<List<Message>> = chatDao.getMessagesForConversation(peerPubkey)

    suspend fun sendMessage(peerPubkey: String, content: String) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val privKey = KianKeys.hexToBytes(privKeyHex)
        val pubKeyHex = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))
        
        val createdAt = System.currentTimeMillis() / 1000
        
        // 1. Build the Rumor (Unsigned Kind 14)
        val rumorTags = listOf(listOf("p", peerPubkey))
        val rumorJson = """{
            "pubkey": "$pubKeyHex",
            "created_at": $createdAt,
            "kind": 14,
            "tags": [["p", "$peerPubkey"]],
            "content": ${json.encodeToString(content)}
        }"""
        
        // 2. Wrap it for the recipient
        val recipientWrap = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = privKey,
            recipientPubKey = KianKeys.hexToBytes(peerPubkey),
            innerEventPubkey = pubKeyHex
        )

        // Save locally first (using a Kind 14 structure for our DB)
        val message = Message(
            id = recipientWrap.id, // Using the wrap ID or we could compute a rumor ID
            conversationPubkey = peerPubkey,
            sender = pubKeyHex,
            content = content,
            messageType = "text",
            createdAt = createdAt,
            status = "sent",
            rawJson = json.encodeToString(NostrEvent.serializer(), recipientWrap)
        )
        
        chatDao.insertConversationIgnore(Conversation(peerPubkey, content, createdAt))
        chatDao.insertMessage(message)
        chatDao.updateLastMessage(peerPubkey, content, createdAt)

        // 3. Publish to recipient's relays
        publishEventToRecipient(recipientWrap, peerPubkey)
        
        // 4. Self-Copy for multi-device sync
        if (peerPubkey != pubKeyHex) {
            val selfWrap = Nip59.giftWrap(
                innerEventJson = rumorJson,
                senderPrivKey = privKey,
                recipientPubKey = KianKeys.getPubKey(privKey),
                innerEventPubkey = pubKeyHex
            )
            publishEventToRecipient(selfWrap, pubKeyHex)
        }
    }

    private suspend fun publishEventToRecipient(event: NostrEvent, recipientPubkey: String) {
        val inboxRelays = try {
            relayDao.getDmInboxRelayUrls(recipientPubkey)
        } catch (e: Exception) {
            emptyList<String>()
        }
        
        val targetRelays = if (inboxRelays.isNotEmpty()) inboxRelays else defaultRelays
        val eventJson = json.encodeToString(NostrEvent.serializer(), event)
        val relayMessage = "[\"EVENT\", $eventJson]"
        
        targetRelays.forEach { url ->
            relayPool.publish(url, relayMessage)
        }
    }

    suspend fun handleIncomingEvent(event: NostrEvent) {
        when (event.kind) {
            4, 14, 1050 -> handleMessageEvent(event)
            1059 -> handleGiftWrap(event)
            20001 -> handleReceipt(event, "delivered")
            20002 -> handleReceipt(event, "read")
            15001 -> handleConversationDelete(event)
        }
    }

    private suspend fun handleGiftWrap(wrap: NostrEvent) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val privKey = KianKeys.hexToBytes(privKeyHex)
        
        try {
            val rumor = Nip59.unwrap(wrap, privKey)
            if (rumor != null) {
                handleMessageEvent(rumor)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to unwrap gift", e)
        }
    }

    private suspend fun handleConversationDelete(event: NostrEvent) {
        val myPubKey = secureStorage.getSecret(SecureStorage.PRIVATE_KEY)?.let { 
            KianKeys.bytesToHex(KianKeys.getPubKey(KianKeys.hexToBytes(it))) 
        } ?: return
        
        if (event.tags.any { it.size >= 2 && it[0] == "p" && it[1] == myPubKey }) {
            chatDao.deleteMessagesForConversation(event.pubkey)
            chatDao.markConversationDeleted(event.pubkey, event.createdAt)
        }
    }

    suspend fun deleteConversationLocally(peerPubkey: String) {
        chatDao.deleteMessagesForConversation(peerPubkey)
        chatDao.deleteConversation(peerPubkey)
    }

    suspend fun deleteConversationEverywhere(peerPubkey: String) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val privKey = KianKeys.hexToBytes(privKeyHex)
        val pubKeyHex = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))
        
        val createdAt = System.currentTimeMillis() / 1000
        val content = "{\"scope\": \"conversation\"}"
        val tags = listOf(listOf("p", peerPubkey))
        
        val eventId = KianKeys.computeEventId(pubKeyHex, createdAt, 15001, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(eventId), privKey))
        
        val event = NostrEvent(
            id = eventId,
            pubkey = pubKeyHex,
            createdAt = createdAt,
            kind = 15001,
            tags = tags,
            content = content,
            sig = sig
        )
        
        publishEventToRecipient(event, peerPubkey)
        deleteConversationLocally(peerPubkey)
    }

    private suspend fun handleMessageEvent(event: NostrEvent) {
        val myPubKey = secureStorage.getSecret(SecureStorage.PRIVATE_KEY)?.let { 
            KianKeys.bytesToHex(KianKeys.getPubKey(KianKeys.hexToBytes(it))) 
        } ?: return

        val pTags = event.tags.filter { it.size >= 2 && it[0] == "p" }.map { it[1] }
        val originalPTag = event.tags.find { it.size >= 2 && it[0] == "original_p" }?.get(1)
        
        val peerPubkey = if (event.pubkey == myPubKey) {
            // This is a message I authored. 
            // If it's a self-copy (sent to me), look for the original recipient
            if (pTags.contains(myPubKey)) {
                originalPTag ?: pTags.find { it != myPubKey } ?: return
            } else {
                pTags.firstOrNull() ?: return
            }
        } else if (pTags.contains(myPubKey)) {
            // Sent by someone else to me
            event.pubkey
        } else {
            return
        }

        val messageType = when (event.kind) {
            1050 -> "token_transfer"
            else -> "text"
        }

        val message = Message(
            id = event.id,
            conversationPubkey = peerPubkey,
            sender = event.pubkey,
            content = event.content,
            messageType = messageType,
            createdAt = event.createdAt,
            status = if (event.pubkey == myPubKey) "sent" else "received",
            rawJson = json.encodeToString(NostrEvent.serializer(), event)
        )

        // Check if message already exists to avoid duplicates
        if (chatDao.getMessageById(event.id) != null) return
        
        // If it's a self-copy, check if we already have the original message
        val originalId = event.tags.find { it.size >= 2 && it[0] == "e" }?.get(1)
        if (originalId != null && chatDao.getMessageById(originalId) != null) return

        chatDao.insertConversationIgnore(Conversation(peerPubkey, event.content, event.createdAt))
        chatDao.insertMessage(message)
        chatDao.updateLastMessage(peerPubkey, event.content, event.createdAt)
        
        if (event.pubkey != myPubKey) {
            chatDao.incrementUnread(peerPubkey)
            // Send delivery receipt
            sendReceipt(peerPubkey, event.id, 20001)
        }
    }

    private suspend fun handleReceipt(event: NostrEvent, status: String) {
        val eTag = event.tags.find { it.size >= 2 && it[0] == "e" }?.get(1) ?: return
        val currentMessage = chatDao.getMessageById(eTag) ?: return
        
        // Only upgrade status (don't downgrade from read to delivered)
        if (status == "read" || currentMessage.status != "read") {
            chatDao.updateMessageStatus(eTag, status, currentMessage.rawJson)
        }
    }

    suspend fun sendReceipt(peerPubkey: String, messageId: String, kind: Int) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val privKey = KianKeys.hexToBytes(privKeyHex)
        val pubKeyHex = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))
        
        val createdAt = System.currentTimeMillis() / 1000
        
        // Build receipt rumor (unsigned)
        val rumorTags = listOf(
            listOf("p", peerPubkey),
            listOf("e", messageId)
        )
        val rumorJson = """{
            "pubkey": "$pubKeyHex",
            "created_at": $createdAt,
            "kind": $kind,
            "tags": [["p", "$peerPubkey"], ["e", "$messageId"]],
            "content": "$messageId"
        }"""
        
        // Wrap for recipient
        val wrappedReceipt = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = privKey,
            recipientPubKey = KianKeys.hexToBytes(peerPubkey),
            innerEventPubkey = pubKeyHex
        )
        
        publishEventToRecipient(wrappedReceipt, peerPubkey)
    }

    suspend fun markAsRead(peerPubkey: String) {
        chatDao.resetUnread(peerPubkey)
        
        // Mark all messages from this peer as read and send receipts
        // For simplicity, we fetch them from the flow or directly from DAO
        // In a real app, you'd do this more efficiently.
    }
    
    suspend fun markMessageRead(peerPubkey: String, messageId: String) {
        val currentMessage = chatDao.getMessageById(messageId) ?: return
        if (currentMessage.status != "read" && currentMessage.sender == peerPubkey) {
            chatDao.updateMessageStatus(messageId, "read", currentMessage.rawJson)
            sendReceipt(peerPubkey, messageId, 20002)
        }
    }
}
