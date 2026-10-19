package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

class ReturningDemoDriverTest {
    private val now = 1_800_000_000_000L

    @Test fun firstRentalStillRequiresPhoneDocumentsAndVirtualPayment() {
        val first = applyLive(LiveDemoSession(), LiveEvent.PICK_CAR, now, "ford-transit")
        assertTrue(first.accepted)
        assertEquals(LivePhase.PHONE, first.state.phase)
        assertFalse(first.state.returningDemoDriver)
    }

    @Test fun onlySuccessfulFinishedRentalUnlocksFastRepeat() {
        val cancelled = applyLive(LiveDemoSession(phase = LivePhase.RETURN, bookingId = "x"),
            LiveEvent.FINISH, now)
        assertFalse(cancelled.accepted)
        val readyToFinish = LiveDemoSession(phase = LivePhase.RETURN,
            bookingId = "x", returnPhotoAt = now - 7000L, lockingAt = now - 4000L,
            returnConditionChecked = true)
        val finished = applyLive(readyToFinish, LiveEvent.FINISH, now)
        assertTrue(finished.accepted)
        assertEquals(LivePhase.RECEIPT, finished.state.phase)
        assertTrue(finished.state.returningDemoDriver)
        val reset = applyLive(finished.state, LiveEvent.RESET, now + 1000L).state
        assertTrue(reset.returningDemoDriver)
        assertEquals(LivePhase.CHOOSE, reset.phase)
        val again = applyLive(reset, LiveEvent.PICK_CAR, now + 1100L, "largus-van")
        assertTrue(again.accepted)
        assertEquals(LivePhase.CONFIRM, again.state.phase)
        assertFalse(again.state.termsAccepted)
        assertFalse(applyLive(again.state, LiveEvent.REQUEST_BOOKING, now + 1200L).accepted)
        val terms = applyLive(again.state, LiveEvent.ACCEPT_TERMS, now + 1300L, "true").state
        assertEquals(LivePhase.WALK,
            applyLive(terms, LiveEvent.REQUEST_BOOKING, now + 1400L).state.phase)
    }
}
