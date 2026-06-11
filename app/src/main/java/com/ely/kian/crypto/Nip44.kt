package com.ely.kian.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ln
import kotlin.math.pow

object Nip44 {
    private const val VERSION = 2
    private val CONVERSATION_SALT = "nip44-v2".toByteArray()

    fun encrypt(plaintext: String, senderPrivKey: ByteArray, recipientPubKey: ByteArray): String {
        val sharedSecret = KianKeys.computeSharedSecret(senderPrivKey, recipientPubKey)
        val conversationKey = hkdfExtract(sharedSecret, CONVERSATION_SALT)
        
        val nonce = ByteArray(32)
        java.security.SecureRandom().nextBytes(nonce)
        
        val keys = getMessageKeys(conversationKey, nonce)
        val padded = pad(plaintext)
        
        val cipher = Cipher.getInstance("ChaCha20")
        val keySpec = SecretKeySpec(keys.chachaKey, "ChaCha20")
        // NIP-44 uses 12-byte nonce for ChaCha20
        val ivSpec = IvParameterSpec(keys.chachaNonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        
        val ciphertext = cipher.doFinal(padded)
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(keys.hmacKey, "HmacSHA256"))
        hmac.update(nonce)
        val mac = hmac.doFinal(ciphertext)
        
        val result = ByteBuffer.allocate(1 + nonce.size + ciphertext.size + mac.size)
        result.put(VERSION.toByte())
        result.put(nonce)
        result.put(ciphertext)
        result.put(mac)
        
        return Base64.encodeToString(result.array(), Base64.NO_WRAP)
    }

    fun decrypt(payload: String, recipientPrivKey: ByteArray, senderPubKey: ByteArray): String {
        val data = Base64.decode(payload, Base64.NO_WRAP)
        if (data[0].toInt() != VERSION) throw Exception("Unsupported NIP-44 version")
        
        val nonce = data.sliceArray(1..32)
        val mac = data.sliceArray(data.size - 32 until data.size)
        val ciphertext = data.sliceArray(33 until data.size - 32)
        
        val sharedSecret = KianKeys.computeSharedSecret(recipientPrivKey, senderPubKey)
        val conversationKey = hkdfExtract(sharedSecret, CONVERSATION_SALT)
        val keys = getMessageKeys(conversationKey, nonce)
        
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(keys.hmacKey, "HmacSHA256"))
        hmac.update(nonce)
        val calculatedMac = hmac.doFinal(ciphertext)
        
        if (!calculatedMac.contentEquals(mac)) throw Exception("Invalid NIP-44 MAC")
        
        val cipher = Cipher.getInstance("ChaCha20")
        val keySpec = SecretKeySpec(keys.chachaKey, "ChaCha20")
        val ivSpec = IvParameterSpec(keys.chachaNonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        
        val padded = cipher.doFinal(ciphertext)
        return unpad(padded)
    }

    private data class MessageKeys(val chachaKey: ByteArray, val chachaNonce: ByteArray, val hmacKey: ByteArray)

    private fun getMessageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        val keys = hkdfExpand(conversationKey, nonce, 76)
        return MessageKeys(
            chachaKey = keys.sliceArray(0..31),
            chachaNonce = keys.sliceArray(32..43),
            hmacKey = keys.sliceArray(44..75)
        )
    }

    private fun hkdfExtract(ikm: ByteArray, salt: ByteArray): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(salt, "HmacSHA256"))
        return hmac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        val okm = ByteArray(length)
        var t = ByteArray(0)
        var i = 1
        var pos = 0
        while (pos < length) {
            hmac.update(t)
            hmac.update(info)
            hmac.update(i.toByte())
            t = hmac.doFinal()
            val copyLen = minOf(t.size, length - pos)
            System.arraycopy(t, 0, okm, pos, copyLen)
            pos += copyLen
            i++
        }
        return okm
    }

    private fun pad(plaintext: String): ByteArray {
        val unpadded = plaintext.toByteArray()
        val len = unpadded.size
        if (len < 1 || len > 65535) throw Exception("Invalid plaintext length")
        
        val paddedLen = calcPaddedLength(len)
        val result = ByteArray(2 + paddedLen)
        result[0] = (len shr 8).toByte()
        result[1] = (len and 0xFF).toByte()
        System.arraycopy(unpadded, 0, result, 2, len)
        return result
    }

    private fun unpad(padded: ByteArray): String {
        val len = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
        if (len < 1 || len > padded.size - 2) throw Exception("Invalid padding")
        val plaintext = ByteArray(len)
        System.arraycopy(padded, 2, plaintext, 0, len)
        return String(plaintext)
    }

    private fun calcPaddedLength(len: Int): Int {
        if (len <= 32) return 32
        val nextPower = 1 shl (kotlin.math.floor(kotlin.math.log2(len - 1f)) + 1).toInt()
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * (kotlin.math.floor((len - 1f) / chunk).toInt() + 1)
    }
}
