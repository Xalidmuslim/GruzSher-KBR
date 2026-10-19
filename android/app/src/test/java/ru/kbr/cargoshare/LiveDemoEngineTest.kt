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
        assertFalse(applyLive(s, LiveEvent.VERIFY_CODE, base + 101L, "1111").accepted)
        s = action(s, LiveEvent.VERIFY_CODE, base + 101L, "2468")
        assertEquals(LivePhase.DOCUMENTS, s.phase)
        assertTrue(s.passportStartedAt > 0L)
        assertEquals(s.passportStartedAt, s.licenceStartedAt)
        assertFalse(applyLive(s, LiveEvent.SCAN_PASSPORT, base + 3400L).accepted)
        s = advanceLive(s, base + 5700L)
        assertEquals(LivePhase.PAYMENT, s.phase)
        s = action(s, LiveEvent.START_PAYMENT, base + 6100L)
        s = advanceLive(s, base + 8700L)
        assertEquals(LivePhase.CONFIRM, s.phase)
        return action(s, LiveEvent.ACCEPT_TERMS, base + 8800L, "true")
    }

    @Test fun phoneCodeIsAvailableImmediatelyButStillValidated() {
        var s = action(LiveDemoSession(), LiveEvent.PICK_CAR, base, "gazelle-next")
        assertFalse(applyLive(s, LiveEvent.REQUEST_CODE, base, "1234").accepted)
        s = action(s, LiveEvent.REQUEST_CODE, base + 1L, "+79000000000")
        assertFalse(applyLive(s, LiveEvent.VERIFY_CODE, base + 1L, "1111").accepted)
        assertEquals(LivePhase.DOCUMENTS, action(s, LiveEvent.VERIFY_CODE, base + 1L, "2468").phase)
    }

    @Test fun cannotBookBeforeVerifyingIdentityAndCard() {
        val s = action(LiveDemoSession(), LiveEvent.PICK_CAR, base, "gazelle-next")
        assertFalse(applyLive(s, LiveEvent.REQUEST_BOOKING, base + 1000L).accepted)
        assertEquals(LivePhase.CONFIRM, prepare().phase)
    }

    @Test fun bookingConfirmationArrivalAndInspectionAreTimed() {
        var s = action(prepare(), LiveEvent.REQUEST_BOOKING, base + 9000L)
        assertEquals(LivePhase.WALK, s.phase)
        assertTrue(s.bookingId.startsWith("SIM-"))
        assertEquals("There must be no dispatcher approval screen", LivePhase.WALK,
            advanceLive(s, base + 9001L).phase)
        assertEquals(LivePhase.WALK, advanceLive(s, base + 20999L).phase)
        s = advanceLive(s, base + 21000L)
        assertEquals(LivePhase.INSPECT, s.phase)
        assertFalse(applyLive(s, LiveEvent.UNLOCK, base + 21002L).accepted)
        for (angle in listOf("Спереди", "Сзади", "Слева", "Справа"))
            s = action(s, LiveEvent.CAPTURE_ANGLE, base + 21003L, angle)
        s = action(s, LiveEvent.UNLOCK, base + 21005L)
        assertEquals(LivePhase.UNLOCK, s.phase)
        assertEquals(LivePhase.UNLOCK, advanceLive(s, base + 23504L).phase)
        s = advanceLive(s, base + 23505L)
        assertEquals(LivePhase.READY, s.phase)
        s = action(s, LiveEvent.START_DRIVE, base + 23600L)
        assertEquals(LivePhase.DRIVING, s.phase)
    }

    @Test fun documentFailureBlocksAutomaticApprovalUntilRetry() {
        var s = action(LiveDemoSession(), LiveEvent.PICK_CAR, base, "gazelle-next")
        s = action(s, LiveEvent.REQUEST_CODE, base + 1L, "+79000000000")
        s = action(s, LiveEvent.VERIFY_CODE, base + 3100L, "2468")
        s = action(s, LiveEvent.FAIL_DOCUMENT_CHECK, base + 3200L, "Истёк срок")
        assertEquals(LivePhase.DOCUMENTS, advanceLive(s, base + 10000L).phase)
        assertFalse(applyLive(s, LiveEvent.REQUEST_BOOKING, base + 10001L).accepted)
        s = action(s, LiveEvent.RETRY_DOCUMENT_CHECK, base + 10100L)
        assertEquals("", s.documentIssue)
        assertEquals(LivePhase.DOCUMENTS, advanceLive(s, base + 12599L).phase)
        assertEquals(LivePhase.PAYMENT, advanceLive(s, base + 12600L).phase)
    }

    @Test fun previousApprovalSessionMigratesImmediatelyWithoutApprovalWait() {
        val saved = LiveDemoSession(phase = LivePhase.APPROVAL,
            approvalStartedAt = base, bookingId = "SIM-OLD", carId = "gazelle-next")
        val next = advanceLive(saved, base + 100L)
        assertEquals(LivePhase.WALK, next.phase)
        assertEquals("SIM-OLD", next.bookingId)
    }

    @Test fun oldDocumentsSessionAutomaticallyStartsSampleChecks() {
        val old = LiveDemoSession(phase = LivePhase.DOCUMENTS, carId = "gazelle-next")
        val migrated = advanceLive(old, base)
        assertEquals(base, migrated.passportStartedAt)
        assertEquals(base, migrated.licenceStartedAt)
        assertEquals(LivePhase.PAYMENT, advanceLive(migrated, base + LIVE_OPERATION_MS).phase)
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
        s = action(s, LiveEvent.CHECK_RETURN_CONDITION, base + 76_000L, "true")
        s = action(s, LiveEvent.LOCK_DOORS, base + 76_000L)
        assertFalse(applyLive(s, LiveEvent.LOCK_DOORS, base + 76_000L).accepted)
        s = action(s, LiveEvent.FINISH, base + 76_000L)
        assertEquals(LivePhase.RECEIPT, s.phase)
        assertTrue(s.locked)
    }

    @Test fun financialDisplayIsExplicitlyVirtual() {
        val s = LiveDemoSession(phase = LivePhase.DRIVING, durationHours = 2,
            drivingAt = base, extraHours = 2)
        assertEquals(4 * 650, demoFare(s, 650, base))
        assertEquals(4 * 650, demoFare(s, 650, base + 31_000L))
    }

    @Test fun receiptTotalFreezesAfterArrival() {
        val s = LiveDemoSession(phase = LivePhase.RECEIPT, drivingAt = base, durationHours = 2)
        assertEquals(demoFare(s, 650, base + LIVE_ROUTE_MS),
            demoFare(s, 650, base + LIVE_ROUTE_MS + 600_000L))
    }

    @Test fun userCanRequestReturnBeforeAutomaticRouteEnds() {
        val driving = LiveDemoSession(phase = LivePhase.DRIVING, drivingAt = base)
        val result = applyLive(driving, LiveEvent.REQUEST_RETURN, base + 8_000L)
        assertTrue(result.message, result.accepted)
        assertEquals(LivePhase.RETURN, result.state.phase)
        assertEquals(1f, routeFraction(result.state, base + 8_000L), 0.001f)
        assertEquals(LivePhase.RETURN, advanceLive(result.state, base + 8_000L).phase)
    }

    @Test fun savedLegacyReturningSessionDoesNotKeepArtificialDelay() {
        val old = LiveDemoSession(phase = LivePhase.RETURNING,
            drivingAt = base, returnRequestedAt = base + 5_000L, bookingId = "SIM-legacy")
        val migrated = advanceLive(old, base + 5_001L)
        assertEquals(LivePhase.RETURN, migrated.phase)
        assertEquals("SIM-legacy", migrated.bookingId)
    }

    @Test fun returnFromWaitResumesTripWithoutLosingTime() {
        val paused = LiveDemoSession(phase = LivePhase.DRIVING, drivingAt = base, pauseStartedAt = base + 5_000L)
        val result = applyLive(paused, LiveEvent.REQUEST_RETURN, base + 9_000L)
        assertTrue(result.accepted)
        assertEquals(0L, result.state.pauseStartedAt)
        assertEquals(4L, result.state.pausedSeconds)
    }

    @Test fun cannotFinishTripStraightFromMovingVehicle() {
        val s = LiveDemoSession(phase = LivePhase.DRIVING, drivingAt = base)
        assertFalse(applyLive(s, LiveEvent.FINISH, base + 2_000L).accepted)
        assertFalse(applyLive(s, LiveEvent.TAKE_RETURN_PHOTO, base + 2_000L).accepted)
    }

    @Test fun completedReceiptDoesNotIncreaseWhileOpen() {
        val s = LiveDemoSession(phase = LivePhase.RECEIPT, drivingAt = base, finishedAt = base + 15_000L)
        assertEquals(demoFare(s, 650, base + 15_000L), demoFare(s, 650, base + 600_000L))
    }

    @Test fun simulatedDistanceAndSpeedStayConsistent() {
        val startedAt = base
        val session = LiveDemoSession(phase = LivePhase.DRIVING, drivingAt = startedAt)
        assertEquals(0.0, demoDistanceKm(session, startedAt), 0.001)
        assertEquals(0.3, demoDistanceKm(session, startedAt + LIVE_ROUTE_MS / 2), 0.001)
        assertEquals(LIVE_ROUTE_DISTANCE_KM, demoDistanceKm(session, startedAt + LIVE_ROUTE_MS), 0.001)
        assertEquals(29, demoSpeedKmh(session))
        assertEquals(0, demoSpeedKmh(session.copy(pauseStartedAt = startedAt + 1_000L)))
    }

    @Test fun noTravelDuringDemoWaiting() {
        val session = LiveDemoSession(phase = LivePhase.DRIVING, drivingAt = base)
        val waiting = applyLive(session, LiveEvent.TOGGLE_WAIT, base + 15_000L).state
        assertEquals(demoDistanceKm(waiting, base + 15_000L),
            demoDistanceKm(waiting, base + 25_000L), 0.001)
        assertEquals(0, demoSpeedKmh(waiting))
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

    @Test fun receiptElapsedTimeIsFrozenAtCompletion() {
        val s = LiveDemoSession(phase = LivePhase.RECEIPT, drivingAt = base, finishedAt = base + 30_000)
        assertEquals(30L, elapsedRideSeconds(s, base + 900_000))
    }
    @Test fun duplicateLockCannotRestartAcknowledgementTimer() {
        val s = LiveDemoSession(phase = LivePhase.RETURN, returnPhotoAt = base - 3000, lockingAt = base)
        val result = applyLive(s, LiveEvent.LOCK_DOORS, base + 1000)
        assertEquals(base, result.state.lockingAt)
    }
    @Test fun supportAndIncidentStoreActualReportText() {
        val s = LiveDemoSession(phase = LivePhase.DRIVING, drivingAt = base)
        val support = applyLive(s, LiveEvent.ASK_SUPPORT, base + 1000, "Как продлить аренду?")
        assertTrue(support.state.events.last().contains("Как продлить аренду?"))
        val incident = applyLive(s, LiveEvent.REPORT_INCIDENT, base + 1000, "Повреждено зеркало")
        assertTrue(incident.state.events.last().contains("Повреждено зеркало"))
    }
    @Test fun damageDescriptionIsPreservedInAct() {
        val s = LiveDemoSession(phase = LivePhase.INSPECT)
        val r = applyLive(s, LiveEvent.REPORT_DAMAGE, base, "Царапина слева, учебное фото")
        assertTrue(r.state.events.last().contains("Царапина слева"))
    }

    @Test fun cannotCloseReturnBeforeConditionConfirmation() {
        val s=LiveDemoSession(phase=LivePhase.RETURN,returnPhotoAt=base)
        assertFalse(applyLive(s,LiveEvent.LOCK_DOORS,base+3000).accepted)
    }
    @Test fun confirmStageRequiresExplicitTermsAcceptance() {
        assertFalse(applyLive(LiveDemoSession(phase=LivePhase.CONFIRM),LiveEvent.REQUEST_BOOKING,base).accepted)
    }

    @Test fun liveFareUsesSameDailyCapAsCatalogQuote() {
        val s=LiveDemoSession(carId="gazelle-next",durationHours=8)
        assertEquals(3400,demoFare(s,550,base))
    }

    @Test fun fullRentalForEveryModelProducesStableReceiptAndHistory() {
        offlineDemoCatalog().forEach { car ->
            var t=base
            var s=action(LiveDemoSession(durationHours=8),LiveEvent.PICK_CAR,t,car.id)
            s=action(s,LiveEvent.REQUEST_CODE,++t,"+79000000000")
            t+=3001;s=action(s,LiveEvent.VERIFY_CODE,t,"2468")
            t+=LIVE_OPERATION_MS;s=advanceLive(s,t)
            s=action(s,LiveEvent.START_PAYMENT,++t)
            t+=LIVE_OPERATION_MS;s=advanceLive(s,t)
            s=action(s,LiveEvent.ACCEPT_TERMS,++t,"true")
            s=action(s,LiveEvent.REQUEST_BOOKING,++t)
            val id=s.bookingId
            assertFalse(applyLive(s,LiveEvent.REQUEST_BOOKING,++t).accepted)
            t+=LIVE_APPROACH_MS;s=advanceLive(s,t)
            listOf("Спереди","Сзади","Слева","Справа").forEach { side -> s=action(s,LiveEvent.CAPTURE_ANGLE,++t,side) }
            s=action(s,LiveEvent.REPORT_DAMAGE,++t,"Демо: царапина на кузове")
            s=action(s,LiveEvent.UNLOCK,++t)
            t+=LIVE_OPERATION_MS;s=advanceLive(s,t)
            s=action(s,LiveEvent.START_DRIVE,++t)
            t+=1000;s=action(s,LiveEvent.TOGGLE_WAIT,t)
            val distance=demoDistanceKm(s,t)
            t+=10000;assertEquals(distance,demoDistanceKm(s,t),0.001)
            s=action(s,LiveEvent.ASK_SUPPORT,t,"Как вернуть машину?")
            s=action(s,LiveEvent.REPORT_INCIDENT,t,"Демо: проверка обращения")
            s=action(s,LiveEvent.EXTEND,++t)
            s=action(s,LiveEvent.TOGGLE_WAIT,++t)
            s=action(s,LiveEvent.REQUEST_RETURN,++t)
            assertEquals(LivePhase.RETURN, s.phase)
            s=action(s,LiveEvent.TAKE_RETURN_PHOTO,++t)
            s=action(s,LiveEvent.CHECK_RETURN_CONDITION,++t,"true")
            s=action(s,LiveEvent.LOCK_DOORS,++t)
            s=action(s,LiveEvent.FINISH,++t)
            s=action(s,LiveEvent.SET_RATING,++t,"5")
            assertEquals(LivePhase.RECEIPT,s.phase)
            assertEquals(id,s.bookingId)
            assertEquals(5,s.rating)
            assertTrue(s.locked)
            assertTrue(s.events.any {it.contains("Как вернуть машину?")})
            assertEquals(car.dailyPriceRub,demoFare(s,car.hourlyPriceRub,t))
            assertEquals(elapsedRideSeconds(s,t),elapsedRideSeconds(s,t+900000))
            assertFalse(applyLive(s,LiveEvent.FINISH,++t).accepted)
        }
    }
    @Test fun everyDocumentFailureBlocksProgressUntilRetry() {
        listOf("Нечитаемый документ","Истёк срок","Неверная категория").forEach { issue ->
            val checking=LiveDemoSession(phase=LivePhase.DOCUMENTS,passportStartedAt=base,licenceStartedAt=base)
            val failed=action(checking,LiveEvent.FAIL_DOCUMENT_CHECK,base+1,issue)
            assertEquals(LivePhase.DOCUMENTS,advanceLive(failed,base+100000).phase)
            val retried=action(failed,LiveEvent.RETRY_DOCUMENT_CHECK,base+100001)
            assertEquals(LivePhase.PAYMENT,advanceLive(retried,base+100001+LIVE_OPERATION_MS).phase)
        }
    }
}
