package com.ely.kian.crypto

import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toSeed
import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.serialization.json.*

object KianKeys {
    private val secp256k1 = Secp256k1.get()
    private val json = Json { encodeDefaults = true }

    fun generateMnemonic(): String {
        val entropy = ByteArray(16) // 128 bits for 12 words
        SecureRandom().nextBytes(entropy)
        return Mnemonics.MnemonicCode(entropy).joinToString(" ")
    }

    fun derivePrivKey(mnemonic: String): ByteArray {
        val seed = Mnemonics.MnemonicCode(mnemonic).toSeed()
        // Simple derivation for now (can be improved to BIP44 if needed)
        // Nostr typically uses the seed directly or a simple hash as private key
        return seed.take(32).toByteArray()
    }

    fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val privKey = ByteArray(32)
        SecureRandom().nextBytes(privKey)
        return privKey to getPubKey(privKey)
    }

    fun getPubKey(privKey: ByteArray): ByteArray {
        return secp256k1.pubkeyCreate(privKey).sliceArray(1..32) // Nostr uses x-only 32-byte pubkeys
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun toNsec(privKey: ByteArray): String {
        return Bech32.encode("nsec", privKey)
    }

    fun toNpub(pubKey: ByteArray): String {
        return Bech32.encode("npub", pubKey)
    }

    fun normalizePubkey(pubkey: String): String {
        return if (pubkey.startsWith("npub")) {
            try {
                bytesToHex(Bech32.decode(pubkey).second)
            } catch (e: Exception) {
                pubkey
            }
        } else {
            pubkey.lowercase()
        }
    }

    fun nsecToPrivKey(nsec: String): ByteArray {
        val (hrp, data) = Bech32.decode(nsec)
        if (hrp != "nsec") throw Exception("Invalid nsec")
        return data
    }

    fun sign(data: ByteArray, privKey: ByteArray): ByteArray {
        return secp256k1.signSchnorr(data, privKey, null)
    }

    fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    fun computeSharedSecret(privKey: ByteArray, pubKey: ByteArray): ByteArray {
        return try {
            // For Nostr NIP-44, we need the X-coordinate of the shared point.
            // We ensure the pubKey is in a valid compressed format (33 bytes) 
            // by prepending 0x02 if it's a 32-byte X-only pubkey.
            val fullPubKey = if (pubKey.size == 32) {
                byteArrayOf(0x02) + pubKey
            } else {
                pubKey
            }
            secp256k1.pubKeyTweakMul(fullPubKey, privKey).sliceArray(1..32)
        } catch (e: Exception) {
            android.util.Log.e("KianKeys", "Shared secret calculation failed", e)
            throw e
        }
    }

    fun computeEventId(
        pubkey: String,
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String
    ): String {
        val tagsJson = buildJsonArray {
            tags.forEach { tag ->
                addJsonArray {
                    tag.forEach { add(it) }
                }
            }
        }
        val eventJson = buildJsonArray {
            add(0)
            add(pubkey)
            add(createdAt)
            add(kind)
            add(tagsJson)
            add(content)
        }
        // Use a minified, deterministic JSON for ID computation
        val jsonMinified = Json { 
            encodeDefaults = true
            prettyPrint = false
        }
        val serialized = jsonMinified.encodeToString(JsonArray.serializer(), eventJson)
        return bytesToHex(sha256(serialized.toByteArray()))
    }

    fun parseAssetRef(assetRef: String): ParsedAsset? {
        val parts = assetRef.split(":")
        if (parts.size < 3) return null
        return ParsedAsset(parts[1], parts[2])
    }

    data class ParsedAsset(val producer: String, val assetId: String)
}
