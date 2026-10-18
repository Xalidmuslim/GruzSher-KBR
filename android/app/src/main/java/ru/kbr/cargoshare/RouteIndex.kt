package ru.kbr.cargoshare

/**
 * The walking demo uses two map points and the driving demo uses five.
 * Never use a fixed route[2]: on a two-point path that crashes the Android app.
 */
internal fun centerIndexForRoute(pointCount: Int): Int {
    require(pointCount > 0) { "A route must contain at least one point" }
    return pointCount / 2
}
