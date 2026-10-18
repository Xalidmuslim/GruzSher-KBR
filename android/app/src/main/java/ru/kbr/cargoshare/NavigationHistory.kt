package ru.kbr.cargoshare

/** Routes 0..3 = bottom tabs, 4 = LIVE nested in More.
 * Choosing the fleet home resets the history; all other routes follow visits.
 */
internal fun pushNavigationRoute(history: List<Int>, current: Int, destination: Int): List<Int> {
    if (destination == 0) return emptyList()
    if (current == destination) return history
    return (history + current).takeLast(24)
}
