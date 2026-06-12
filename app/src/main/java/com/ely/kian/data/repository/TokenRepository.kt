package com.ely.kian.data.repository

import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.*
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
    val amount: Long,
    val recipient: String,
    val status: String, // 'waiting_mint' | 'fulfilled' | 'rejected' | 'offline'
    val createdAt: Long
)

class TokenRepository(
    private val keyDao: KeyDao,
    private val tokenDao: TokenDao,
    private val productDao: ProductDao,
    private val offlineQueueDao: OfflineQueueDao,
    private val secureStorage: SecureStorage,
    private val syncManagerProvider: () -> NostrSyncManager
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val syncManager by lazy { syncManagerProvider() }

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

    suspend fun sendTokenTransfer(utxoId: String, amount: Long, recipientPubkey: String) {
        val key = keyDao.getKey() ?: throw Exception("No key found")
        val pubkey = key.pubkey
        val utxo = tokenDao.getUtxo(utxoId) ?: throw Exception("Selected token entry is unavailable")

        if (utxo.owner != pubkey) {
            throw Exception("You can only send token entries you own")
        }

        if (recipientPubkey.isBlank()) {
            throw Exception("Recipient is required")
        }

        if (amount <= 0 || amount > utxo.amount) {
            throw Exception("Enter a valid token amount")
        }

        // 1. Send Transfer Request (Kind 1050) to the Producer
        // In Kian, the producer must approve transfers (NIP-protocol.md 2.4)
        val createdAt = System.currentTimeMillis() / 1000
        val content = """{"utxo_id": "$utxoId", "asset_ref": "${utxo.assetRef}", "amount": $amount, "recipient": "$recipientPubkey"}"""
        
        // Tags for Kind 1050: Producer (p) and UTXO (e)
        val tags = listOf(
            listOf("p", utxo.producer),
            listOf("e", utxoId)
        )

        // TODO: In a real NIP-17 implementation, this would be wrapped in NIP-59
        // For now, we use the direct publish pattern or similar
        // Actually, we should probably add this to a generic event publisher
        
        // Marking as spent locally
        tokenDao.markSpent(utxoId)
        
        // Note: The Expo code also sends a notification to the recipient
        // to inform them of the pending transfer.
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
            put("images", product.images) // Already JSON
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
        syncManager.publishEvent(event)
    }

    suspend fun handleTokenEvent(event: com.ely.kian.data.remote.model.NostrEvent) {
        when (event.kind) {
            35001 -> handleGenesis(event)
            35002 -> handleRemint(event)
        }
    }

    private suspend fun handleGenesis(event: com.ely.kian.data.remote.model.NostrEvent) {
        val dTag = event.tags.find { it.size >= 2 && it[0] == "d" }?.get(1) ?: return
        val assetRef = "35001:${event.pubkey}:$dTag"
        
        try {
            val myPubkey = keyDao.getKey()?.pubkey
            val recipient = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1) ?: event.pubkey
            
            // Only save if it's for me
            if (recipient != myPubkey) return

            val content = json.parseToJsonElement(event.content).jsonObject
            val amount = content["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            
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
        } catch (e: Exception) {
            // Log error
        }
    }

    private suspend fun handleRemint(event: com.ely.kian.data.remote.model.NostrEvent) {
        val aTag = event.tags.find { it.size >= 2 && it[0] == "a" }?.get(1) ?: return
        val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1) ?: return
        
        try {
            val myPubkey = keyDao.getKey()?.pubkey
            
            // Only save if it's for me
            if (pTag != myPubkey) return

            val content = json.parseToJsonElement(event.content).jsonObject
            val prevUtxoId = content["previous_utxo"]?.jsonPrimitive?.content
            val amount = content["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            
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
        } catch (e: Exception) {
            // Log error
        }
    }
}
