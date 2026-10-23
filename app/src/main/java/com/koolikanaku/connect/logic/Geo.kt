package com.koolikanaku.connect.logic

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Geohash and distance helpers. Workers store only a coarse geohash, never exact coordinates. */
object Geo {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    /** Precision 6 is a cell of about 1.2 km x 0.6 km: coarse enough to hide a house. */
    const val STORED_PRECISION = 6
    /** Precision 5 is a cell of about 4.9 km x 4.9 km, used to find candidates. */
    const val QUERY_PRECISION = 5

    data class LatLng(val lat: Double, val lng: Double)
    private data class Bounds(val latMin: Double, val latMax: Double, val lngMin: Double, val lngMax: Double) {
        val center get() = LatLng((latMin + latMax) / 2, (lngMin + lngMax) / 2)
        val latSpan get() = latMax - latMin
        val lngSpan get() = lngMax - lngMin
    }

    fun encode(lat: Double, lng: Double, precision: Int = STORED_PRECISION): String {
        require(lat in -90.0..90.0 && lng in -180.0..180.0) { "Invalid coordinates" }
        var latMin = -90.0; var latMax = 90.0; var lngMin = -180.0; var lngMax = 180.0
        val sb = StringBuilder()
        var evenBit = true
        var bit = 0; var ch = 0
        while (sb.length < precision) {
            if (evenBit) {
                val mid = (lngMin + lngMax) / 2
                if (lng >= mid) { ch = (ch shl 1) or 1; lngMin = mid } else { ch = ch shl 1; lngMax = mid }
            } else {
                val mid = (latMin + latMax) / 2
                if (lat >= mid) { ch = (ch shl 1) or 1; latMin = mid } else { ch = ch shl 1; latMax = mid }
            }
            evenBit = !evenBit
            if (++bit == 5) { sb.append(BASE32[ch]); bit = 0; ch = 0 }
        }
        return sb.toString()
    }

    private fun bounds(hash: String): Bounds {
        var latMin = -90.0; var latMax = 90.0; var lngMin = -180.0; var lngMax = 180.0
        var evenBit = true
        for (c in hash) {
            val idx = BASE32.indexOf(c)
            require(idx >= 0) { "Invalid geohash character '$c'" }
            for (n in 4 downTo 0) {
                val bitValue = (idx shr n) and 1
                if (evenBit) {
                    val mid = (lngMin + lngMax) / 2
                    if (bitValue == 1) lngMin = mid else lngMax = mid
                } else {
                    val mid = (latMin + latMax) / 2
                    if (bitValue == 1) latMin = mid else latMax = mid
                }
                evenBit = !evenBit
            }
        }
        return Bounds(latMin, latMax, lngMin, lngMax)
    }

    /** The middle of the cell. This is as precise as a stored worker location ever gets. */
    fun decodeCenter(hash: String): LatLng = bounds(hash).center

    /** The cell holding [hash] plus its 8 neighbours, so a search near a cell edge still finds everyone. */
    fun cellAndNeighbors(hash: String): List<String> {
        val b = bounds(hash)
        val c = b.center
        val result = LinkedHashSet<String>()
        for (dLat in -1..1) for (dLng in -1..1) {
            var lat = c.lat + dLat * b.latSpan
            var lng = c.lng + dLng * b.lngSpan
            if (lat > 90.0 || lat < -90.0) continue          // no cell beyond a pole
            if (lng > 180.0) lng -= 360.0                     // wrap across the antimeridian
            if (lng < -180.0) lng += 360.0
            result.add(encode(lat, lng, hash.length))
        }
        return result.toList()
    }

    /** Candidate geohash prefixes for a search around a point. */
    fun searchPrefixes(lat: Double, lng: Double): List<String> = cellAndNeighbors(encode(lat, lng, QUERY_PRECISION))

    fun distanceKm(a: LatLng, b: LatLng): Double {
        val r = 6371.0088
        val dLat = (b.lat - a.lat) * PI / 180
        val dLng = (b.lng - a.lng) * PI / 180
        val h = sin(dLat / 2).pow(2) + cos(a.lat * PI / 180) * cos(b.lat * PI / 180) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /** What an owner sees. A worker's exact distance is never shown, only a band. */
    fun distanceBand(km: Double): String = when {
        km < 1.0 -> "under 1 km"
        else -> "about ${km.roundToInt()} km"
    }
}
