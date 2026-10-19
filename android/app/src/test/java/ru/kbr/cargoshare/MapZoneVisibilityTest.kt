package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

/** Regression policy for the two map engines: never expose zones in fleet browsing. */
class MapZoneVisibilityTest {
    @Test fun parkingZonesAreForDrivingOnly() {
        assertFalse(shouldShowParkingZones(false, "грузовик", false))
        assertFalse(shouldShowParkingZones(true, "пешком", false))
        assertFalse(shouldShowParkingZones(true, "грузовик", true))
        assertTrue(shouldShowParkingZones(true, "грузовик", false))
    }
}
