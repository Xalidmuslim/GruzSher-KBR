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

/** Actual on-device regression for realtime typography in an open settings sheet. */
@RunWith(AndroidJUnit4::class)
class FontScaleLiveTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val context = inst.targetContext

    private fun tap(label: String): UiObject2 {
        val element = device.wait(Until.findObject(By.text(label)), 15_000L)
        assertNotNull("Cannot find '$label'", element)
        val clickable = generateSequence(element!!) { it.parent }
            .firstOrNull { it.isClickable } ?: element
        clickable.click()
        return element
    }

    private fun screenshot(name: String) {
        device.executeShellCommand("mkdir -p /sdcard/Download/GruzSherQA")
        device.executeShellCommand("screencap -p /sdcard/Download/GruzSherQA/$name.png")
    }

    @Test fun changingFontSizeImmediatelyResizesTextInsideOpenSheet() {
        val prefs = context.getSharedPreferences("gruzsher_preferences", 0)
        prefs.edit().putFloat(APP_FONT_SCALE_KEY, 1f).commit()
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(
                context.packageName)!!.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(launch)
            assertTrue(device.wait(Until.hasObject(By.text("Найти грузовик")), 30_000L))
            tap("Профиль")
            assertTrue(device.wait(Until.hasObject(By.text("Личный кабинет")), 15_000L))
            tap("Настройки")
            assertTrue(device.wait(Until.hasObject(By.text("Размер шрифта")), 15_000L))

            tap("90%")
            assertTrue(device.wait(Until.hasObject(By.text("Выбран размер: 90%")), 5_000L))
            val small = device.wait(
                Until.findObject(By.text("Проверка шрифта")), 5_000L)
            assertNotNull(small)
            val smallHeight = small!!.visibleBounds.height()
            screenshot("font_live_90")

            tap("120%")
            assertTrue(device.wait(Until.hasObject(By.text("Выбран размер: 120%")), 5_000L))
            assertTrue("Settings sheet closed unexpectedly",
                device.hasObject(By.text("Размер шрифта")))
            val large = device.wait(
                Until.findObject(By.text("Проверка шрифта")), 5_000L)
            assertNotNull(large)
            val largeHeight = large!!.visibleBounds.height()
            screenshot("font_live_120")
            assertTrue("Font scale not applied to visible text. Before=$smallHeight after=$largeHeight",
                largeHeight >= smallHeight * 1.22f)
            assertEquals(1.2f, prefs.getFloat(APP_FONT_SCALE_KEY, 1f), 0f)
        } finally {
            prefs.edit().putFloat(APP_FONT_SCALE_KEY, 1f).commit()
        }
    }
}
