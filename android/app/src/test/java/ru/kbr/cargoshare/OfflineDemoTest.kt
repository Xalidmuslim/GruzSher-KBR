package ru.kbr.cargoshare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OfflineDemoTest {
    private val start = Instant.parse("2026-11-04T12:00:00Z")
    private val end = Instant.parse("2026-11-04T16:00:00Z")

    @Test fun overlappingPendingDemoBookingBlocksSameTruck() {
        val existing = listOf(DemoRentalSlot("gazelle-next", start, end, "PENDING"))
        assertFalse(canReserveDemo(existing, "gazelle-next", start.plusSeconds(1800), end.plusSeconds(1200)))
        assertTrue(canReserveDemo(existing, "ford-transit", start.plusSeconds(1800), end))
        assertTrue(canReserveDemo(existing, "gazelle-next", end, end.plusSeconds(3600)))
    }

    @Test fun rejectInvalidDateOrDuration() {
        assertFalse(validDemoWindow(start.minusSeconds(5), end, start))
        assertFalse(validDemoWindow(start, start.plusSeconds(3600), start))
        assertFalse(validDemoWindow(start.plusSeconds(91L * 86400), end.plusSeconds(91L * 86400), start))
        assertTrue(validDemoWindow(start, end, start.minusSeconds(300)))
    }

    @Test fun cancelledAndExpiredDemoBookingsDoNotBlock() {
        for (status in listOf("CANCELLED", "EXPIRED", "COMPLETED")) {
            val previous = listOf(DemoRentalSlot("gazelle-next", start, end, status))
            assertTrue(canReserveDemo(previous, "gazelle-next", start, end))
        }
    }

    @Test fun confirmedAndActiveDemoBookingsStillBlock() {
        for (status in listOf("CONFIRMED", "ACTIVE")) {
            val previous = listOf(DemoRentalSlot("gazelle-next", start, end, status))
            assertFalse(canReserveDemo(previous, "gazelle-next", start, end))
        }
    }
}
