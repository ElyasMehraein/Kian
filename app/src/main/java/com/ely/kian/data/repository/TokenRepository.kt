package com.ely.kian.data.repository

import com.ely.kian.data.local.dao.*
import com.ely.kian.data.local.entities.TokenUtxo
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
    private val chatDao: ChatDao,
    private val offlineQueueDao: OfflineQueueDao
) {
    private val json = Json { ignoreUnknownKeys = true }

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

            // Combine messages and offline queue
            chatDao.listTokenTransfers().map { messages ->
                messages.filter { it.sender == pubkey || it.conversationPubkey == pubkey }.mapNotNull { message ->
                    try {
                        val contentJson = json.parseToJsonElement(message.content).jsonObject
                        val utxoId = contentJson["utxo_id"]?.jsonPrimitive?.content
                        val assetRef = contentJson["asset_ref"]?.jsonPrimitive?.content
                        val amount = contentJson["amount"]?.jsonPrimitive?.content?.toLongOrNull()
                        val recipient = contentJson["recipient"]?.jsonPrimitive?.content
                        val requestStatus = contentJson["request_status"]?.jsonPrimitive?.content ?: "waiting_mint"

                        if (utxoId != null && assetRef != null && amount != null && recipient != null) {
                            PendingItem(
                                eventId = message.id,
                                utxoId = utxoId,
                                assetRef = assetRef,
                                amount = amount,
                                recipient = recipient,
                                status = when (requestStatus) {
                                    "fulfilled" -> "fulfilled"
                                    "rejected" -> "rejected"
                                    else -> "waiting_mint"
                                },
                                createdAt = message.createdAt
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
            }
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
}
