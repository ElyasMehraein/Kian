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
            1050 -> handleTransferRequest(event)
            1051 -> handleReceiptConfirmation(event)
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
                Log.w(TAG, "Received transfer request for unknown or invalid UTXO: $utxoId")
                return
            }
            
            if (existingUtxo.spent) {
                Log.i(TAG, "Transfer request for $utxoId ignored as it is already spent.")
                return
            }

            voucherDao.markSpent(utxoId)

            if (isRedemption) {
                Log.i(TAG, "Voucher redemption request received from ${event.pubkey}")
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
                
                Log.i(TAG, "Approved voucher transfer: $amount from ${event.pubkey} to $recipient")
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

        val tags = listOf(
            listOf("a", assetRef),
            listOf("p", recipientPubkey)
        )

        val id = KianKeys.computeEventId(myPubkey, createdAt, 35002, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), myPrivKey))

        val remintEvent = NostrEvent(id, myPubkey, createdAt, 35002, tags, content, sig)
        val rumorJson = json.encodeToString(remintEvent)
        
        val giftWrap = Nip59.giftWrap(rumorJson, myPrivKey, KianKeys.hexToBytes(senderPubkey), myPubkey)
        val senderInbox = relayDao.getDmInboxRelayUrls(senderPubkey)
        syncManager.publishEvent(giftWrap, senderInbox)
    }

    private suspend fun handleReceiptConfirmation(event: NostrEvent) {
        val myPubkey = keyDao.getKey()?.pubkey ?: return
        val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1)
        if (pTag != myPubkey) return
        
        val transferEventId = event.tags.find { it.size >= 2 && it[0] == "e" }?.get(1) ?: return
        
        Log.i(TAG, "Voucher receipt confirmed by ${event.pubkey} for transfer $transferEventId")
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

        val tags = listOf(
            listOf("a", assetRef),
            listOf("p", recipientPubkey)
        )

        val id = KianKeys.computeEventId(myPubkey, createdAt, 35002, tags, content)
        val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), myPrivKey))

        val remintEvent = NostrEvent(id, myPubkey, createdAt, 35002, tags, content, sig)
        val rumorJson = json.encodeToString(remintEvent)
        
        val giftWrap = Nip59.giftWrap(rumorJson, myPrivKey, KianKeys.hexToBytes(recipientPubkey), myPubkey)
        val recipientInbox = relayDao.getDmInboxRelayUrls(recipientPubkey)
        syncManager.publishEvent(giftWrap, recipientInbox)
        
        val utxo = VoucherUtxo(
            utxoId = id,
            assetRef = assetRef,
            producer = myPubkey,
            owner = recipientPubkey,
            amount = amount,
            prevUtxoId = prevUtxoId,
            createdAt = createdAt,
            spent = false
        )
        voucherDao.insertUtxo(utxo)
    }

    private suspend fun handleGenesis(event: NostrEvent) {
        val dTag = event.tags.find { it.size >= 2 && it[0] == "d" }?.get(1) ?: return
        val assetRef = "35001:${event.pubkey}:$dTag"
        
        try {
            val myPubkey = keyDao.getKey()?.pubkey
            val recipient = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1) ?: event.pubkey
            
            val contentObj = json.parseToJsonElement(event.content).jsonObject
            
            val definition = VoucherDefinition(
                assetId = dTag,
                pubkey = event.pubkey,
                name = contentObj["name"]?.jsonPrimitive?.content ?: dTag,
                description = contentObj["description"]?.jsonPrimitive?.contentOrNull,
                images = contentObj["images"]?.toString() ?: "[]",
                eventId = event.id,
                createdAt = event.createdAt
            )
            voucherDao.upsertDefinition(definition)

            if (voucherDao.getUtxo(event.id) != null) return
            if (recipient != myPubkey) return

            val amount = contentObj["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            
            val utxo = VoucherUtxo(
                utxoId = event.id,
                assetRef = assetRef,
                producer = event.pubkey,
                owner = recipient,
                amount = amount,
                prevUtxoId = null,
                createdAt = event.createdAt,
                spent = false
            )
            voucherDao.insertUtxo(utxo)
            notifications.emit("New voucher received: ${definition.name} ($amount)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle genesis", e)
        }
    }

    private suspend fun handleRemint(event: NostrEvent) {
        val aTag = event.tags.find { it.size >= 2 && it[0] == "a" }?.get(1) ?: return
        val pTag = event.tags.find { it.size >= 2 && it[0] == "p" }?.get(1) ?: return
        
        try {
            val myPubkey = keyDao.getKey()?.pubkey
            
            if (voucherDao.getUtxo(event.id) != null) return
            if (pTag != myPubkey) return

            val contentObj = json.parseToJsonElement(event.content).jsonObject
            val prevUtxoId = contentObj["previous_utxo"]?.jsonPrimitive?.content
            val amount = contentObj["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            
            if (prevUtxoId != null) {
                voucherDao.markSpent(prevUtxoId)
            }
            
            val utxo = VoucherUtxo(
                utxoId = event.id,
                assetRef = aTag,
                producer = event.pubkey,
                owner = pTag,
                amount = amount,
                prevUtxoId = prevUtxoId,
                createdAt = event.createdAt,
                spent = false
            )
            voucherDao.insertUtxo(utxo)
            
            val parsed = parseAssetRef(aTag)
            val name = parsed?.let { voucherDao.getDefinition(it.assetId, it.producer)?.name } ?: "Asset"
            notifications.emit("Voucher transfer completed: $name ($amount units)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle remint", e)
        }
    }

    private fun parseAssetRef(assetRef: String): ParsedAsset? {
        val parts = assetRef.split(":")
        if (parts.size < 3) return null
        return ParsedAsset(parts[1], parts[2])
    }

    private data class ParsedAsset(val producer: String, val assetId: String)
}
