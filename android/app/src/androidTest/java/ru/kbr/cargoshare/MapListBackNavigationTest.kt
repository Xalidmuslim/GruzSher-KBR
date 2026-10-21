package ru.kbr.cargoshare

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Back from "Списком" must restore the previous embedded map, not exit Fleet. */
@RunWith(AndroidJUnit4::class)
class MapListBackNavigationTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val ctx = inst.targetContext

    private fun capture(name: String) {
        device.executeShellCommand("mkdir -p /sdcard/Download/GruzSherQA")
        device.executeShellCommand("screencap -p /sdcard/Download/GruzSherQA/$name.png")
        device.executeShellCommand("uiautomator dump /sdcard/Download/GruzSherQA/$name.xml")
    }

    @Test fun systemBackFromFullTruckListRestoresEmbeddedMap() {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(launch)
        assertTrue(device.wait(Until.hasObject(By.text("Найти грузовик")), 30_000L))

        // The map/list toggle sits below the header and map on shorter phones.
        var toggle = device.wait(Until.findObject(By.textContains("Списком")), 3_000L)
        repeat(3) {
            if (toggle == null) {
                device.swipe(device.displayWidth / 2,
                    (device.displayHeight * .78).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * .37).toInt(), 24)
                toggle = device.wait(Until.findObject(By.textContains("Списком")), 3_000L)
            }
        }
        assertNotNull("Fleet map mode toggle is not available", toggle)
        toggle!!.click()
        assertTrue("List mode did not appear",
            device.wait(Until.hasObject(By.textContains("С картой")), 8_000L))
        capture("fleet_list_before_back")

        // Both the system Back button and the OS predictive edge gesture are
        // routed through Compose's BackHandler.
        device.pressBack()
        assertTrue("Map was not restored by system Back",
            device.wait(Until.hasObject(By.textContains("Списком")), 8_000L))
        assertTrue("Embedded vehicle map was not brought into view",
            device.wait(Until.hasObject(By.textContains("Учебные точки")), 8_000L))
        // "Найти грузовик" is intentionally scrolled offscreen after we
        // restore the map. Map caption plus bottom navigation identify Fleet.
        assertTrue("Fleet tab is no longer selected",
            device.hasObject(By.text("Автомобили")))
        capture("fleet_map_after_back")
    }
}
