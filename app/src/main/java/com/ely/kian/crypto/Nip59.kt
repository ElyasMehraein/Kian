package com.ely.kian.crypto

import com.ely.kian.data.remote.model.NostrEvent
import kotlinx.serialization.json.*
import java.util.Random

object Nip59 {
    private const val KIND_GIFT_WRAP = 1059
    private const val KIND_GIFT_SEAL = 13

    fun giftWrap(
        innerEventJson: String,
        senderPrivKey: ByteArray,
        recipientPubKey: ByteArray,
        innerEventPubkey: String
    ): NostrEvent {
        // 1. Create the Seal (Kind 13)
        val sealCreatedAt = randomizedTimestamp()
        val encryptedRumor = Nip44.encrypt(innerEventJson, senderPrivKey, recipientPubKey)
        
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
        val sealJson = Json.encodeToString(NostrEvent.serializer(), seal)
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
            val seal = Json.decodeFromString<NostrEvent>(sealJson)
            
            if (seal.kind != KIND_GIFT_SEAL) return null
            
            // 2. Decrypt Seal to get Rumor (Inner Event)
            val innerJson = Nip44.decrypt(seal.content, recipientPrivKey, KianKeys.hexToBytes(seal.pubkey))
            
            // The inner event is a "rumor" - it might not have an ID or Sig yet in standard NIP-59,
            // but in Kian we expect it to be a valid NostrEvent-like structure.
            return Json.decodeFromString<NostrEvent>(innerJson)
        } catch (e: Exception) {
            android.util.Log.e("Nip59", "Failed to unwrap", e)
            return null
        }
    }

    private fun randomizedTimestamp(): Long {
        val now = System.currentTimeMillis() / 1000
        val window = 48 * 60 * 60 // 48 hours in seconds
        return now - window + Random().nextInt(window * 2)
    }
}
