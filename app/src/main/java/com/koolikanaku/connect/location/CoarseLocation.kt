package com.koolikanaku.connect.location

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Coarse-only location for the connect layer (extra_feature.md Section 5.2: `ACCESS_COARSE_LOCATION`,
 * never fine). Uses the framework `LocationManager` instead of Play Services location, so there is no
 * new Play Services dependency. Only a geohash (never raw coordinates) ever leaves this object.
 */
object CoarseLocation {

    /** Kochi, used when location is denied/unavailable so the owner's search never dead-ends (Section 10). */
    const val FALLBACK_AREA_LABEL = "Kochi"
    private const val FALLBACK_LAT = 9.9312
    private const val FALLBACK_LNG = 76.2673
    private const val GEOHASH_PRECISION = 7

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Best-effort last known coarse fix from any enabled provider; null if none is available. */
    fun lastKnownLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching {
            lm.allProviders
                .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    fun geohashOf(location: Location): String = geohashEncode(location.latitude, location.longitude, GEOHASH_PRECISION)

    fun fallbackGeohash(): String = geohashEncode(FALLBACK_LAT, FALLBACK_LNG, GEOHASH_PRECISION)

    /** Standard base32 geohash encoder (no external dependency needed for a coarse, local-only search). */
    private fun geohashEncode(lat: Double, lng: Double, precision: Int): String {
        val base32 = "0123456789bcdefghjkmnpqrstuvwxyz"
        var latInterval = doubleArrayOf(-90.0, 90.0)
        var lngInterval = doubleArrayOf(-180.0, 180.0)
        val sb = StringBuilder()
        var isEven = true
        var bit = 0
        var ch = 0
        while (sb.length < precision) {
            if (isEven) {
                val mid = (lngInterval[0] + lngInterval[1]) / 2
                if (lng > mid) { ch = ch or (1 shl (4 - bit)); lngInterval[0] = mid } else lngInterval[1] = mid
            } else {
                val mid = (latInterval[0] + latInterval[1]) / 2
                if (lat > mid) { ch = ch or (1 shl (4 - bit)); latInterval[0] = mid } else latInterval[1] = mid
            }
            isEven = !isEven
            if (bit < 4) bit++ else { sb.append(base32[ch]); bit = 0; ch = 0 }
        }
        return sb.toString()
    }
}
