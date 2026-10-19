package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

class PickupAddressTest {
    @Test fun coordinatesArePreciseAndReadable() {
        assertEquals("43.49450, 43.60710", pickupCoordinates(43.4945, 43.6071))
        assertEquals("Координаты не указаны", pickupCoordinates(null, 43.6071))
        assertEquals("Координаты не указаны", pickupCoordinates(91.0, 43.6071))
    }

    @Test fun trainingPickupDescriptionsCannotPassAsVerifiedAddresses() {
        assertTrue(isTrainingPickup("Нальчик · учебная точка №1 (север)"))
        assertTrue(isTrainingPickup("Вымышленное местоположение — демо"))
        assertTrue(isTrainingPickup(""))
        assertFalse(isTrainingPickup("Нальчик, проспект Ленина, 1"))
    }
}
