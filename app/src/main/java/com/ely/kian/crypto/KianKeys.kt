package com.ely.kian.crypto

import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toSeed
import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom

object KianKeys {
    private val secp256k1 = Secp256k1.get()

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

    fun getPubKey(privKey: ByteArray): ByteArray {
        return secp256k1.pubkeyCreate(privKey).sliceArray(1..32) // Nostr uses x-only 32-byte pubkeys
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
