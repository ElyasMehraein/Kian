package com.ely.kian.data.repository

import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.*
import com.ely.kian.data.local.entities.VoucherUtxo
import com.ely.kian.data.local.entities.VoucherCategory
import com.ely.kian.data.local.entities.VoucherCategoryMapping
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class ShowcaseMapping(val assetRef: String, val categoryId: String, val isShowcase: Boolean)

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
        val normalizedPubkey = KianKeys.normalizePubkey(pubkey)
        return combine(
            voucherDao.getUnspentUtxosByOwner(normalizedPubkey).onStart { emit(emptyList()) },
            voucherDao.getAssetSettingsByPubkey(normalizedPubkey).onStart { emit(emptyList()) },
            voucherDao.getAllMappingsByPubkey(normalizedPubkey).onStart { emit(emptyList()) },
            voucherDao.getAllDefinitionsFlow().onStart { emit(emptyList()) }
        ) { utxos, settings, mappings, definitions ->
            val settingsMap = settings.associateBy { it.assetRef }
            val mappingsMap = mappings.groupBy { it.assetRef }
                .mapValues { it.value.map { m -> m.categoryId } }
            
            val defsMap = definitions.associateBy { "35001:${KianKeys.normalizePubkey(it.pubkey)}:${it.assetId}" }

            val balanceMap = utxos.groupBy { it.assetRef }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            val producedAssetRefs = definitions
                .filter { KianKeys.normalizePubkey(it.pubkey) == normalizedPubkey }
                .map { "35001:${KianKeys.normalizePubkey(it.pubkey)}:${it.assetId}" }

            val mappedAssetRefs = mappings.map { it.assetRef }

            val allRelevantRefs = (balanceMap.keys + producedAssetRefs + mappedAssetRefs).distinct()

            allRelevantRefs.map { assetRef ->
                val amountFromUtxos = balanceMap[assetRef] ?: 0L
                val definition = defsMap[assetRef]
                val myCategoryIds = mappingsMap[assetRef] ?: emptyList()
                
                val parsed = KianKeys.parseAssetRef(assetRef)
                // Default to false for new items to respect Privacy-first
                val isShowcase = settingsMap[assetRef]?.isShowcase ?: false

                // Real balance if we own it, otherwise the advertised amount from definition
                val finalAmount = if (amountFromUtxos > 0) amountFromUtxos else (definition?.amount ?: 0L)

                BalanceItem(
                    assetRef = assetRef,
                    amount = finalAmount,
                    description = definition?.description ?: "",
                    images = definition?.images ?: emptyList(),
                    name = definition?.name ?: formatAssetRef(assetRef),
                    producer = definition?.pubkey ?: (parsed?.producer ?: ""),
                    categories = myCategoryIds,
                    isShowcase = isShowcase
                )
            }.sortedByDescending { it.amount }
        }
    }

    fun getUtxos(): Flow<List<VoucherUtxo>> {
        return keyDao.getKeyFlow().flatMapLatest { key ->
            val pubkey = key?.pubkey ?: return@flatMapLatest flowOf(emptyList())
            voucherDao.getUtxosByOwner(pubkey)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPendingConfirmations(): Flow<List<PendingItem>> {
        return keyDao.getKeyFlow().flatMapLatest { key ->
            val rawPubkey = key?.pubkey ?: return@flatMapLatest flowOf(emptyList())
            val myPubkey = KianKeys.normalizePubkey(rawPubkey)
            
            val utxoFlow = voucherDao.getAllActivityUtxos(myPubkey)
            val offlineFlow = offlineQueueDao.getAll().map { queue ->
                queue.filter { item -> item.queueScope == "token_transfer" }
                .map { item ->
                    PendingItem(
                        eventId = item.eventId,
                        utxoId = "",
                        assetRef = "",
                        assetName = "Transfer (Offline)",
                        amount = 0,
                        recipient = item.peerPubkey ?: "Unknown",
                        status = "pending",
                        type = "send",
                        createdAt = item.createdAt
                    )
                }
            }

            combine(offlineFlow, utxoFlow) { offline, completed ->
                val completedItems = completed.map { 
                    val isIncoming = it.owner == myPubkey
                    PendingItem(
                        eventId = it.utxoId,
                        utxoId = it.utxoId,
                        assetRef = it.assetRef,
                        assetName = "",
                        amount = it.amount,
                        recipient = if (isIncoming) it.producer else it.owner,
                        status = "completed",
                        type = if (isIncoming) "receive" else "send",
                        createdAt = it.createdAt
                    )
                }
                (offline + completedItems).sortedByDescending { it.createdAt }
            }
        }
    }

    suspend fun updateCategoryShowcase(categoryId: String, isShowcase: Boolean) {
        val rawPubkey = keyDao.getKey()?.pubkey ?: return
        val pubkey = KianKeys.normalizePubkey(rawPubkey)
        voucherDao.updateCategoryShowcase(categoryId, pubkey, isShowcase)
    }

    suspend fun updateAssetShowcase(assetRef: String, isShowcase: Boolean) {
        val rawPubkey = keyDao.getKey()?.pubkey ?: return
        val pubkey = KianKeys.normalizePubkey(rawPubkey)
        val current = voucherDao.isAssetShowcased(pubkey, assetRef)
        if (current == null) {
            voucherDao.upsertAssetSettings(com.ely.kian.data.local.entities.VoucherAssetSettings(pubkey, assetRef, isShowcase))
        } else {
            voucherDao.updateAssetShowcase(pubkey, assetRef, isShowcase)
        }
        publishShowcase()
    }

    fun getCategories(pubkey: String) = voucherDao.listCategoriesByPubkey(KianKeys.normalizePubkey(pubkey))

    fun getDefinitionsByProducer(pubkey: String) = voucherDao.getDefinitionsByProducer(KianKeys.normalizePubkey(pubkey))

    fun getShowcaseMappings(pubkey: String): Flow<List<ShowcaseMapping>> {
        val normalized = KianKeys.normalizePubkey(pubkey)
        return combine(
            voucherDao.getAllMappingsByPubkey(normalized).onStart { emit(emptyList()) },
            voucherDao.getAssetSettingsByPubkey(normalized).onStart { emit(emptyList()) }
        ) { mappings, settings ->
            mappings.map { m ->
                val isShowcase = settings.find { it.assetRef == m.assetRef }?.isShowcase ?: false
                ShowcaseMapping(m.assetRef, m.categoryId, isShowcase)
            }
        }
    }

    suspend fun saveCategory(name: String, parentId: String?, level: Int, pubkey: String) {
        val id = "cat-${System.currentTimeMillis()}"
        val category = VoucherCategory(
            id = id,
            pubkey = KianKeys.normalizePubkey(pubkey),
            name = name,
            parentId = parentId,
            level = level,
            isShowcase = false,
            createdAt = System.currentTimeMillis() / 1000
        )
        voucherDao.upsertCategory(category)
        publishShowcase()
    }

    suspend fun deleteCategoryBranch(categoryIds: List<String>, pubkey: String) {
        voucherDao.deleteCategories(KianKeys.normalizePubkey(pubkey), categoryIds)
        publishShowcase()
    }

    suspend fun isCategoryInUse(categoryIds: List<String>, pubkey: String): Boolean {
        return voucherDao.countMappingsForCategories(KianKeys.normalizePubkey(pubkey), categoryIds) > 0
    }

    suspend fun updateVoucherCategories(assetRef: String, categoryIds: List<String>) {
        val pubkey = keyDao.getKey()?.pubkey ?: return
        val normalized = KianKeys.normalizePubkey(pubkey)
        voucherDao.deleteMappingsForAsset(normalized, assetRef)
        categoryIds.forEach { catId ->
            voucherDao.upsertMapping(VoucherCategoryMapping(normalized, assetRef, catId))
        }
        publishShowcase()
    }

    suspend fun publishShowcase() {
        try {
            val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
            val privKey = KianKeys.hexToBytes(privKeyHex)
            val pubkey = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))

            val categories = voucherDao.listCategoriesByPubkey(pubkey).first()
            val mappings = voucherDao.getAllMappingsByPubkey(pubkey).first()
            val settings = voucherDao.getAssetSettingsByPubkey(pubkey).first()
            
            val now = System.currentTimeMillis() / 1000

            // Kind 30017: Categorized Archive
            val content = "Kian Showcase"
            val tags = mutableListOf<List<String>>()
            tags.add(listOf("d", "kian_showcase"))
            
            // Standard Nostr Tags for Categories and Mappings
            // Format: ["c", id, name, parentId]
            categories.forEach { cat ->
                tags.add(listOf("c", cat.id, cat.name, cat.parentId ?: ""))
            }
            
            val definitions = voucherDao.getAllDefinitionsFlow().first()

            val utxosList = voucherDao.getUnspentUtxosByOwner(pubkey).first()
            
            // Format: ["a", assetRef, relay, categoryId, isShowcase]
            mappings.forEach { m ->
                val isShowcase = settings.find { it.assetRef == m.assetRef }?.isShowcase ?: false
                if (isShowcase) {
                    tags.add(listOf("a", m.assetRef, "", m.categoryId, "true"))
                    
                    // Ensure Kind 35001 is public for showcased items
                    val parsed = KianKeys.parseAssetRef(m.assetRef)
                    if (parsed?.producer == pubkey) {
                        val def = definitions.find { it.assetId == parsed.assetId && it.pubkey == pubkey }
                        if (def != null) {
                            val defTags = mutableListOf<List<String>>()
                            defTags.add(listOf("d", def.assetId))
                            defTags.add(listOf("name", def.name))
                            if (!def.description.isNullOrEmpty()) defTags.add(listOf("description", def.description))
                            def.images.forEach { defTags.add(listOf("image", it)) }
                            defTags.add(listOf("amount", amountForDef(m.assetRef, utxosList).toString()))
                            
                            val defId = KianKeys.computeEventId(pubkey, def.createdAt, 35001, defTags, "")
                            val defSig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(defId), privKey))
                            val defEvent = NostrEvent(defId, pubkey, def.createdAt, 35001, defTags, "", defSig)
                            syncManager.publishEvent(defEvent)
                        }
                    }
                }
            }

            val id = KianKeys.computeEventId(pubkey, now, 30017, tags, content)
            val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), privKey))

            val event = NostrEvent(id, pubkey, now, 30017, tags, content, sig)
            syncManager.publishEvent(event)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to publish showcase", e)
        }
    }

    private fun amountForDef(assetRef: String, utxos: List<VoucherUtxo>): Long {
        return utxos.filter { it.assetRef == assetRef && !it.spent }.sumOf { it.amount }
    }

    fun formatAssetRef(assetRef: String): String {
        val parsed = KianKeys.parseAssetRef(assetRef) ?: return assetRef
        return "${parsed.producer.take(6)}:${parsed.assetId.take(10)}"
    }

    suspend fun lockTokenForPurchase(utxoId: String, amount: Long, merchantPubkey: String): String {
        return nostrHandler.sendTransfer(utxoId, amount, merchantPubkey)
    }

    suspend fun sendTokenTransfer(utxoId: String, amount: Long, recipientPubkey: String): String {
        return nostrHandler.sendTransfer(utxoId, amount, recipientPubkey)
    }

    suspend fun confirmReceipt(transferEventId: String, producerPubkey: String) {
        nostrHandler.sendReceiptConfirmation(transferEventId, producerPubkey)
    }

    suspend fun handleTokenEvent(event: NostrEvent) {
        nostrHandler.handleTokenEvent(event)
    }

    suspend fun republishDefinitions() {
    }

    suspend fun mintToken(name: String, description: String, images: List<String>, amount: Long) {
        try {
            val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
            val privKey = KianKeys.hexToBytes(privKeyHex)
            val pubkey = KianKeys.bytesToHex(KianKeys.getPubKey(privKey))

            val now = System.currentTimeMillis() / 1000
            val assetId = "v-${System.currentTimeMillis()}"

            // Standard Kind 35001 for Asset Definition
            // NIP-01: Use 'd' tag for replaceable events
            val tags = mutableListOf<List<String>>()
            tags.add(listOf("d", assetId))
            tags.add(listOf("name", name))
            if (description.isNotEmpty()) tags.add(listOf("description", description))
            images.forEach { tags.add(listOf("image", it)) }
            tags.add(listOf("amount", amount.toString()))

            // Content can be a JSON for additional structured data or just empty
            val content = buildJsonObject {
                put("name", name)
                put("description", description)
                put("images", json.encodeToJsonElement(images))
                put("amount", amount)
            }.toString()

            val id = KianKeys.computeEventId(pubkey, now, 35001, tags, content)
            val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), privKey))

            val event = NostrEvent(id, pubkey, now, 35001, tags, content, sig)
            
            // 1. Save Locally
            val definition = com.ely.kian.data.local.entities.VoucherDefinition(
                assetId = assetId,
                pubkey = pubkey,
                name = name,
                description = description,
                images = images,
                amount = amount,
                eventId = id,
                createdAt = now
            )
            voucherDao.upsertDefinition(definition)

            val assetRef = "35001:$pubkey:$assetId"
            val utxo = com.ely.kian.data.local.entities.VoucherUtxo(
                utxoId = id,
                assetRef = assetRef,
                producer = pubkey,
                owner = pubkey,
                amount = amount,
                prevUtxoId = null,
                createdAt = now,
                spent = false
            )
            voucherDao.insertUtxo(utxo)

            // 2. Local Save only (Private by default)
            syncManager.publishEvent(event) // Still publish to local relay if connected, but we'll control global visibility via Showcase
            _notifications.emit("Voucher minted locally (Private)")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to mint token", e)
            _notifications.emit("Error minting voucher")
        }
    }
}
