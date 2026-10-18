package ru.kbr.cargoshare

import java.time.Duration
import java.time.Instant

/** UI-only reservation simulator. Never represents an actual booking or access to a vehicle. */
data class DemoRentalSlot(
    val vehicleId: String,
    val startAt: Instant,
    val endAt: Instant,
    val status: String,
)

fun canReserveDemo(existing: List<DemoRentalSlot>, vehicleId: String, start: Instant, end: Instant): Boolean {
    if (!start.isBefore(end)) return false
    return existing.none { slot ->
        slot.vehicleId == vehicleId && slot.status in setOf("PENDING", "CONFIRMED", "ACTIVE") &&
            slot.startAt.isBefore(end) && slot.endAt.isAfter(start)
    }
}

fun validDemoWindow(start: Instant, end: Instant, now: Instant): Boolean {
    val length = Duration.between(start, end)
    return !start.isBefore(now) && !start.isAfter(now.plus(Duration.ofDays(90))) &&
        length >= Duration.ofHours(2) && length <= Duration.ofDays(30)
}
