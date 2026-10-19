package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

class DemoGeoTest {
    @Test fun remainingWalkingDistanceDecreasesToZero() {
        val first = demoWalkRemainingMeters(43.4853, 43.6071, 0f)
        assertTrue(first in 170..240)
        assertTrue(demoWalkRemainingMeters(43.4853, 43.6071, .5f) in 80..120)
        assertEquals(0, demoWalkRemainingMeters(43.4853, 43.6071, 1f))
    }

    @Test fun parkingDistanceDecreasesIntoIllustrativeGeofence() {
        assertTrue(demoReturnRemainingMeters(43.4853, 43.6071, 0f) > DEMO_PARKING_ZONE_RADIUS_METERS)
        assertTrue(demoReturnRemainingMeters(43.4853, 43.6071, .95f) < DEMO_PARKING_ZONE_RADIUS_METERS)
        assertEquals(0, demoReturnRemainingMeters(43.4853, 43.6071, 1f))
    }

    @Test fun coordinateDistanceIsSymmetricAndZeroForSameLocation() {
        assertEquals(0.0, demoDistanceMeters(43.4853,43.6071,43.4853,43.6071), .00001)
        val a = demoDistanceMeters(43.4853,43.6071,43.49,43.61)
        val b = demoDistanceMeters(43.49,43.61,43.4853,43.6071)
        assertEquals(a,b,.00001)
    }
}
