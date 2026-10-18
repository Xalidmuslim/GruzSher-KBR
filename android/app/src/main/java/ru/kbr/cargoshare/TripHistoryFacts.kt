package ru.kbr.cargoshare

/**
 * Facts derived from a persisted offline-demo receipt only.
 * Old bookings without a receipt have no measured/simulated mileage history;
 * their distance or driving time must never be invented from booking duration.
 */
internal data class TripHistoryFacts(
    val plannedHours: Int,
    val addedHours: Int,
    val drivingSeconds: Long?,
    val distanceKm: Double?,
    val pausedSeconds: Long,
    val completedAtMillis: Long?,
    val pickupInspected: Boolean,
    val returnInspected: Boolean,
    val doorsLocked: Boolean,
    val assistanceRequested: Boolean,
    val incidentReported: Boolean,
    val rating: Int
)

internal fun demoTripHistoryFacts(session: LiveDemoSession?): TripHistoryFacts? {
    if (session == null || session.phase != LivePhase.RECEIPT) return null
    val validDrive = session.drivingAt > 0L &&
        session.finishedAt >= session.drivingAt
    val seconds = if (validDrive) elapsedRideSeconds(session, session.finishedAt) else null
    return TripHistoryFacts(
        plannedHours = session.durationHours.coerceAtLeast(0),
        addedHours = session.extraHours.coerceAtLeast(0),
        drivingSeconds = seconds,
        distanceKm = if (validDrive) demoDistanceKm(session, session.finishedAt) else null,
        pausedSeconds = session.pausedSeconds.coerceAtLeast(0),
        completedAtMillis = session.finishedAt.takeIf { it > 0L },
        pickupInspected = session.inspected.size >= 4,
        returnInspected = session.returnPhotoAt > 0L && session.returnConditionChecked,
        doorsLocked = session.locked,
        assistanceRequested = session.assistance,
        incidentReported = session.incident,
        rating = session.rating.coerceIn(0, 5)
    )
}

internal fun formatRideDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3_600L
    val minutes = (safe % 3_600L) / 60L
    val rem = safe % 60L
    return if (hours > 0L) "${hours} ч ${minutes} мин"
    else if (minutes > 0L) "${minutes} мин ${rem} сек"
    else "${rem} сек"
}
