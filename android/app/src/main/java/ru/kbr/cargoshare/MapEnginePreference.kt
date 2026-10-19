package ru.kbr.cargoshare

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal const val MAP_ENGINE_KEY = "map_engine"
internal const val MAP_ENGINE_OSMDROID = "osmdroid"
internal const val MAP_ENGINE_MAPLIBRE = "maplibre"

/** Unknown or old preference values always fall back to the proven OSMDroid map. */
internal fun normalizeMapEngine(value: String?): String =
    if (value == MAP_ENGINE_MAPLIBRE) MAP_ENGINE_MAPLIBRE else MAP_ENGINE_OSMDROID

internal fun savedMapEngine(context: Context): String =
    normalizeMapEngine(context.applicationContext
        .getSharedPreferences("gruzsher_preferences", 0)
        .getString(MAP_ENGINE_KEY, MAP_ENGINE_OSMDROID))

@Composable
internal fun rememberMapEngine(): String {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences("gruzsher_preferences", 0)
    }
    var selected by remember(preferences) {
        mutableStateOf(normalizeMapEngine(
            preferences.getString(MAP_ENGINE_KEY, MAP_ENGINE_OSMDROID)))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == MAP_ENGINE_KEY) selected = normalizeMapEngine(
                prefs.getString(MAP_ENGINE_KEY, MAP_ENGINE_OSMDROID))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return selected
}
