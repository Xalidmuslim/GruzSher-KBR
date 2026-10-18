package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

class BookingFlowTest {
    @Test fun verifiedProgressionExplainsManualHandover() {
        assertEquals(0, bookingStage("PENDING"))
        assertEquals(1, bookingStage("CONFIRMED"))
        assertEquals(2, bookingStage("ACTIVE"))
        assertEquals(3, bookingStage("COMPLETED"))
        assertEquals(-1, bookingStage("CANCELLED"))
        assertEquals("Автомобиль передан (демо)", bookingStatusText("ACTIVE"))
    }

    @Test fun cancellationAllowedOnlyBeforeHandover() {
        assertTrue(canCancelBooking("PENDING"))
        assertTrue(canCancelBooking("CONFIRMED"))
        assertFalse(canCancelBooking("ACTIVE"))
        assertFalse(canCancelBooking("COMPLETED"))
        assertFalse(canCancelBooking("CANCELLED"))
        assertFalse(canCancelBooking("EXPIRED"))
    }

    @Test fun stableIntentOnRetryChangesWhenBookingDetailsChange() {
        val intent = BookingIntent("gazelle-next", "2026-10-23T12:00:00+03:00", "2026-10-23T16:00:00+03:00")
        val retry = BookingIntent("gazelle-next", "2026-10-23T12:00:00+03:00", "2026-10-23T16:00:00+03:00")
        assertEquals(intent, retry)
        assertNotEquals(intent, intent.copy(end = "2026-10-23T18:00:00+03:00"))
    }
}
