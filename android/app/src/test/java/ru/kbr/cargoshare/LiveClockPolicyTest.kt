package ru.kbr.cargoshare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveClockPolicyTest {
    @Test fun idleScreensHaveNoPeriodicRecomposition() {
        listOf(LivePhase.CHOOSE, LivePhase.PHONE, LivePhase.CONFIRM,
            LivePhase.INSPECT, LivePhase.READY, LivePhase.RECEIPT).forEach {
            assertFalse("No per-second work expected for $it", needsLiveClock(it))
        }
    }
    @Test fun automaticAndMotionPhasesKeepAClock() {
        listOf(LivePhase.DOCUMENTS, LivePhase.PAYMENT, LivePhase.APPROVAL,
            LivePhase.WALK, LivePhase.UNLOCK, LivePhase.DRIVING,
            LivePhase.RETURNING, LivePhase.RETURN).forEach {
            assertTrue("Timer required for $it", needsLiveClock(it))
        }
    }
}
