package com.ely.kian.crypto

import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.serialization.json.*
import java.util.Random

object Nip59 {
    private const val KIND_GIFT_WRAP = 1059
    private const val KIND_GIFT_SEAL = 13
    private val json = Json { ignoreUnknownKeys = true }

    fun giftWrap(
        innerEventJson: String,
        senderPrivKey: ByteArray,
        recipientPubKey: ByteArray,
        innerEventPubkey: String
    ): NostrEvent {
        // 0. Parse rumor and sign it
        val rumor = try {
            val element = json.parseToJsonElement(innerEventJson).jsonObject
            val id = element["id"]?.jsonPrimitive?.content ?: KianKeys.computeEventId(
                pubkey = innerEventPubkey,
                createdAt = element["created_at"]!!.jsonPrimitive.long,
                kind = element["kind"]!!.jsonPrimitive.int,
                tags = element["tags"]?.jsonArray?.map { it.jsonArray.map { v -> v.jsonPrimitive.content } } ?: emptyList(),
                content = element["content"]!!.jsonPrimitive.content
            )
            
            val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(id), senderPrivKey))
            
            NostrEvent(
                id = id,
                pubkey = innerEventPubkey,
                createdAt = element["created_at"]!!.jsonPrimitive.long,
                kind = element["kind"]!!.jsonPrimitive.int,
                tags = element["tags"]?.jsonArray?.map { it.jsonArray.map { v -> v.jsonPrimitive.content } } ?: emptyList(),
                content = element["content"]!!.jsonPrimitive.content,
                sig = sig
            )
        } catch (e: Exception) {
            json.decodeFromString<NostrEvent>(innerEventJson)
        }

        if (rumor.sig.isEmpty()) {
            // This is a safety catch: a rumor MUST be signed
            val sig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(rumor.id), senderPrivKey))
            return giftWrapInternal(rumor.copy(sig = sig), senderPrivKey, recipientPubKey, innerEventPubkey)
        }

        return giftWrapInternal(rumor, senderPrivKey, recipientPubKey, innerEventPubkey)
    }

    private fun giftWrapInternal(
        rumor: NostrEvent,
        senderPrivKey: ByteArray,
        recipientPubKey: ByteArray,
        innerEventPubkey: String
    ): NostrEvent {
        val signedRumorJson = json.encodeToString(NostrEvent.serializer(), rumor)

        // 1. Create the Seal (Kind 13)
        val sealCreatedAt = randomizedTimestamp()
        val encryptedRumor = Nip44.encrypt(signedRumorJson, senderPrivKey, recipientPubKey)
        
        val sealId = KianKeys.computeEventId(
            pubkey = innerEventPubkey,
            createdAt = sealCreatedAt,
            kind = KIND_GIFT_SEAL,
            tags = emptyList(),
            content = encryptedRumor
        )
        val sealSig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(sealId), senderPrivKey))
        
        val seal = NostrEvent(
            id = sealId,
            pubkey = innerEventPubkey,
            createdAt = sealCreatedAt,
            kind = KIND_GIFT_SEAL,
            tags = emptyList(),
            content = encryptedRumor,
            sig = sealSig
        )

        // 2. Wrap the Seal in a Gift Wrap (Kind 1059) using an ephemeral key
        val (ephemeralPriv, ephemeralPub) = KianKeys.generateKeyPair()
        val wrapCreatedAt = randomizedTimestamp()
        val sealJson = json.encodeToString(NostrEvent.serializer(), seal)
        val encryptedSeal = Nip44.encrypt(sealJson, ephemeralPriv, recipientPubKey)
        
        val wrapTags = listOf(listOf("p", KianKeys.bytesToHex(recipientPubKey)))
        val wrapId = KianKeys.computeEventId(
            pubkey = KianKeys.bytesToHex(ephemeralPub),
            createdAt = wrapCreatedAt,
            kind = KIND_GIFT_WRAP,
            tags = wrapTags,
            content = encryptedSeal
        )
        val wrapSig = KianKeys.bytesToHex(KianKeys.sign(KianKeys.hexToBytes(wrapId), ephemeralPriv))
        
        return NostrEvent(
            id = wrapId,
            pubkey = KianKeys.bytesToHex(ephemeralPub),
            createdAt = wrapCreatedAt,
            kind = KIND_GIFT_WRAP,
            tags = wrapTags,
            content = encryptedSeal,
            sig = wrapSig
        )
    }

    fun unwrap(
        wrap: NostrEvent,
        recipientPrivKey: ByteArray
    ): NostrEvent? {
        try {
            if (wrap.kind != KIND_GIFT_WRAP) return null
            
            // 1. Decrypt Wrap to get Seal
            val sealJson = Nip44.decrypt(wrap.content, recipientPrivKey, KianKeys.hexToBytes(wrap.pubkey))
            val seal = json.decodeFromString<NostrEvent>(sealJson)
            
            if (seal.kind != KIND_GIFT_SEAL) return null
            
            // 2. Decrypt Seal to get Rumor (Inner Event)
            val innerJson = Nip44.decrypt(seal.content, recipientPrivKey, KianKeys.hexToBytes(seal.pubkey))
            
            // The inner event is a "rumor"
            val rumor = json.decodeFromString<NostrEvent>(innerJson)
            
            // Ensure the rumor has an ID for local storage and tracking
            return if (rumor.id.isEmpty()) {
                rumor.copy(id = KianKeys.computeEventId(
                    pubkey = rumor.pubkey,
                    createdAt = rumor.createdAt,
                    kind = rumor.kind,
                    tags = rumor.tags,
                    content = rumor.content
                ))
            } else {
                rumor
            }
        } catch (e: Exception) {
            android.util.Log.e("Nip59", "Failed to unwrap", e)
            return null
        }
    }

    private fun randomizedTimestamp(): Long {
        val now = System.currentTimeMillis() / 1000
        val window = 48 * 60 * 60 // 48 hours
        // NIP-59: SHOULD be a random time in the past.
        // Random within the last 48 hours to avoid being rejected by relays for future dates.
        return now - java.util.Random().nextInt(window).toLong()
    }
}
