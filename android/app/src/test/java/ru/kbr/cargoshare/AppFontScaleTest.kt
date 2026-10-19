package ru.kbr.cargoshare

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontScaleTest {
    @Test fun scaleIsClampedAndFinite() {
        assertEquals(.9f, normalizeAppFontScale(.1f))
        assertEquals(1.0f, normalizeAppFontScale(1.03f))
        assertEquals(1.1f, normalizeAppFontScale(1.12f))
        assertEquals(1.2f, normalizeAppFontScale(9f))
        assertEquals(1.0f, normalizeAppFontScale(Float.NaN))
    }
}
