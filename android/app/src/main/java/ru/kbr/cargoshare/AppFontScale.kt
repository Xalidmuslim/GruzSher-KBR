package ru.kbr.cargoshare

/** Local UI typography multiplier; multiplies, rather than replaces, Android's
 * accessibility font scale. Controls 100–150%; UI components must remain scrollable at 150%.
 */
internal const val APP_FONT_SCALE_KEY = "app_font_scale"
internal val APP_FONT_SCALE_OPTIONS = listOf(1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f)

internal fun normalizeAppFontScale(value: Float): Float =
    if (!value.isFinite()) 1.1f
    else APP_FONT_SCALE_OPTIONS.minByOrNull { kotlin.math.abs(it - value) } ?: 1f
