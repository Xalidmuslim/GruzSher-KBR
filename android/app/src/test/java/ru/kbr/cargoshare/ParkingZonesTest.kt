package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

class ParkingZonesTest {
    private val square = listOf(
        ZonePoint(43.48,43.60), ZonePoint(43.48,43.61),
        ZonePoint(43.49,43.61), ZonePoint(43.49,43.60))

    @Test fun properAreasOnly() {
        assertTrue(validZonePoints(square))
        assertFalse(validZonePoints(square.take(2)))
        assertFalse(validZonePoints(listOf(ZonePoint(1.0,1.0),
            ZonePoint(2.0,2.0), ZonePoint(3.0,3.0))))
    }

    @Test fun forbiddenOverridesAllowedWhenOverlapping() {
        val ok = LocalParkingZone("a", "Разрешено", ParkingRule.ALLOWED, square)
        val stop = LocalParkingZone("b", "Запрещено", ParkingRule.FORBIDDEN, square)
        val at = ZonePoint(43.485,43.605)
        assertEquals(true, localReturnRule(listOf(ok), at))
        assertEquals(false, localReturnRule(listOf(ok, stop), at))
        assertEquals(false, localReturnRule(listOf(ok), ZonePoint(43.5,43.61)))
        assertNull(localReturnRule(emptyList(), at))
        assertNull(localReturnRule(listOf(ok.copy(enabled=false)), at))
    }

    @Test fun pointInsideAndOutside() {
        assertTrue(containsGeoPoint(square, ZonePoint(43.485,43.605)))
        assertFalse(containsGeoPoint(square, ZonePoint(43.47,43.60)))
    }
}
