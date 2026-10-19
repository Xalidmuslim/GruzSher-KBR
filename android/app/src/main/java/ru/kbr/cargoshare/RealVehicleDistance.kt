package ru.kbr.cargoshare

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.*

/** Direct/geodesic distance; NOT driving or pedestrian route distance. */
internal fun airDistanceMeters(fromLat: Double, fromLon: Double,
    toLat: Double, toLon: Double): Double {
    if (!listOf(fromLat, fromLon, toLat, toLon).all { it.isFinite() } ||
        abs(fromLat) > 90 || abs(toLat) > 90 || abs(fromLon) > 180 || abs(toLon) > 180) {
        return Double.NaN
    }
    val earthRadius = 6_371_008.8
    val a = sin(Math.toRadians(toLat - fromLat) / 2).pow(2) +
        cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
        sin(Math.toRadians(toLon - fromLon) / 2).pow(2)
    return 2 * earthRadius * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

internal fun humanDistance(meters: Double): String = when {
    !meters.isFinite() || meters < 0 -> "Расстояние недоступно"
    meters < 1000 -> "${meters.roundToInt()} м"
    meters < 10_000 -> String.format(java.util.Locale.forLanguageTag("ru"),
        "%.1f км", meters / 1000)
    else -> "${(meters / 1000).roundToInt()} км"
}

internal fun hasPhoneLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

internal fun locationServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return false
    return runCatching {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)
}

/**
 * Uses real Android OS location with explicit permission. Cache is accepted
 * only when fresh (under two minutes); otherwise await a new fix for up to
 * 12 seconds. On failure return null, never a simulated fallback.
 *
 * The vehicle positions in this demo catalogue are illustrative.
 */
internal suspend fun currentPhoneLocation(context: Context): Location? {
    if (!hasPhoneLocationPermission(context) || !locationServiceEnabled(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    val fine = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val providers = (if (fine)
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    else listOf(LocationManager.NETWORK_PROVIDER))
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return null
    val cached = providers.mapNotNull { provider ->
        runCatching {
            @Suppress("MissingPermission")
            manager.getLastKnownLocation(provider)
        }.getOrNull()
    }.filter { System.currentTimeMillis() - it.time in 0..120_000L &&
        it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
        .maxByOrNull { it.time }
    if (cached != null) return cached

    return withTimeoutOrNull(12_000L) {
        suspendCancellableCoroutine<Location?> { continuation ->
            val listeners = mutableListOf<LocationListener>()
            try {
                providers.forEach { provider ->
                    val listener = LocationListener { fix ->
                        if (fix.latitude in -90.0..90.0 &&
                            fix.longitude in -180.0..180.0 &&
                            !continuation.isCompleted) continuation.resume(fix)
                    }
                    listeners.add(listener)
                    @Suppress("MissingPermission")
                    manager.requestLocationUpdates(provider, 0L, 0f,
                        listener, Looper.getMainLooper())
                }
            } catch (_: SecurityException) {
                if (!continuation.isCompleted) continuation.resume(null)
            } catch (_: IllegalArgumentException) {
                if (!continuation.isCompleted) continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                listeners.forEach { runCatching { manager.removeUpdates(it) } }
            }
            // Cancellation normally unregisters; also unregister on successful fix.
            continuation.invokeOnCompletion {
                listeners.forEach { runCatching { manager.removeUpdates(it) } }
            }
        }
    }
}
