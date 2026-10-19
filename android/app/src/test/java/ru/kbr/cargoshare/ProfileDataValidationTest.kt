package ru.kbr.cargoshare
import org.junit.Test
import org.junit.Assert.*

class ProfileDataValidationTest {
    @Test fun optionalContactsAndValidation() {
        assertTrue(validProfilePhone(""))
        assertTrue(validProfileEmail(""))
        assertTrue(validProfilePhone("+7 999 123-45-67"))
        assertFalse(validProfilePhone("not a phone"))
        assertTrue(validProfileEmail("test@example.org"))
        assertFalse(validProfileEmail("not-an-email"))
    }
}
