package ru.kbr.cargoshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripHistoryFactsTest {
    @Test fun completedSessionExposesStoredDemoMetrics() {
        val receipt = LiveDemoSession(
            phase = LivePhase.RECEIPT, durationHours = 2, extraHours = 1,
            drivingAt = 1_000L, finishedAt = 76_000L,
            inspected = setOf("front","rear","left","right"),
            returnConditionChecked = true, returnPhotoAt = 73_000L,
            locked = true, rating = 5)
        val summary = demoTripHistoryFacts(receipt)!!
        assertEquals(75L, summary.drivingSeconds)
        assertEquals(0.6, summary.distanceKm!!, 0.0001)
        assertEquals(2, summary.plannedHours)
        assertEquals(1, summary.addedHours)
        assertTrue(summary.pickupInspected)
        assertTrue(summary.returnInspected)
        assertTrue(summary.doorsLocked)
        assertEquals("1 мин 15 сек", formatRideDuration(summary.drivingSeconds!!))
    }

    @Test fun missingOrIncompleteRideNeverInventsDistance() {
        assertNull(demoTripHistoryFacts(null))
        assertNull(demoTripHistoryFacts(LiveDemoSession(phase = LivePhase.DRIVING)))
        val legacy = demoTripHistoryFacts(LiveDemoSession(phase = LivePhase.RECEIPT))!!
        assertNull(legacy.drivingSeconds)
        assertNull(legacy.distanceKm)
    }
}
