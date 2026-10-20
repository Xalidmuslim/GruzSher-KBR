package ru.kbr.cargoshare

import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumMotionTest {
    @Test fun transitionsAreDeliberateAndNeverUltraFast() {
        assertTrue(PremiumMotion.SCREEN_MS >= 550)
        assertTrue(PremiumMotion.CARD_MS >= 450)
        assertTrue(PremiumMotion.GALLERY_MS >= 550)
        assertTrue(PremiumMotion.TOUCH_MS >= 300)
        assertTrue(PremiumMotion.SCREEN_MS <= 900)
        assertTrue(PremiumMotion.GALLERY_MS <= 900)
    }

    @Test fun easingIsSmoothMonotonicAndHasNoOvershoot() {
        val easing = PremiumMotion.Smooth
        var previous = easing.transform(0f)
        for (i in 1..100) {
            val value = easing.transform(i / 100f)
            assertTrue("Easing must be monotonic without bounce", value >= previous - 0.00001f)
            assertTrue("Easing must stay within [0,1]", value in 0f..1f)
            previous = value
        }
    }
}
