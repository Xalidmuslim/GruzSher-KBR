package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

class DemoFleetCatalogTest {
    @Test
    fun fourthGazelleBusiness2012IsVisibleInOfflineDemoFleet() {
        val cars = offlineDemoCatalog()
        assertEquals("Offline demo must show 4 trucks", 4, cars.size)
        assertEquals(cars.size, cars.map { it.id }.distinct().size)
        val fourth = cars.single { it.id == "gazelle-business-2012" }
        assertEquals("ГАЗель Бизнес 2012", fourth.name)
        assertTrue("All estimates must be clearly training-only", fourth.description.contains("Учебные"))
        assertNotNull("Fourth truck should be visible on DEMO map", fourth.latitude)
        assertNotNull(fourth.longitude)
    }
}
