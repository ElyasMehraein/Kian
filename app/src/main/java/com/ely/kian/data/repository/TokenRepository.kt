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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

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

    private val nostrHandler by lazy {
        TokenNostrHandler(
            keyDao, tokenDao, productDao, relayDao, secureStorage, syncManager, _notifications, json
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getBalances(): Flow<List<BalanceItem>> {
        return keyDao.getKeyFlow().flatMapLatest { key ->
            val pubkey = key?.pubkey ?: return@flatMapLatest flowOf(emptyList())
            getBalancesForPubkey(pubkey)
        }
    }

    fun getBalancesForPubkey(pubkey: String): Flow<List<BalanceItem>> {
        return tokenDao.getUnspentUtxosByOwner(pubkey).map { utxos ->
            val balanceMap = utxos.groupBy { it.assetRef }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            val items = mutableListOf<BalanceItem>()
            for ((assetRef, amount) in balanceMap) {
                val parsed = parseAssetRef(assetRef)
                val definition = parsed?.let { tokenDao.getDefinition(it.assetId, it.producer) }

                if (definition != null) {
                    items.add(BalanceItem(
                        assetRef = assetRef,
                        amount = amount,
                        description = definition.description ?: "",
                        images = parseJsonList(definition.images),
                        name = definition.name,
                        producer = definition.pubkey,
                        categories = parseJsonList(definition.categories),
                        unit = definition.unit,
                        isShowcase = definition.isShowcase
                    ))
                } else if (parsed != null) {
                    val product = productDao.getProduct(parsed.assetId, parsed.producer)
                    if (product != null) {
                        items.add(BalanceItem(
                            assetRef = assetRef,
                            amount = amount,
                            description = product.description ?: "",
                            images = parseJsonList(product.images),
                            name = product.name,
                            producer = product.pubkey,
                            categories = emptyList(),
                            unit = "unit"
                        ))
                    } else {
                        items.add(BalanceItem(
                            assetRef = assetRef,
                            amount = amount,
                            description = "",
                            images = emptyList(),
                            name = formatAssetRef(assetRef),
                            producer = parsed.producer,
                            categories = emptyList(),
                            unit = "unit"
                        ))
                    }
                } else {
                    items.add(BalanceItem(
                        assetRef = assetRef,
                        amount = amount,
                        description = "",
                        images = emptyList(),
                        name = formatAssetRef(assetRef),
                        producer = "",
                        categories = emptyList(),
                        unit = "unit"
                    ))
                }
            }
            items
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
                    if (item.queueScope == "token_transfer") {
                        PendingItem(
                            eventId = item.eventId,
                            utxoId = "offline",
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

            val utxoFlow = tokenDao.getAllActivityUtxos(pubkey).map { utxos ->
                val items = mutableListOf<PendingItem>()
                for (utxo in utxos) {
                    val parsed = parseAssetRef(utxo.assetRef)
                    val definition = parsed?.let { tokenDao.getDefinition(it.assetId, it.producer) }
                    val name = definition?.name ?: formatAssetRef(utxo.assetRef)

                    items.add(PendingItem(
                        eventId = utxo.utxoId,
                        utxoId = utxo.utxoId,
                        assetRef = utxo.assetRef,
                        assetName = name,
                        amount = utxo.amount,
                        recipient = utxo.owner,
                        status = "fulfilled",
                        createdAt = utxo.createdAt
                    ))
                }
                items
            }

            combine(offlineFlow, utxoFlow) { offline, completed ->
                (offline + completed).sortedByDescending { it.createdAt }
            }
        }
    }

    suspend fun updateShowcase(assetRef: String, isShowcase: Boolean) {
        val parsed = parseAssetRef(assetRef) ?: return
        tokenDao.updateShowcase(parsed.assetId, parsed.producer, isShowcase)
    }

    suspend fun updateTokenDetails(
        assetRef: String,
        name: String,
        description: String,
        categories: List<String>
    ) {
        val parsed = parseAssetRef(assetRef) ?: return
        val existing = tokenDao.getDefinition(parsed.assetId, parsed.producer)
        if (existing != null) {
            val updated = existing.copy(
                name = name,
                description = description,
                categories = json.encodeToString(categories)
            )
            tokenDao.upsertDefinition(updated)
        } else {
            val definition = TokenDefinition(
                assetId = parsed.assetId,
                pubkey = parsed.producer,
                productId = null,
                name = name,
                description = description,
                images = "[]",
                categories = json.encodeToString(categories),
                eventId = "",
                isShowcase = false,
                createdAt = System.currentTimeMillis() / 1000
            )
            tokenDao.upsertDefinition(definition)
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

        val rumorJson = json.encodeToString(NostrEvent.serializer(), event)
        
        val giftWrapToProducer = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = privKey,
            recipientPubKey = KianKeys.hexToBytes(utxo.producer),
            innerEventPubkey = myPubkey
        )
        
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
            listOf("t", "trader") 
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

        val rumorJson = json.encodeToString(NostrEvent.serializer(), event)
        
        val giftWrapToRecipient = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = privKey,
            recipientPubKey = KianKeys.hexToBytes(recipientPubkey),
            innerEventPubkey = pubkey
        )
        
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
        nostrHandler.handleTokenEvent(event)
    }
}
