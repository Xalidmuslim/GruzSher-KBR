package ru.kbr.cargoshare

import kotlin.math.*

/** Illustrative coordinates only; never real device GPS or lawful parking data. */
internal const val DEMO_PARKING_ZONE_RADIUS_METERS = 85.0
private const val EARTH_RADIUS_METERS = 6_371_000.0

internal fun demoDistanceMeters(latA: Double, lonA: Double, latB: Double, lonB: Double): Double {
    val dLat = Math.toRadians(latB - latA)
    val dLon = Math.toRadians(lonB - lonA)
    val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(latA)) *
        cos(Math.toRadians(latB)) * sin(dLon / 2).pow(2.0)
    return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
}

/** Exact virtual two-point walk geometry from LiveRouteMap. */
internal fun demoWalkRemainingMeters(lat: Double, lon: Double, fraction: Float): Int {
    val progress = fraction.coerceIn(0f, 1f)
    val fromLat = lat + 0.0012
    val fromLon = lon - 0.0018
    return demoDistanceMeters(
        fromLat + (lat - fromLat) * progress,
        fromLon + (lon - fromLon) * progress, lat, lon
    ).roundToInt()
}

/** Exact virtual five-point drive route geometry from LiveRouteMap. */
internal fun demoReturnRemainingMeters(lat: Double, lon: Double, fraction: Float): Int {
    val pts = listOf(lat to lon, (lat + .0010) to (lon + .0008),
        (lat + .0020) to (lon + .0020), (lat + .0032) to (lon + .0024),
        (lat + .0044) to (lon + .0027))
    val step = fraction.coerceIn(0f, 1f) * (pts.size - 1)
    val i = step.toInt().coerceAtMost(pts.size - 2)
    val f = (step - i).coerceIn(0f, 1f)
    val pLat = pts[i].first + (pts[i + 1].first - pts[i].first) * f
    val pLon = pts[i].second + (pts[i + 1].second - pts[i].second) * f
    return demoDistanceMeters(pLat, pLon, pts.last().first, pts.last().second).roundToInt()
}
