package com.ely.kian.data.repository

import android.util.Log
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.Nip59
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.*
import com.ely.kian.data.local.entities.TokenDefinition
import com.ely.kian.data.local.entities.TokenUtxo
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class BalanceItem(
    val assetRef: String,
    val amount: Long,
    val description: String,
    val images: List<String>,
    val name: String,
    val producer: String,
    val categories: List<String>,
    val unit: String
)

data class PendingItem(
    val eventId: String,
    val utxoId: String,
    val assetRef: String,
    val assetName: String,
    val amount: Long,
    val recipient: String,
    val status: String, // 'waiting_mint' | 'fulfilled' | 'rejected' | 'offline'
    val createdAt: Long
)

class TokenRepository(
    private val keyDao: KeyDao,
    private val tokenDao: TokenDao,
    private val productDao: ProductDao,
    private val relayDao: RelayDao,
    private val offlineQueueDao: OfflineQueueDao,
    private val secureStorage: SecureStorage,
    private val syncManagerProvider: () -> NostrSyncManager
) {
    private val TAG = "TokenRepository"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val syncManager by lazy { syncManagerProvider() }

    private val _notifications = MutableSharedFlow<String>(replay = 0)
    val notifications = _notifications.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getBalances(): Flow<List<BalanceItem>> {
        return keyDao.getKeyFlow().flatMapLatest { key ->
            val pubkey = key?.pubkey ?: return@flatMapLatest flowOf(emptyList())

            tokenDao.getUnspentUtxosByOwner(pubkey).map { utxos ->
                val balanceMap = utxos.groupBy { it.assetRef }
                    .mapValues { entry -> entry.value.sumOf { it.amount } }

                balanceMap.map { (assetRef, amount) ->
                    val parsed = parseAssetRef(assetRef)
                    // Note: This is calling suspend functions inside map. 
                    // In a production app, we might want to pre-fetch or use a more reactive approach.
                    val definition = parsed?.let { tokenDao.getDefinition(it.assetId, it.producer) }

                    if (definition != null) {
                        BalanceItem(
                            assetRef = assetRef,
                            amount = amount,
                            description = definition.description ?: "",
                            images = parseJsonList(definition.images),
                            name = definition.name,
                            producer = definition.pubkey,
                            categories = parseJsonList(definition.categories),
                            unit = definition.unit
                        )
                    } else if (parsed != null) {
                        val product = productDao.getProduct(parsed.assetId, parsed.producer)
                        if (product != null) {
                            BalanceItem(
                                assetRef = assetRef,
                                amount = amount,
                                description = product.description ?: "",
                                images = parseJsonList(product.images),
                                name = product.name,
                                producer = product.pubkey,
                                categories = emptyList(),
                                unit = "unit"
                            )
                        } else {
                            BalanceItem(
                                assetRef = assetRef,
                                amount = amount,
                                description = "",
                                images = emptyList(),
                                name = formatAssetRef(assetRef),
                                producer = parsed.producer,
                                categories = emptyList(),
                                unit = "unit"
                            )
                        }
                    } else {
                        BalanceItem(
                            assetRef = assetRef,
                            amount = amount,
                            description = "",
                            images = emptyList(),
                            name = formatAssetRef(assetRef),
                            producer = "",
                            categories = emptyList(),
                            unit = "unit"
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getUtxos(): Flow<List<TokenUtxo>> {
        return keyDao.getKeyFlow().flatMapLatest { key ->
            val pubkey = key?.pubkey ?: return@flatMapLatest flowOf(emptyList())
            tokenDao.getUnspentUtxosByOwner(pubkey)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getPendingConfirmations(): Flow<List<PendingItem>> {
        return keyDao.getKeyFlow().flatMapLatest { key ->
            val pubkey = key?.pubkey ?: return@flatMapLatest flowOf(emptyList())

            val offlineFlow = offlineQueueDao.getAll().map { queue ->
                queue.mapNotNull { item ->
                    // Simplified: In a real app, we'd decode CBOR and check Kind 1050
                    // For now, if scope is "token_transfer", we treat it as pending
                    if (item.queueScope == "token_transfer") {
                        PendingItem(
                            eventId = item.eventId,
                            utxoId = "offline", // Extract from CBOR in real app
                            assetRef = "offline",
                            assetName = "Offline Transfer",
                            amount = 0,
                            recipient = item.peerPubkey ?: "",
                            status = "offline",
                            createdAt = item.createdAt
                        )
                    } else null
                }
            }

            offlineFlow.map { it.sortedByDescending { item -> item.createdAt } }
        }
    }

    private fun parseAssetRef(assetRef: String): ParsedAsset? {
        val parts = assetRef.split(":")
        if (parts.size < 3) return null
        return ParsedAsset(parts[1], parts[2])
    }

    private fun formatAssetRef(assetRef: String): String {
        if (assetRef.length < 16) return assetRef
        return "${assetRef.take(10)}...${assetRef.takeLast(6)}"
    }

    private fun parseJsonList(jsonStr: String): List<String> {
        return try {
            json.decodeFromString<List<String>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private data class ParsedAsset(val producer: String, val assetId: String)

    suspend fun sendTokenTransfer(utxoId: String, amount: Long, recipientPubkey: String): String {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: throw Exception("No key found")
        val privKey = KianKeys.hexToBytes(privKeyHex)
        val myPubkey = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))
        
        val utxo = tokenDao.getUtxo(utxoId) ?: throw Exception("Selected token entry is unavailable")

        if (utxo.owner != myPubkey) {
            throw Exception("You can only send token entries you own")
        }

        if (recipientPubkey.isBlank()) {
            throw Exception("Recipient is required")
        }

        if (amount <= 0 || amount > utxo.amount) {
            throw Exception("Enter a valid token amount")
        }

        val isToProducer = recipientPubkey == utxo.producer

        // 1. Create Kind 1050 Transfer Request
        val createdAt = System.currentTimeMillis() / 1000
        val content = buildJsonObject {
            put("utxo_id", utxoId)
            put("asset_ref", utxo.assetRef)
            put("amount", amount)
            put("recipient", recipientPubkey)
            if (isToProducer) {
                put("type", "redemption")
            }
        }.toString()
        
        val tags = listOf(
            listOf("p", utxo.producer),
            listOf("e", utxoId),
            listOf("t", "token_transfer")
        )

        val id = KianKeys.computeEventId(myPubkey, createdAt, 1050, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), privKey))

        val event = NostrEvent(
            id = id,
            pubkey = myPubkey,
            createdAt = createdAt,
            kind = 1050,
            tags = tags,
            content = content,
            sig = sig
        )

        // 2. Wrap and Publish
        val rumorJson = json.encodeToString(NostrEvent.serializer(), event)
        
        // Wrap for Producer (Verification)
        val giftWrapToProducer = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = privKey,
            recipientPubKey = KianKeys.hexToBytes(utxo.producer),
            innerEventPubkey = myPubkey
        )
        
        // Wrap for Recipient (Notification) - if not producer
        if (!isToProducer) {
            val giftWrapToRecipient = Nip59.giftWrap(
                innerEventJson = rumorJson,
                senderPrivKey = privKey,
                recipientPubKey = KianKeys.hexToBytes(recipientPubkey),
                innerEventPubkey = myPubkey
            )
            val recipientInbox = relayDao.getDmInboxRelayUrls(recipientPubkey)
            syncManager.publishEvent(giftWrapToRecipient, recipientInbox)
        }

        val producerInbox = relayDao.getDmInboxRelayUrls(utxo.producer)
        syncManager.publishEvent(giftWrapToProducer, producerInbox)

        // 3. Update local state
        tokenDao.markSpent(utxoId)
        
        return id
    }

    suspend fun mintProduct(recipientPubkey: String, productId: String, quantity: Long) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: throw Exception("No key found")
        val privKey = KianKeys.hexToBytes(privKeyHex)
        val pubkey = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))
        val product = productDao.getProduct(productId, pubkey) ?: throw Exception("Product not found")

        if (product.pubkey != pubkey) {
            throw Exception("You can only mint tokens for your own products")
        }

        // Logic for Kind 35001 (Genesis)
        val createdAt = System.currentTimeMillis() / 1000
        val dTag = "ast_${product.id}_$createdAt"
        val assetRef = "35001:$pubkey:$dTag"
        
        val content = buildJsonObject {
            put("amount", quantity)
            put("unit", "unit")
            put("name", product.name)
            put("description", product.description ?: "")
            try {
                val imagesArray = json.parseToJsonElement(product.images).jsonArray
                put("images", imagesArray)
            } catch (e: Exception) {
                put("images", buildJsonArray { })
            }
            try {
                val categoriesArray = json.parseToJsonElement(product.categories).jsonArray
                put("categories", categoriesArray)
            } catch (e: Exception) {
                put("categories", buildJsonArray { })
            }
        }.toString()

        val tags = listOf(
            listOf("d", dTag),
            listOf("p", recipientPubkey),
            listOf("t", "trader") // Marking as commerce token
        )

        val id = KianKeys.computeEventId(pubkey, createdAt, 35001, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), privKey))

        val event = NostrEvent(
            id = id,
            pubkey = pubkey,
            createdAt = createdAt,
            kind = 35001,
            tags = tags,
            content = content,
            sig = sig
        )

        val utxo = TokenUtxo(
            utxoId = id,
            assetRef = assetRef,
            producer = pubkey,
            owner = recipientPubkey,
            amount = quantity,
            prevUtxoId = null,
            createdAt = createdAt,
            spent = false
        )
        
        tokenDao.insertUtxo(utxo)

        // 5. Wrap and Publish (NIP-59)
        val rumorJson = json.encodeToString(NostrEvent.serializer(), event)
        
        // Wrap for Recipient
        val giftWrapToRecipient = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = privKey,
            recipientPubKey = KianKeys.hexToBytes(recipientPubkey),
            innerEventPubkey = pubkey
        )
        
        // Wrap for Sender (Self-sync)
        val giftWrapToSelf = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = privKey,
            recipientPubKey = KianKeys.hexToBytes(pubkey),
            innerEventPubkey = pubkey
        )

        val recipientInbox = relayDao.getDmInboxRelayUrls(recipientPubkey)
        val myOutbox = relayDao.getDmInboxRelayUrls(pubkey)
        val targetRelays = (recipientInbox + myOutbox).distinct()

        syncManager.publishEvent(giftWrapToRecipient, targetRelays)
        syncManager.publishEvent(giftWrapToSelf, myOutbox)
    }

    suspend fun confirmReceipt(transferEventId: String, recipientPubkey: String) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val privKey = KianKeys.hexToBytes(privKeyHex)
        val myPubkey = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))
        
        val createdAt = System.currentTimeMillis() / 1000
        val content = "I have received the product and confirmed the quality."
        val tags = listOf(
            listOf("e", transferEventId),
            listOf("p", recipientPubkey),
            listOf("t", "receipt_confirmation")
        )

        val id = KianKeys.computeEventId(myPubkey, createdAt, 1051, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), privKey))

        val event = NostrEvent(id, myPubkey, createdAt, 1051, tags, content, sig)
        val rumorJson = json.encodeToString(NostrEvent.serializer(), event)
        
        val giftWrap = Nip59.giftWrap(rumorJson, privKey, KianKeys.hexToBytes(recipientPubkey), myPubkey)
        syncManager.publishEvent(giftWrap)
    }

    suspend fun handleTokenEvent(event: com.ely.kian.data.remote.model.NostrEvent) {
        when (event.kind) {
            35001 -> handleGenesis(event)
            35002 -> handleRemint(event)
            1050 -> handleTransferRequest(event)
            1051 -> handleReceiptConfirmation(event)
        }
    }

    private suspend fun handleTransferRequest(event: NostrEvent) {
        val myPrivKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val myPrivKey = KianKeys.hexToBytes(myPrivKeyHex)
        val myPubkey = KianKeys.bytesToHex(KianKeys.getPubKey(myPrivKey))
        
        // Only process if I am the producer
        val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1)
        if (pTag != myPubkey) return

        try {
            val contentObj = json.parseToJsonElement(event.content).jsonObject
            val utxoId = contentObj["utxo_id"]?.jsonPrimitive?.content ?: return
            val amount = contentObj["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val recipient = contentObj["recipient"]?.jsonPrimitive?.content ?: return
            val assetRef = contentObj["asset_ref"]?.jsonPrimitive?.content ?: return
            val isRedemption = contentObj["type"]?.jsonPrimitive?.content == "redemption"

            // 1. Verify authenticity
            val existingUtxo = tokenDao.getUtxo(utxoId)
            if (existingUtxo == null || existingUtxo.producer != myPubkey) {
                Log.w(TAG, "Received transfer request for unknown or invalid UTXO: $utxoId")
                return
            }
            
            // If the UTXO is already spent, it means we already processed THIS transfer 
            // (or another one for the same UTXO). Since Kian spends the whole UTXO, we are idempotent here.
            if (existingUtxo.spent) {
                Log.i(TAG, "Transfer request for $utxoId ignored as it is already spent.")
                return
            }

            // 2. Mark as spent (Burn)
            tokenDao.markSpent(utxoId)

            if (isRedemption) {
                Log.i(TAG, "Token redemption request received from ${event.pubkey}")
                _notifications.emit("🎁 Product redemption request from ${event.pubkey}")
            } else {
                // 3. Issue Kind 35002 (Remint) for Recipient
                issueRemint(recipient, assetRef, amount, utxoId, myPrivKey)
                
                // If the sender is not the recipient, also notify the sender by sending them a gift-wrapped copy 
                // of the recipient's remint. They will ignore the balance part but use it for UI confirmation.
                if (event.pubkey != recipient) {
                    notifySenderOfApproval(event.pubkey, recipient, assetRef, amount, utxoId, myPrivKey)
                }

                // 4. Issue Kind 35002 (Remint) for Sender (Change) if any
                val change = existingUtxo.amount - amount
                if (change > 0) {
                    issueRemint(event.pubkey, assetRef, change, utxoId, myPrivKey)
                }
                
                Log.i(TAG, "Approved token transfer: $amount from ${event.pubkey} to $recipient")
                _notifications.emit("✅ Approved token transfer ($amount units) to $recipient")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle transfer request", e)
        }
    }

    private suspend fun notifySenderOfApproval(
        senderPubkey: String,
        recipientPubkey: String,
        assetRef: String,
        amount: Long,
        prevUtxoId: String,
        myPrivKey: ByteArray
    ) {
        val myPubkey = KianKeys.bytesToHex(KianKeys.getPubKey(myPrivKey))
        val createdAt = System.currentTimeMillis() / 1000
        
        val content = buildJsonObject {
            put("amount", amount)
            put("previous_utxo", prevUtxoId)
            put("status", "approved")
        }.toString()

        val tags = listOf(
            listOf("a", assetRef),
            listOf("p", recipientPubkey) // Still points to recipient
        )

        val id = KianKeys.computeEventId(myPubkey, createdAt, 35002, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), myPrivKey))

        val remintEvent = NostrEvent(id, myPubkey, createdAt, 35002, tags, content, sig)
        val rumorJson = json.encodeToString(NostrEvent.serializer(), remintEvent)
        
        // Wrap for SENDER
        val giftWrap = Nip59.giftWrap(rumorJson, myPrivKey, KianKeys.hexToBytes(senderPubkey), myPubkey)
        val senderInbox = relayDao.getDmInboxRelayUrls(senderPubkey)
        syncManager.publishEvent(giftWrap, senderInbox)
    }

    private suspend fun handleReceiptConfirmation(event: NostrEvent) {
        val myPubkey = keyDao.getKey()?.pubkey ?: return
        val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1)
        if (pTag != myPubkey) return
        
        val transferEventId = event.tags.find { it.size >= 2 && it[0] == "e" }?.get(1) ?: return
        
        // Mark as fully received/burnt
        Log.i(TAG, "Product receipt confirmed by ${event.pubkey} for transfer $transferEventId")
        _notifications.emit("Product receipt confirmed by ${event.pubkey}")
    }

    private suspend fun issueRemint(
        recipientPubkey: String,
        assetRef: String,
        amount: Long,
        prevUtxoId: String,
        myPrivKey: ByteArray
    ) {
        val myPubkey = KianKeys.bytesToHex(KianKeys.getPubKey(myPrivKey))
        val createdAt = System.currentTimeMillis() / 1000
        
        val content = buildJsonObject {
            put("amount", amount)
            put("previous_utxo", prevUtxoId)
        }.toString()

        val tags = listOf(
            listOf("a", assetRef),
            listOf("p", recipientPubkey)
        )

        val id = KianKeys.computeEventId(myPubkey, createdAt, 35002, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), myPrivKey))

        val remintEvent = NostrEvent(id, myPubkey, createdAt, 35002, tags, content, sig)
        val rumorJson = json.encodeToString(NostrEvent.serializer(), remintEvent)
        
        // Wrap and Publish
        val giftWrap = Nip59.giftWrap(rumorJson, myPrivKey, KianKeys.hexToBytes(recipientPubkey), myPubkey)
        val recipientInbox = relayDao.getDmInboxRelayUrls(recipientPubkey)
        syncManager.publishEvent(giftWrap, recipientInbox)
        
        // CRITICAL: Producer must save the UTXO it issued to OTHERS to verify future transfers
        val utxo = TokenUtxo(
            utxoId = id,
            assetRef = assetRef,
            producer = myPubkey,
            owner = recipientPubkey,
            amount = amount,
            prevUtxoId = prevUtxoId,
            createdAt = createdAt,
            spent = false
        )
        tokenDao.insertUtxo(utxo)
    }


    private suspend fun handleGenesis(event: com.ely.kian.data.remote.model.NostrEvent) {
        val dTag = event.tags.find { it.size >= 2 && it[0] == "d" }?.get(1) ?: return
        val assetRef = "35001:${event.pubkey}:$dTag"
        
        try {
            val myPubkey = keyDao.getKey()?.pubkey
            val recipient = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1) ?: event.pubkey
            
            val contentObj = json.parseToJsonElement(event.content).jsonObject
            
            // 1. Upsert definition (safe to repeat)
            val definition = TokenDefinition(
                assetId = dTag,
                pubkey = event.pubkey,
                productId = null,
                name = contentObj["name"]?.jsonPrimitive?.content ?: dTag,
                description = contentObj["description"]?.jsonPrimitive?.contentOrNull,
                images = contentObj["images"]?.toString() ?: "[]",
                categories = contentObj["categories"]?.toString() ?: "[]",
                unit = contentObj["unit"]?.jsonPrimitive?.content ?: "unit",
                eventId = event.id,
                createdAt = event.createdAt
            )
            tokenDao.upsertDefinition(definition)

            // 2. Check if already processed to prevent duplicate balance/notifications
            if (tokenDao.getUtxo(event.id) != null) return

            // 3. Only save UTXO and notify if it's for me
            if (recipient != myPubkey) return

            val amount = contentObj["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            
            val utxo = TokenUtxo(
                utxoId = event.id,
                assetRef = assetRef,
                producer = event.pubkey,
                owner = recipient,
                amount = amount,
                prevUtxoId = null,
                createdAt = event.createdAt,
                spent = false
            )
            tokenDao.insertUtxo(utxo)
            _notifications.emit("New asset received: ${definition.name} ($amount ${definition.unit})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle genesis", e)
        }
    }

    private suspend fun handleRemint(event: com.ely.kian.data.remote.model.NostrEvent) {
        val aTag = event.tags.find { it.size >= 2 && it[0] == "a" }?.get(1) ?: return
        val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1) ?: return
        
        try {
            val myPubkey = keyDao.getKey()?.pubkey
            
            // 1. Check if already processed
            if (tokenDao.getUtxo(event.id) != null) return

            // 2. Only save and notify if it's for me
            if (pTag != myPubkey) return

            val contentObj = json.parseToJsonElement(event.content).jsonObject
            val prevUtxoId = contentObj["previous_utxo"]?.jsonPrimitive?.content
            val amount = contentObj["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            
            if (prevUtxoId != null) {
                tokenDao.markSpent(prevUtxoId)
            }
            
            val utxo = TokenUtxo(
                utxoId = event.id,
                assetRef = aTag,
                producer = event.pubkey,
                owner = pTag,
                amount = amount,
                prevUtxoId = prevUtxoId,
                createdAt = event.createdAt,
                spent = false
            )
            tokenDao.insertUtxo(utxo)
            
            val parsed = parseAssetRef(aTag)
            val name = parsed?.let { tokenDao.getDefinition(it.assetId, it.producer)?.name } ?: "Asset"
            _notifications.emit("Token transfer completed: $name ($amount units)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle remint", e)
        }
    }
}
