package ru.kbr.cargoshare

/** A trip is active only in truck-route views, never catalogue or approach. */
internal fun shouldShowParkingZones(enabled: Boolean, mode: String, compact: Boolean): Boolean =
    enabled && !compact && mode == "грузовик"
