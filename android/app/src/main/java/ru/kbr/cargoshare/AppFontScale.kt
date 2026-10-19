package ru.kbr.cargoshare

/** Local UI typography multiplier; multiplies, rather than replaces, Android's
 * accessibility font scale. Kept within 90–120% to protect compact map labels.
 */
internal const val APP_FONT_SCALE_KEY = "app_font_scale"
internal val APP_FONT_SCALE_OPTIONS = listOf(.9f, 1.0f, 1.1f, 1.2f)

internal fun normalizeAppFontScale(value: Float): Float =
    APP_FONT_SCALE_OPTIONS.minByOrNull { kotlin.math.abs(it - value) } ?: 1f
