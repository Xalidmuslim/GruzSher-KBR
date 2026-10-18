package ru.kbr.cargoshare

import org.junit.Assert.*
import org.junit.Test

class VehicleFiltersTest {
    @Test fun vansAndBoardTrucksAreDifferent() {
        assertTrue(categoryMatches("Тентованный фургон", "Фургоны"))
        assertFalse(categoryMatches("Тентованный фургон", "Бортовые"))
        assertTrue(categoryMatches("Бортовой", "Бортовые"))
        assertFalse(categoryMatches("Бортовой", "Фургоны"))
    }
    @Test fun caseInsensitiveAndAll() {
        assertTrue(categoryMatches("РЕФРИЖЕРАТОР", "Рефрижераторы"))
        assertTrue(categoryMatches("Фургон", "Все"))
    }
}
