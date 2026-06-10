package com.ely.kian.data.repository

import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.ChatDao
import com.ely.kian.data.local.entities.Conversation
import com.ely.kian.data.local.entities.Message
import com.ely.kian.data.remote.RelayPoolManager
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
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
        val pubKey = KianKeys.getPubKey(privKey)
        val pubKeyHex = KianKeys.bytesToHex(pubKey)
        
        val createdAt = System.currentTimeMillis() / 1000
        
        // For now, we use Kind 4 but without real encryption (just for demo/functional skeleton)
        // In a real app, we would use NIP-04 or NIP-44 encryption here
        val tags = listOf(listOf("p", peerPubkey))
        
        val eventId = KianKeys.computeEventId(
            pubkey = pubKeyHex,
            createdAt = createdAt,
            kind = 4,
            tags = tags,
            content = content
        )
        
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(eventId), privKey))
        
        val event = NostrEvent(
            id = eventId,
            pubkey = pubKeyHex,
            createdAt = createdAt,
            kind = 4,
            tags = tags,
            content = content,
            sig = sig
        )

        // Save locally first
        val message = Message(
            id = event.id,
            conversationPubkey = peerPubkey,
            sender = pubKeyHex,
            content = content,
            createdAt = createdAt,
            status = "sent",
            rawJson = json.encodeToString(NostrEvent.serializer(), event)
        )
        
        chatDao.insertConversationIgnore(Conversation(peerPubkey, content, createdAt))
        chatDao.insertMessage(message)
        chatDao.updateLastMessage(peerPubkey, content, createdAt)

        // Publish to relays
        val eventJson = json.encodeToString(NostrEvent.serializer(), event)
        val relayMessage = "[\"EVENT\", $eventJson]"
        defaultRelays.forEach { url ->
            relayPool.publish(url, relayMessage)
        }
    }

    suspend fun handleIncomingEvent(event: NostrEvent) {
        if (event.kind != 4) return
        
        val myPubKey = secureStorage.getSecret(SecureStorage.PRIVATE_KEY)?.let { 
            KianKeys.bytesToHex(KianKeys.getPubKey(KianKeys.hexToBytes(it))) 
        } ?: return

        val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1)
        
        val peerPubkey = if (event.pubkey == myPubKey) {
            pTag ?: return // Sent by me to pTag
        } else if (pTag == myPubKey) {
            event.pubkey // Sent by event.pubkey to me
        } else {
            return // Not for me
        }

        val message = Message(
            id = event.id,
            conversationPubkey = peerPubkey,
            sender = event.pubkey,
            content = event.content,
            createdAt = event.createdAt,
            status = if (event.pubkey == myPubKey) "sent" else "received",
            rawJson = json.encodeToString(NostrEvent.serializer(), event)
        )

        chatDao.insertConversationIgnore(Conversation(peerPubkey, event.content, event.createdAt))
        chatDao.insertMessage(message)
        chatDao.updateLastMessage(peerPubkey, event.content, event.createdAt)
        if (event.pubkey != myPubKey) {
            chatDao.incrementUnread(peerPubkey)
        }
    }

    suspend fun markAsRead(peerPubkey: String) {
        chatDao.resetUnread(peerPubkey)
    }
}
