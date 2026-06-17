package com.ely.kian.data.repository

import android.util.Log
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.Nip59
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.dao.KeyDao
import com.ely.kian.data.local.dao.RelayDao
import com.ely.kian.data.local.dao.VoucherDao
import com.ely.kian.data.local.entities.VoucherDefinition
import com.ely.kian.data.local.entities.VoucherUtxo
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class VoucherNostrHandler(
    private val keyDao: KeyDao,
    private val voucherDao: VoucherDao,
    private val relayDao: RelayDao,
    private val secureStorage: SecureStorage,
    private val syncManager: NostrSyncManager,
    private val notifications: MutableSharedFlow<String>,
    private val json: Json
) {
    private val TAG = "VoucherNostrHandler"

    suspend fun handleTokenEvent(event: NostrEvent) {
        when (event.kind) {
            35001 -> handleGenesis(event)
            35002 -> handleRemint(event)
            30017 -> handleShowcaseSync(event)
            1050 -> handleTransferRequest(event)
            1051 -> handleReceiptConfirmation(event)
        }
    }

    private suspend fun handleShowcaseSync(event: NostrEvent) {
        val dTag = event.tags.find { it.size >= 2 && it[0] == "d" }?.get(1)
        if (dTag != "kian_showcase" && event.kind != 30017) return
        
        val author = KianKeys.normalizePubkey(event.pubkey)
        val myPubkey = keyDao.getKey()?.pubkey?.let { KianKeys.normalizePubkey(it) }
        val isOwnEvent = author == myPubkey

        try {
            // Standard Tags: ["c", id, name, parentId]
            val categoryTags = event.tags.filter { it.size >= 3 && it[0] == "c" }
            val idToParent = categoryTags.associate { it[1] to (if (it.size >= 4 && it[3].isNotEmpty()) it[3] else null) }
            
            // Calculate levels properly
            val levelMap = mutableMapOf<String, Int>()
            fun getLevel(id: String, visited: Set<String>): Int {
                if (id in levelMap) return levelMap[id]!!
                if (id in visited) return 1 // Cycle detected, treat as root
                
                val parentId = idToParent[id]
                val level = if (parentId == null || !idToParent.containsKey(parentId)) {
                    1
                } else {
                    getLevel(parentId, visited + id) + 1
                }
                levelMap[id] = level
                return level
            }

            val categories = categoryTags.map { t ->
                val id = t[1]
                com.ely.kian.data.local.entities.VoucherCategory(
                    id = id,
                    pubkey = author,
                    name = t[2],
                    parentId = idToParent[id],
                    level = getLevel(id, emptySet()),
                    isShowcase = false,
                    createdAt = event.createdAt
                )
            }

            // Standard Tags: ["a", assetRef, relay, categoryId, isShowcase]
            val mappings = event.tags.mapNotNull { t ->
                if (t.size >= 4 && t[0] == "a") {
                    val assetRef = t[1]
                    val catId = t[3]
                    val isShowcase = if (t.size >= 5) t[4].toBoolean() else true
                    Triple(assetRef, catId, isShowcase)
                } else null
            }

            // For observers, the incoming event is the absolute Source of Truth
            if (!isOwnEvent) {
                voucherDao.deleteCategoriesByPubkey(author)
                voucherDao.deleteMappingsByPubkey(author)
                voucherDao.deleteAssetSettingsByPubkey(author)
            }

            // Apply categories
            categories.forEach { voucherDao.upsertCategory(it) }

            // Apply mappings and settings
            mappings.forEach { (assetRef, catId, isShowcase) ->
                voucherDao.upsertMapping(com.ely.kian.data.local.entities.VoucherCategoryMapping(author, assetRef, catId))
                voucherDao.upsertAssetSettings(com.ely.kian.data.local.entities.VoucherAssetSettings(author, assetRef, isShowcase))
            }
            
            Log.d(TAG, "Synced showcase for $author (Categories: ${categories.size}, Mappings: ${mappings.size})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle showcase sync", e)
        }
    }

    private suspend fun handleTransferRequest(event: NostrEvent) {
        val myPrivKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return
        val myPrivKey = KianKeys.hexToBytes(myPrivKeyHex)
        val myPubkey = KianKeys.bytesToHex(KianKeys.getPubKey(myPrivKey))
        
        val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1)
        if (pTag != myPubkey) return

        try {
            val contentObj = json.parseToJsonElement(event.content).jsonObject
            val utxoId = contentObj["utxo_id"]?.jsonPrimitive?.content ?: return
            val amount = contentObj["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val recipient = contentObj["recipient"]?.jsonPrimitive?.content ?: return
            val assetRef = contentObj["asset_ref"]?.jsonPrimitive?.content ?: return
            val isRedemption = contentObj["type"]?.jsonPrimitive?.content == "redemption"

            val existingUtxo = voucherDao.getUtxo(utxoId)
            if (existingUtxo == null || existingUtxo.producer != myPubkey) {
                return
            }
            
            if (existingUtxo.spent) return

            voucherDao.markSpent(utxoId)

            if (isRedemption) {
                notifications.emit("🎁 Voucher redemption request from ${event.pubkey}")
            } else {
                issueRemint(recipient, assetRef, amount, utxoId, myPrivKey)
                if (event.pubkey != recipient) {
                    notifySenderOfApproval(event.pubkey, recipient, assetRef, amount, utxoId, myPrivKey)
                }
                val change = existingUtxo.amount - amount
                if (change > 0) {
                    issueRemint(event.pubkey, assetRef, change, utxoId, myPrivKey)
                }
                notifications.emit("✅ Approved voucher transfer ($amount units) to $recipient")
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

        val tags = listOf(listOf("a", assetRef), listOf("p", recipientPubkey))
        val id = KianKeys.computeEventId(myPubkey, createdAt, 35002, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), myPrivKey))

        val event = NostrEvent(id, myPubkey, createdAt, 35002, tags, content, sig)
        val rumor = json.encodeToString(event)
        syncManager.publishEvent(Nip59.giftWrap(rumor, myPrivKey, KianKeys.hexToBytes(senderPubkey), myPubkey), relayDao.getDmInboxRelayUrls(senderPubkey))
    }

    private suspend fun handleReceiptConfirmation(event: NostrEvent) {
        val myPubkey = keyDao.getKey()?.pubkey ?: return
        val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1)
        if (pTag != myPubkey) return
        notifications.emit("Voucher receipt confirmed by ${event.pubkey}")
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

        val tags = listOf(listOf("a", assetRef), listOf("p", recipientPubkey))
        val id = KianKeys.computeEventId(myPubkey, createdAt, 35002, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), myPrivKey))

        val event = NostrEvent(id, myPubkey, createdAt, 35002, tags, content, sig)
        val rumor = json.encodeToString(event)
        syncManager.publishEvent(Nip59.giftWrap(rumor, myPrivKey, KianKeys.hexToBytes(recipientPubkey), myPubkey), relayDao.getDmInboxRelayUrls(recipientPubkey))
        
        voucherDao.insertUtxo(VoucherUtxo(id, assetRef, myPubkey, recipientPubkey, amount, prevUtxoId, createdAt, false))
    }

    private suspend fun handleGenesis(event: NostrEvent) {
        val dTag = event.tags.find { it.size >= 2 && it[0] == "d" }?.get(1) ?: return
        val author = KianKeys.normalizePubkey(event.pubkey)
        val assetRef = "35001:$author:$dTag"
        
        try {
            val myPubkey = keyDao.getKey()?.pubkey?.let { KianKeys.normalizePubkey(it) }
            val recipient = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1)?.let { KianKeys.normalizePubkey(it) } ?: author
            
            // Try tags first (Standard Nostr way), then content (Legacy/Structured)
            val name = event.tags.find { it[0] == "name" }?.get(1)
                ?: json.parseToJsonElement(event.content).jsonObject["name"]?.jsonPrimitive?.content ?: dTag
                
            val description = event.tags.find { it[0] == "description" }?.get(1)
                ?: json.parseToJsonElement(event.content).jsonObject["description"]?.jsonPrimitive?.contentOrNull
                
            val images = event.tags.filter { it[0] == "image" }.map { it[1] }.ifEmpty {
                try {
                    val contentObj = json.parseToJsonElement(event.content).jsonObject
                    json.decodeFromJsonElement<List<String>>(contentObj["images"] ?: JsonArray(emptyList()))
                } catch (e: Exception) { emptyList() }
            }

            val amount = event.tags.find { it[0] == "amount" }?.get(1)?.toLongOrNull()
                ?: json.parseToJsonElement(event.content).jsonObject["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

            val definition = VoucherDefinition(
                assetId = dTag,
                pubkey = author,
                name = name,
                description = description,
                images = images,
                eventId = event.id,
                createdAt = event.createdAt
            )
            voucherDao.upsertDefinition(definition)

            if (recipient == myPubkey && voucherDao.getUtxo(event.id) == null) {
                voucherDao.insertUtxo(VoucherUtxo(event.id, assetRef, author, recipient, amount, null, event.createdAt, false))
                notifications.emit("New voucher received: ${definition.name} ($amount)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle genesis", e)
        }
    }

    private suspend fun handleRemint(event: NostrEvent) {
        val aTag = event.tags.find { it.size >= 2 && it[0] == "a" }?.get(1) ?: return
        val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1) ?: return
        
        try {
            val myPubkey = keyDao.getKey()?.pubkey
            if (pTag != myPubkey || voucherDao.getUtxo(event.id) != null) return

            val contentObj = json.parseToJsonElement(event.content).jsonObject
            val prevUtxoId = contentObj["previous_utxo"]?.jsonPrimitive?.content
            val amount = contentObj["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            
            prevUtxoId?.let { voucherDao.markSpent(it) }
            
            voucherDao.insertUtxo(VoucherUtxo(event.id, aTag, event.pubkey, pTag, amount, prevUtxoId, event.createdAt, false))
            
            val parsed = KianKeys.parseAssetRef(aTag)
            val name = parsed?.let { voucherDao.getDefinition(it.assetId, it.producer)?.name } ?: "Asset"
            notifications.emit("Voucher transfer completed: $name ($amount units)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle remint", e)
        }
    }
}
