package com.koolikanaku.connect.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.koolikanaku.connect.logic.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Approximate location only (ACCESS_COARSE_LOCATION). No play-services library is needed. */
object CoarseLocation {

    private const val FRESH_MS = 10 * 60_000L
    private const val WAIT_MS = 8_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Null when permission is missing or no provider can give a position in time. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Geo.LatLng? = withContext(Dispatchers.Main) {
        if (!hasPermission(context)) return@withContext null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { it == LocationManager.PASSIVE_PROVIDER || lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < FRESH_MS) return@withContext last.toLatLng()

        val fix = withTimeoutOrNull(WAIT_MS) {
            providers.filter { it != LocationManager.PASSIVE_PROVIDER }.firstNotNullOfOrNull { provider ->
                runCatching { requestOne(lm, provider) }.getOrNull()
            }
        }
        (fix ?: last)?.toLatLng()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOne(lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, { it.run() }) { location -> if (cont.isActive) cont.resume(location) }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) { lm.removeUpdates(this); if (cont.isActive) cont.resume(location) }
                @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) { lm.removeUpdates(this); if (cont.isActive) cont.resume(null) }
            }
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }

    private fun Location.toLatLng() = Geo.LatLng(latitude, longitude)
}
