package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

class LiveDemoEngineTest {
    private val base = 1_800_000_000_000L
    private fun action(s: LiveDemoSession, a: LiveEvent, now: Long, value: String = ""): LiveDemoSession {
        val result = applyLive(s, a, now, value)
        assertTrue("Rejected ${a.name}: ${result.message}", result.accepted)
        return result.state
    }
    private fun prepare(): LiveDemoSession {
        var s = action(LiveDemoSession(), LiveEvent.PICK_CAR, base, "ford-transit")
        s = action(s, LiveEvent.REQUEST_CODE, base + 100L, "+79000000000")
        assertFalse(applyLive(s, LiveEvent.VERIFY_CODE, base + 2000L, "2468").accepted)
        s = action(s, LiveEvent.VERIFY_CODE, base + 3200L, "2468")
        assertEquals(LivePhase.DOCUMENTS, s.phase)
        s = action(s, LiveEvent.SCAN_PASSPORT, base + 3300L)
        s = action(s, LiveEvent.SCAN_LICENCE, base + 3400L)
        s = advanceLive(s, base + 6000L)
        assertEquals(LivePhase.PAYMENT, s.phase)
        s = action(s, LiveEvent.START_PAYMENT, base + 6100L)
        s = advanceLive(s, base + 8700L)
        assertEquals(LivePhase.CONFIRM, s.phase)
        return s
    }

    @Test fun phoneCodeIsDelayedAndActuallyValidated() {
        var s = action(LiveDemoSession(), LiveEvent.PICK_CAR, base, "gazelle-next")
        assertFalse(applyLive(s, LiveEvent.REQUEST_CODE, base, "1234").accepted)
        s = action(s, LiveEvent.REQUEST_CODE, base + 1L, "+79000000000")
        assertFalse(applyLive(s, LiveEvent.VERIFY_CODE, base + 3010L, "1111").accepted)
        assertEquals(LivePhase.DOCUMENTS, action(s, LiveEvent.VERIFY_CODE, base + 3010L, "2468").phase)
    }

    @Test fun cannotBookBeforeVerifyingIdentityAndCard() {
        val s = action(LiveDemoSession(), LiveEvent.PICK_CAR, base, "gazelle-next")
        assertFalse(applyLive(s, LiveEvent.REQUEST_BOOKING, base + 1000L).accepted)
        assertEquals(LivePhase.CONFIRM, prepare().phase)
    }

    @Test fun bookingConfirmationArrivalAndInspectionAreTimed() {
        var s = action(prepare(), LiveEvent.REQUEST_BOOKING, base + 9000L)
        assertEquals(LivePhase.APPROVAL, s.phase)
        assertEquals(LivePhase.APPROVAL, advanceLive(s, base + 12999L).phase)
        s = advanceLive(s, base + 13000L)
        assertEquals(LivePhase.WALK, s.phase)
        assertEquals(LivePhase.WALK, advanceLive(s, base + 24999L).phase)
        s = advanceLive(s, base + 25001L)
        assertEquals(LivePhase.INSPECT, s.phase)
        assertFalse(applyLive(s, LiveEvent.UNLOCK, base + 25002L).accepted)
        for (angle in listOf("Спереди", "Сзади", "Слева", "Справа"))
            s = action(s, LiveEvent.CAPTURE_ANGLE, base + 25003L, angle)
        s = action(s, LiveEvent.UNLOCK, base + 25005L)
        assertEquals(LivePhase.UNLOCK, s.phase)
        assertEquals(LivePhase.UNLOCK, advanceLive(s, base + 27504L).phase)
        s = advanceLive(s, base + 27505L)
        assertEquals(LivePhase.READY, s.phase)
        s = action(s, LiveEvent.START_DRIVE, base + 27600L)
        assertEquals(LivePhase.DRIVING, s.phase)
    }

    @Test fun routeMovesWithClockAndWaitModeStopsProgress() {
        var s = LiveDemoSession(phase = LivePhase.DRIVING, drivingAt = base)
        assertEquals(0f, routeFraction(s, base), 0.001f)
        assertEquals(0.4f, routeFraction(s, base + 30_000L), 0.001f)
        s = action(s, LiveEvent.TOGGLE_WAIT, base + 30_000L)
        assertEquals(0.4f, routeFraction(s, base + 60_000L), 0.001f)
        s = action(s, LiveEvent.TOGGLE_WAIT, base + 60_000L)
        assertEquals(0.4f, routeFraction(s, base + 60_000L), 0.001f)
        assertEquals(1f, routeFraction(s, base + 105_000L), 0.001f)
        assertEquals(LivePhase.RETURN, advanceLive(s, base + 105_000L).phase)
    }

    @Test fun returnRequiresPhotosAndDoorLockAcknowledgement() {
        var s = LiveDemoSession(phase = LivePhase.RETURN, drivingAt = base, bookingId = "SIM-42")
        assertFalse(applyLive(s, LiveEvent.FINISH, base + 75_000L).accepted)
        s = action(s, LiveEvent.TAKE_RETURN_PHOTO, base + 76_000L)
        assertFalse(applyLive(s, LiveEvent.LOCK_DOORS, base + 77_000L).accepted)
        s = action(s, LiveEvent.LOCK_DOORS, base + 78_500L)
        assertFalse(applyLive(s, LiveEvent.FINISH, base + 80_000L).accepted)
        s = action(s, LiveEvent.FINISH, base + 81_000L)
        assertEquals(LivePhase.RECEIPT, s.phase)
        assertTrue(s.locked)
    }

    @Test fun financialDisplayIsExplicitlyVirtual() {
        val s = LiveDemoSession(phase = LivePhase.DRIVING, durationHours = 2,
            drivingAt = base, extraHours = 2)
        assertEquals(4 * 650, demoFare(s, 650, base))
        assertTrue(demoFare(s, 650, base + 31_000L) > 4 * 650)
    }

    @Test fun invalidOperationsDoNotModifySession() {
        val s = LiveDemoSession()
        for (event in listOf(LiveEvent.UNLOCK, LiveEvent.START_DRIVE,
            LiveEvent.LOCK_DOORS, LiveEvent.FINISH)) {
            val r = applyLive(s, event, base)
            assertFalse(r.accepted)
            assertEquals(s, r.state)
        }
    }
}
