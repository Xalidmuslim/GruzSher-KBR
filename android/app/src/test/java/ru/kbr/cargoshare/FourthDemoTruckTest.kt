package ru.kbr.cargoshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FourthDemoTruckTest {
    @Test fun catalogHasFourSeparatelySelectableTrucks() {
        val cars = offlineDemoCatalog()
        assertEquals(4, cars.size)
        assertEquals(4, cars.map { it.id }.distinct().size)
        assertEquals(4, cars.map { it.latitude to it.longitude }.distinct().size)
        assertTrue(cars.all { it.latitude != null && it.longitude != null })
    }

    @Test fun duplicatedGazelleUsesTheExactSameSixPhotosAndRates() {
        val cars = offlineDemoCatalog()
        val original = cars.single { it.id == "gazelle-next" }
        val fourth = cars.single { it.id == "gazelle-next-2" }
        assertNotEquals(original.id, fourth.id)
        assertNotEquals(original.latitude to original.longitude,
            fourth.latitude to fourth.longitude)
        assertEquals(original.hourlyPriceRub, fourth.hourlyPriceRub)
        assertEquals(original.dailyPriceRub, fourth.dailyPriceRub)
        for (view in listOf("hero", "rear", "cargo", "front", "left", "right")) {
            assertEquals(artworkSource(original, view), artworkSource(fourth, view))
        }
    }
}
