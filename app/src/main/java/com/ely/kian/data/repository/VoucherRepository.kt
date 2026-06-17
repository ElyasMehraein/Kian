package com.ely.kian.data.repository

import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.Nip59
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.*
import com.ely.kian.data.local.entities.VoucherDefinition
import com.ely.kian.data.local.entities.VoucherUtxo
import com.ely.kian.data.local.entities.VoucherCategory
import com.ely.kian.data.local.entities.VoucherCategoryMapping
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class VoucherRepository(
    private val keyDao: KeyDao,
    private val voucherDao: VoucherDao,
    private val relayDao: RelayDao,
    private val offlineQueueDao: OfflineQueueDao,
    private val secureStorage: SecureStorage,
    private val syncManagerProvider: () -> NostrSyncManager
) {
    private val TAG = "VoucherRepository"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val syncManager by lazy { syncManagerProvider() }

    private val _notifications = MutableSharedFlow<String>(replay = 0)
    val notifications = _notifications.asSharedFlow()

    private val nostrHandler by lazy {
        VoucherNostrHandler(
            keyDao, voucherDao, relayDao, secureStorage, syncManager, _notifications, json
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
        return combine(
            voucherDao.getUnspentUtxosByOwner(pubkey),
            voucherDao.getAssetSettingsByPubkey(pubkey)
        ) { utxos, settings ->
            val settingsMap = settings.associateBy { it.assetRef }
            val balanceMap = utxos.groupBy { it.assetRef }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            val items = mutableListOf<BalanceItem>()
            for ((assetRef, amount) in balanceMap) {
                val parsed = parseAssetRef(assetRef)
                val definition = parsed?.let { voucherDao.getDefinition(it.assetId, it.producer) }
                val myCategoryIds = voucherDao.getCategoryIdsForAsset(pubkey, assetRef)
                val isShowcase = settingsMap[assetRef]?.isShowcase ?: false

                if (definition != null) {
                    items.add(BalanceItem(
                        assetRef = assetRef,
                        amount = amount,
                        description = definition.description ?: "",
                        images = parseJsonList(definition.images),
                        name = definition.name,
                        producer = definition.pubkey,
                        categories = myCategoryIds,
                        isShowcase = isShowcase
                    ))
                } else {
                    items.add(BalanceItem(
                        assetRef = assetRef,
                        amount = amount,
                        description = "",
                        images = emptyList(),
                        name = formatAssetRef(assetRef),
                        producer = parsed?.producer ?: "",
                        categories = myCategoryIds,
                        isShowcase = isShowcase
                    ))
                }
            }
            items
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getUtxos(): Flow<List<VoucherUtxo>> {
        return keyDao.getKeyFlow().flatMapLatest { key ->
            val pubkey = key?.pubkey ?: return@flatMapLatest flowOf(emptyList())
            voucherDao.getUnspentUtxosByOwner(pubkey)
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

            val utxoFlow = voucherDao.getAllActivityUtxos(pubkey).map { utxos ->
                val items = mutableListOf<PendingItem>()
                for (utxo in utxos) {
                    val parsed = parseAssetRef(utxo.assetRef)
                    val definition = parsed?.let { voucherDao.getDefinition(it.assetId, it.producer) }
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

    suspend fun updateCategoryShowcase(categoryId: String, isShowcase: Boolean) {
        val pubkey = keyDao.getKey()?.pubkey ?: return
        voucherDao.updateCategoryShowcase(categoryId, pubkey, isShowcase)
    }

    suspend fun updateAssetShowcase(assetRef: String, isShowcase: Boolean) {
        val pubkey = keyDao.getKey()?.pubkey ?: return
        val current = voucherDao.isAssetShowcased(pubkey, assetRef)
        if (current == null) {
            voucherDao.upsertAssetSettings(com.ely.kian.data.local.entities.VoucherAssetSettings(pubkey, assetRef, isShowcase))
        } else {
            voucherDao.updateAssetShowcase(pubkey, assetRef, isShowcase)
        }
    }

    fun getCategories(pubkey: String) = voucherDao.listCategoriesByPubkey(pubkey)

    suspend fun saveCategory(name: String, parentId: String?, level: Int, pubkey: String) {
        val id = "cat-${System.currentTimeMillis()}"
        val category = VoucherCategory(
            id = id,
            pubkey = pubkey,
            name = name,
            parentId = parentId,
            level = level,
            createdAt = System.currentTimeMillis() / 1000
        )
        voucherDao.upsertCategory(category)
    }

    suspend fun deleteCategoryBranch(ids: List<String>, pubkey: String) {
        voucherDao.deleteCategories(pubkey, ids)
    }

    suspend fun isCategoryInUse(ids: List<String>, pubkey: String): Boolean {
        return voucherDao.countMappingsForCategories(pubkey, ids) > 0
    }

    suspend fun updateVoucherCategories(assetRef: String, categoryIds: List<String>) {
        val pubkey = keyDao.getKey()?.pubkey ?: return
        voucherDao.deleteMappingsForAsset(pubkey, assetRef)
        categoryIds.forEach { catId ->
            voucherDao.upsertMapping(VoucherCategoryMapping(pubkey, assetRef, catId))
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
        
        val utxo = voucherDao.getUtxo(utxoId) ?: throw Exception("Selected voucher entry is unavailable")

        if (utxo.owner != myPubkey) {
            throw Exception("You can only send voucher entries you own")
        }

        if (recipientPubkey.isBlank()) {
            throw Exception("Recipient is required")
        }

        if (amount <= 0 || amount > utxo.amount) {
            throw Exception("Enter a valid voucher amount")
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

        voucherDao.markSpent(utxoId)
        
        return id
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

    suspend fun mintToken(
        name: String,
        description: String,
        images: List<String>,
        quantity: Long
    ) {
        val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: throw Exception("No key found")
        val privKey = KianKeys.hexToBytes(privKeyHex)
        val pubkey = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))

        val createdAt = System.currentTimeMillis() / 1000
        val dTag = "ast_${pubkey.take(8)}_$createdAt"
        val assetRef = "35001:$pubkey:$dTag"
        
        val content = buildJsonObject {
            put("amount", quantity)
            put("name", name)
            put("description", description)
            putJsonArray("images") { images.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("categories") { } 
        }.toString()

        val tags = listOf(
            listOf("d", dTag),
            listOf("p", pubkey),
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

        val utxo = VoucherUtxo(
            utxoId = id,
            assetRef = assetRef,
            producer = pubkey,
            owner = pubkey,
            amount = quantity,
            prevUtxoId = null,
            createdAt = createdAt,
            spent = false
        )
        voucherDao.insertUtxo(utxo)

        val definition = VoucherDefinition(
            assetId = dTag,
            pubkey = pubkey,
            name = name,
            description = description,
            images = json.encodeToString(images),
            eventId = id,
            createdAt = createdAt
        )
        voucherDao.upsertDefinition(definition)

        val rumorJson = json.encodeToString(NostrEvent.serializer(), event)
        val giftWrapToSelf = Nip59.giftWrap(
            innerEventJson = rumorJson,
            senderPrivKey = privKey,
            recipientPubKey = KianKeys.hexToBytes(pubkey),
            innerEventPubkey = pubkey
        )

        val myOutbox = relayDao.getDmInboxRelayUrls(pubkey)
        syncManager.publishEvent(giftWrapToSelf, myOutbox)
    }
}
