package ru.kbr.cargoshare

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks actual visible bounds, not just successful Kotlin layout compilation. */
@RunWith(AndroidJUnit4::class)
class CompactLayoutTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val ctx = inst.targetContext
    private val density = ctx.resources.displayMetrics.density

    private fun clickable(label: String): UiObject2 {
        val text = device.wait(Until.findObject(By.textContains(label)), 12_000L)
        assertNotNull("UI label missing: $label", text)
        return generateSequence(text!!) { it.parent }.firstOrNull { it.isClickable } ?: text
    }

    private fun snapshot(name: String) {
        device.executeShellCommand("mkdir -p /sdcard/Download/GruzSherQA")
        device.executeShellCommand("screencap -p /sdcard/Download/GruzSherQA/$name.png")
        device.executeShellCommand("uiautomator dump /sdcard/Download/GruzSherQA/$name.xml")
    }

    @Test
    fun compactFleetCardAndBookingButtonRemainFullyOnscreen() {
        ctx.getSharedPreferences("gruzsher_preferences", 0).edit()
            .putFloat(APP_FONT_SCALE_KEY, 1.1f).commit()
        val start = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(start)
        assertTrue(device.wait(Until.hasObject(By.text("Найти грузовик")), 30_000L))
        device.swipe(device.displayWidth / 2,
            (device.displayHeight * .78).toInt(), device.displayWidth / 2,
            (device.displayHeight * .41).toInt(), 25)
        Thread.sleep(600)
        snapshot("compact_home_110")
        val car = clickable("ГАЗель NEXT")
        val cardHeightDp = car.visibleBounds.height() / density
        assertTrue("Truck card contains excessive blank space: $cardHeightDp dp",
            cardHeightDp < 265f)
        clickable("Выбрать").click()
        assertTrue(device.wait(Until.hasObject(By.text("Выбор грузовика")), 15_000L))
        Thread.sleep(400)
        snapshot("compact_booking_footer_110")
        val button = clickable("Продолжить к автопроверке")
        val bounds = button.visibleBounds
        val heightDp = bounds.height() / density
        assertTrue("Primary action clipped vertically: $heightDp dp",
            heightDp >= 45f)
        assertTrue("Green CTA extends under system navigation: $bounds",
            bounds.bottom <= device.displayHeight - (15 * density).toInt())
    }
}
