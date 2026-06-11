package com.ely.kian.crypto

import java.io.ByteArrayOutputStream

object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    fun encode(hrp: String, data: ByteArray): String {
        val converted = convertBits(data, 8, 5, true)
        return encodeRaw(hrp, converted)
    }

    fun decode(bech32: String): Pair<String, ByteArray> {
        val (hrp, dataWithChecksum) = decodeRaw(bech32)
        if (dataWithChecksum.size < 6) throw Exception("Data too short")
        if (!verifyChecksum(hrp, dataWithChecksum)) throw Exception("Invalid checksum")
        val data = dataWithChecksum.copyOfRange(0, dataWithChecksum.size - 6)
        return hrp to convertBits(data, 5, 8, false)
    }

    private fun encodeRaw(hrp: String, values: ByteArray): String {
        val checksum = createChecksum(hrp, values)
        val combined = values + checksum
        val sb = StringBuilder(hrp.length + 1 + combined.size)
        sb.append(hrp).append('1')
        for (b in combined) {
            sb.append(CHARSET[b.toInt()])
        }
        return sb.toString()
    }

    private fun decodeRaw(bech32: String): Pair<String, ByteArray> {
        val pos = bech32.lastIndexOf('1')
        if (pos < 1 || pos + 7 > bech32.length) throw Exception("Invalid separator position")
        val hrp = bech32.substring(0, pos).lowercase()
        val data = ByteArray(bech32.length - pos - 1)
        for (i in 0 until data.size) {
            val char = bech32[pos + 1 + i].lowercaseChar()
            val idx = CHARSET.indexOf(char)
            if (idx == -1) throw Exception("Invalid character: $char")
            data[i] = idx.toByte()
        }
        return hrp to data
    }

    private fun verifyChecksum(hrp: String, data: ByteArray): Boolean {
        return polymod(hrpExpand(hrp) + data) == 1
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val out = ByteArrayOutputStream()
        val maxv = (1 shl toBits) - 1
        for (value in data) {
            val v = value.toInt() and 0xff
            acc = (acc shl fromBits) or v
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                out.write((acc shr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) {
                out.write((acc shl (toBits - bits)) and maxv)
            }
        } else if (bits >= fromBits || (acc shl (toBits - bits)) and maxv != 0) {
            throw Exception("Invalid padding")
        }
        return out.toByteArray()
    }

    private fun createChecksum(hrp: String, data: ByteArray): ByteArray {
        val values = hrpExpand(hrp) + data + ByteArray(6)
        val poly = polymod(values) xor 1
        val checksum = ByteArray(6)
        for (i in 0 until 6) {
            checksum[i] = ((poly ushr (5 * (5 - i))) and 31).toByte()
        }
        return checksum
    }

    private fun hrpExpand(hrp: String): ByteArray {
        val res = ByteArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            res[i] = (hrp[i].code shr 5).toByte()
            res[i + hrp.length + 1] = (hrp[i].code and 31).toByte()
        }
        res[hrp.length] = 0
        return res
    }

    private fun polymod(values: ByteArray): Int {
        val generators = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor (v.toInt() and 0xff)
            for (i in 0 until 5) {
                if ((top ushr i) and 1 != 0) {
                    chk = chk xor generators[i]
                }
            }
        }
        return chk
    }
}
