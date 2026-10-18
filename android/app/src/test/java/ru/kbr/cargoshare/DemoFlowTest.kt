package ru.kbr.cargoshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoFlowTest {
    private fun ready(): DemoState = listOf(
        DemoAction.SMS_LOGIN, DemoAction.VERIFY_DOCUMENTS, DemoAction.LINK_PAYMENT
    ).fold(DemoState()) { state, action -> performDemoAction(state, action).state }

    @Test fun startRequiresApprovalInspectionAndOpenDoors() {
        var s = chooseDemoBooking(ready(), "ride-1", "PENDING").state
        assertFalse(performDemoAction(s, DemoAction.START_RIDE).success)
        s = performDemoAction(s, DemoAction.ADMIN_APPROVE).state
        assertFalse(performDemoAction(s, DemoAction.START_RIDE).success)
        s = performDemoAction(s, DemoAction.OPEN_DOORS).state
        s = performDemoAction(s, DemoAction.BEFORE_PHOTOS).state
        val started = performDemoAction(s, DemoAction.START_RIDE)
        assertTrue(started.success)
        assertEquals("ACTIVE", started.state.tripStatus)
    }

    @Test fun approvalRequiresFakeAccountDocsAndPayment() {
        var s = chooseDemoBooking(DemoState(), "ride-1", "PENDING").state
        assertFalse(performDemoAction(s, DemoAction.ADMIN_APPROVE).success)
        s = performDemoAction(s, DemoAction.SMS_LOGIN).state
        assertFalse(performDemoAction(s, DemoAction.ADMIN_APPROVE).success)
        s = performDemoAction(s, DemoAction.VERIFY_DOCUMENTS).state
        assertFalse(performDemoAction(s, DemoAction.ADMIN_APPROVE).success)
        s = performDemoAction(s, DemoAction.LINK_PAYMENT).state
        assertTrue(performDemoAction(s, DemoAction.ADMIN_APPROVE).success)
    }

    @Test fun completionRequiresReturnInspectionAndLockedDoors() {
        var s = chooseDemoBooking(ready(), "ride-1", "PENDING").state
        for (a in listOf(DemoAction.ADMIN_APPROVE, DemoAction.OPEN_DOORS, DemoAction.BEFORE_PHOTOS, DemoAction.START_RIDE)) {
            s = performDemoAction(s, a).state
        }
        assertFalse(performDemoAction(s, DemoAction.FINISH_RIDE).success)
        s = performDemoAction(s, DemoAction.RETURN_PHOTOS).state
        assertFalse(performDemoAction(s, DemoAction.FINISH_RIDE).success)
        s = performDemoAction(s, DemoAction.CLOSE_DOORS).state
        val completed = performDemoAction(s, DemoAction.FINISH_RIDE)
        assertTrue(completed.success)
        assertEquals("COMPLETED", completed.state.tripStatus)
        assertTrue(completed.state.receiptReady)
    }

    @Test fun noActualPaymentOrDoorControlIsRepresented() {
        var s = chooseDemoBooking(ready(), "ride-2", "PENDING").state
        s = performDemoAction(s, DemoAction.ADMIN_APPROVE).state
        val door = performDemoAction(s, DemoAction.OPEN_DOORS)
        assertTrue(door.message.contains("имитация", ignoreCase = true))
        assertTrue(performDemoAction(ready(), DemoAction.LINK_PAYMENT).message.contains("демо", ignoreCase = true))
    }

    @Test fun invalidActionDoesNotMutateStateOrHistory() {
        val original = DemoState()
        val failed = performDemoAction(original, DemoAction.REFUEL)
        assertFalse(failed.success)
        assertEquals(original, failed.state)
    }

    @Test fun selectingAnotherRideClearsPreviousVehicleControls() {
        var s = chooseDemoBooking(ready(), "ride-1", "PENDING").state
        s = performDemoAction(s, DemoAction.ADMIN_APPROVE).state
        s = performDemoAction(s, DemoAction.OPEN_DOORS).state
        s = chooseDemoBooking(s, "ride-2", "PENDING").state
        assertFalse(s.doorsOpen)
        assertFalse(s.beforePhotos)
        assertEquals("ride-2", s.tripId)
    }

    @Test fun tripExtrasAreSimulationOnly() {
        var s = chooseDemoBooking(ready(), "ride-1", "PENDING").state
        listOf(DemoAction.ADMIN_APPROVE, DemoAction.OPEN_DOORS, DemoAction.BEFORE_PHOTOS, DemoAction.START_RIDE)
            .forEach { s = performDemoAction(s, it).state }
        assertEquals(2, performDemoAction(s, DemoAction.EXTEND).state.extraHours)
        assertTrue(performDemoAction(s, DemoAction.SUPPORT).state.supportRequested)
        assertEquals(1, performDemoAction(s, DemoAction.INCIDENT).state.incidentCount)
    }
}
