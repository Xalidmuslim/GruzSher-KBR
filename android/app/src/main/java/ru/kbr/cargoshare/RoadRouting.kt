package ru.kbr.cargoshare

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Road route, with coordinates ordered as (latitude, longitude), and road distance in metres. */
internal data class RoadRoute(val distanceMeters: Double, val points: List<Pair<Double, Double>>)

/**
 * Parse OSRM's GeoJSON response. Never substitute straight-line distance for
 * an unavailable road route. Reject invalid/empty geometry and non-finite values.
 */
internal fun parseRoadRoute(json: String): RoadRoute? = runCatching {
    val body = JSONObject(json)
    if (body.optString("code") != "Ok") return@runCatching null
    val result = body.optJSONArray("routes")?.optJSONObject(0) ?: return@runCatching null
    val distance = result.optDouble("distance")
    if (!distance.isFinite() || distance < 0) return@runCatching null
    val coords = result.optJSONObject("geometry")?.optJSONArray("coordinates")
        ?: return@runCatching null
    if (coords.length() !in 2..20_000) return@runCatching null
    val path = (0 until coords.length()).map { i ->
        val coordinate = coords.optJSONArray(i) ?: return@runCatching null
        val lon = coordinate.optDouble(0)
        val lat = coordinate.optDouble(1)
        if (!lat.isFinite() || !lon.isFinite() ||
            lat !in -90.0..90.0 || lon !in -180.0..180.0)
            return@runCatching null
        lat to lon
    }
    RoadRoute(distance, path)
}.getOrNull()

/**
 * OSRM public demo endpoint is suitable for manual, low-volume tests only.
 * For a commercial fleet app, replace with operator-controlled routing service.
 * No retries / no background polling to respect rate limits.
 */
internal suspend fun fetchDemoDrivingRoute(
    from: Location, destinationLat: Double, destinationLon: Double
): RoadRoute? = withContext(Dispatchers.IO) {
    if (from.latitude !in -90.0..90.0 || from.longitude !in -180.0..180.0 ||
        destinationLat !in -90.0..90.0 || destinationLon !in -180.0..180.0) return@withContext null
    val point = { lon: Double, lat: Double ->
        String.format(Locale.US, "%.6f,%.6f", lon, lat)
    }
    val url = "https://router.project-osrm.org/route/v1/driving/" +
        point(from.longitude, from.latitude) + ";" +
        point(destinationLon, destinationLat) +
        "?overview=full&geometries=geojson&steps=false"
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000
            readTimeout = 9_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GruzSherKBR-Demo/3.7.8 (Android route preview)")
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
        val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (text.length > 1_800_000) return@withContext null
        parseRoadRoute(text)
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}
