package ru.kbr.cargoshare

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/** Locally editable TRAINING zones. Never authoritative permission to park. */
internal enum class ParkingRule { ALLOWED, FORBIDDEN }
internal data class ZonePoint(val latitude: Double, val longitude: Double)
internal data class LocalParkingZone(
    val id: String,
    val title: String,
    val rule: ParkingRule,
    val vertices: List<ZonePoint>,
    val enabled: Boolean = true
)
internal const val LOCAL_ZONES_KEY = "local_admin_parking_zones_v1"

internal fun validZonePoints(points: List<ZonePoint>): Boolean {
    if (points.size !in 3..40) return false
    if (points.any { !it.latitude.isFinite() || !it.longitude.isFinite()
            || it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 }) return false
    // Reject degenerate (collinear) areas. Very small zoning patches are also
    // unreliable to draw/tap at typical smartphone zoom levels.
    var area = 0.0
    for (i in points.indices) {
        val a = points[i]; val b = points[(i + 1) % points.size]
        area += a.longitude * b.latitude - b.longitude * a.latitude
    }
    return abs(area) > 1e-9
}

/** Point-in-polygon; forbidden rule always wins over allowed on overlap. */
internal fun containsGeoPoint(vertices: List<ZonePoint>, location: ZonePoint): Boolean {
    if (vertices.size < 3) return false
    var inside = false
    var j = vertices.lastIndex
    for (i in vertices.indices) {
        val a = vertices[i]; val b = vertices[j]
        if ((a.latitude > location.latitude) != (b.latitude > location.latitude) &&
            (location.longitude < (b.longitude - a.longitude) *
                (location.latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude)) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * Null means admin has not configured any matching zone; it is not permission.
 * false means a matching prohibition or no matching permitted area when
 * locally-configured active zones exist.
 */
internal fun localReturnRule(zones: List<LocalParkingZone>, at: ZonePoint): Boolean? {
    val active = zones.filter { it.enabled && validZonePoints(it.vertices) }
    if (active.isEmpty()) return null
    if (active.any { it.rule == ParkingRule.FORBIDDEN &&
            containsGeoPoint(it.vertices, at) }) return false
    return active.any { it.rule == ParkingRule.ALLOWED &&
        containsGeoPoint(it.vertices, at) }
}

internal class LocalParkingZoneStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "gruzsher_preferences", Context.MODE_PRIVATE)

    fun load(): List<LocalParkingZone> {
        val raw = prefs.getString(LOCAL_ZONES_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<LocalParkingZone>()
            for (i in 0 until minOf(array.length(), 40)) {
                val item = array.optJSONObject(i) ?: continue
                val v = item.optJSONArray("vertices") ?: continue
                val points = (0 until minOf(v.length(), 40)).mapNotNull { index ->
                    val point = v.optJSONArray(index) ?: return@mapNotNull null
                    if (point.length() < 2) return@mapNotNull null
                    ZonePoint(point.optDouble(0, Double.NaN),
                        point.optDouble(1, Double.NaN))
                }
                if (!validZonePoints(points)) continue
                val rule = runCatching {
                    ParkingRule.valueOf(item.optString("rule", ""))
                }.getOrNull() ?: continue
                result += LocalParkingZone(
                    item.optString("id", UUID.randomUUID().toString()),
                    item.optString("title", "Зона №${i + 1}").take(60), rule,
                    points, item.optBoolean("enabled", true))
            }
            result.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    fun save(zones: List<LocalParkingZone>): Boolean {
        if (zones.size > 30 || zones.any { !validZonePoints(it.vertices) ||
                it.title.isBlank() || it.title.length > 60 }) return false
        val array = JSONArray()
        for (z in zones) {
            val vertices = JSONArray()
            z.vertices.forEach { p ->
                vertices.put(JSONArray().put(p.latitude).put(p.longitude))
            }
            array.put(JSONObject().put("id", z.id).put("title", z.title)
                .put("rule", z.rule.name).put("enabled", z.enabled)
                .put("vertices", vertices))
        }
        return prefs.edit().putString(LOCAL_ZONES_KEY, array.toString()).commit()
    }
}

@Composable
internal fun rememberLocalParkingZones(): List<LocalParkingZone> {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { LocalParkingZoneStore(context) }
    val prefs = remember(context) {
        context.getSharedPreferences("gruzsher_preferences", Context.MODE_PRIVATE)
    }
    var zones by remember(store) { mutableStateOf(store.load()) }
    DisposableEffect(prefs, store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LOCAL_ZONES_KEY) zones = store.load()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return zones
}
