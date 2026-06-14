package com.ely.kian.util

object Geohash {
    private val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private val BITS = intArrayOf(16, 8, 4, 2, 1)

    fun encode(lat: Double, lon: Double, precision: Int = 9): String {
        val geohash = StringBuilder()
        var minLat = -90.0
        var maxLat = 90.0
        var minLon = -180.0
        var maxLon = 180.0
        var bit = 0
        var ch = 0
        var isEven = true

        while (geohash.length < precision) {
            val mid: Double
            if (isEven) {
                mid = (minLon + maxLon) / 2
                if (lon > mid) {
                    ch = ch or BITS[bit]
                    minLon = mid
                } else {
                    maxLon = mid
                }
            } else {
                mid = (minLat + maxLat) / 2
                if (lat > mid) {
                    ch = ch or BITS[bit]
                    minLat = mid
                } else {
                    maxLat = mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                geohash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return geohash.toString()
    }

    fun decode(geohash: String): Pair<Double, Double> {
        var minLat = -90.0
        var maxLat = 90.0
        var minLon = -180.0
        var maxLon = 180.0
        var isEven = true

        for (i in geohash.indices) {
            val ch = BASE32.indexOf(geohash[i])
            for (j in 0..4) {
                val mask = BITS[j]
                if (isEven) {
                    val mid = (minLon + maxLon) / 2
                    if ((ch and mask) != 0) {
                        minLon = mid
                    } else {
                        maxLon = mid
                    }
                } else {
                    val mid = (minLat + maxLat) / 2
                    if ((ch and mask) != 0) {
                        minLat = mid
                    } else {
                        maxLat = mid
                    }
                }
                isEven = !isEven
            }
        }
        return Pair((minLat + maxLat) / 2, (minLon + maxLon) / 2)
    }
}
