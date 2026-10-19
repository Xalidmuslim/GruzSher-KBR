package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

class RealDistanceTest {
    @Test fun zeroAndKilometers() {
        assertEquals(0.0, airDistanceMeters(43.48, 43.60, 43.48, 43.60), 0.001)
        val oneDegree = airDistanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_195.0, oneDegree, 150.0)
        assertEquals(oneDegree, airDistanceMeters(0.0, 1.0, 0.0, 0.0), 0.001)
    }
    @Test fun humanFriendlyFormatting() {
        assertEquals("87 м", humanDistance(87.2))
        assertEquals("1,5 км", humanDistance(1540.0))
        assertEquals("23 км", humanDistance(22_900.0))
        assertEquals("Расстояние недоступно", humanDistance(Double.NaN))
    }
}
