package ru.kbr.cargoshare

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/** On-device QA for the clipped green CTA and real marker-to-details flow. */
@RunWith(AndroidJUnit4::class)
class LayoutSmokeTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val context = inst.targetContext

    private fun click(text: String, wait: Long = 15_000L) {
        if (!device.wait(Until.hasObject(By.text(text)), 3_000L)) {
            runCatching {
                UiScrollable(UiSelector().scrollable(true))
                    .scrollIntoView(UiSelector().text(text))
            }
        }
        val found = device.wait(Until.findObject(By.text(text)), wait)
        if (found == null) capture("missing_action")
        assertNotNull("Missing UI element: $text", found)
        val target = generateSequence(found!!) { it.parent }.firstOrNull { it.isClickable } ?: found
        target.click()
    }

    private fun capture(name: String) {
        device.executeShellCommand("mkdir -p /sdcard/Download/GruzSherQA")
        device.executeShellCommand("screencap -p /sdcard/Download/GruzSherQA/$name.png")
        device.executeShellCommand("uiautomator dump /sdcard/Download/GruzSherQA/$name.xml")
    }

    private fun tapCarMarker() {
        var position: Pair<Int, Int>? = null
        val car = offlineDemoCatalog().first { it.name.contains("Ford", true) }
        inst.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).firstOrNull()
            fun findMap(view: View): MapView? {
                if (view is MapView) return view
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        val found = findMap(view.getChildAt(i))
                        if (found != null) return found
                    }
                }
                return null
            }
            val map = activity?.window?.decorView?.let(::findMap)
            if (map != null && car.latitude != null && car.longitude != null) {
                val origin = IntArray(2)
                map.getLocationOnScreen(origin)
                val point = map.projection.toPixels(GeoPoint(car.latitude, car.longitude), null)
                position = (origin[0] + point.x) to (origin[1] + point.y)
            }
        }
        assertNotNull("OSMDroid fleet map not displayed", position)
        val (x, y) = position!!
        assertTrue("Truck pin outside the active screen: $x,$y",
            x > 0 && x < device.displayWidth && y > 0 && y < device.displayHeight)
        device.click(x, y)
    }

    @Test fun mapVehicleCardAndGreenBookingFooterAreNotCropped() {
        context.getSharedPreferences("gruzsher_live_demo_v1", 0).edit().clear().commit()
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        // Capture unconditionally: a startup/permission overlay otherwise
        // produces no visual evidence and hides the root cause.
        Thread.sleep(2500L)
        capture("layout_startup")
        if (!device.wait(Until.hasObject(By.textContains("Найти грузовик")), 20_000L)) {
            capture("layout_startup_unexpected")
            fail("Fleet home title not visible after launch; inspect captured layout_startup* screenshots")
        }
        capture("layout_home")

        // The booking carousel starts below the fold on Pixel screens.
        // Swipe INSIDE the scrollable content before interacting.
        device.swipe(device.displayWidth / 2,
            (device.displayHeight * .78).toInt(), device.displayWidth / 2,
            (device.displayHeight * .35).toInt(), 28)
        Thread.sleep(600)
        capture("layout_carousel_after_scroll")
        click("Выбрать")
        assertTrue(device.wait(Until.hasObject(By.text("Выбор грузовика")), 15_000L))
        val primary = device.wait(Until.findObject(By.text("Продолжить к автопроверке · ДЕМО")), 15_000L)
        assertNotNull("Primary booking CTA not displayed", primary)
        assertNotNull("Separate route action not displayed",
            device.wait(Until.findObject(By.text("Построить маршрут")), 5_000L))
        val primaryButton = generateSequence(primary!!) { it.parent }
            .firstOrNull { it.isClickable } ?: primary
        val bounds = primaryButton.visibleBounds
        // Assert against the OS navigation zone; don't mistake app pixels
        // painted under the gesture handle for accessible tap targets.
        val safeEdge = device.displayHeight - (18 * context.resources.displayMetrics.density).toInt()
        assertTrue("Green booking button clipped by system gesture navigation: $bounds / $safeEdge",
            bounds.bottom < safeEdge)
        capture("layout_booking_green_cta")

        click("Построить маршрут")
        assertTrue(device.wait(Until.hasObject(By.text("Расположение автомобиля на карте")), 15_000L))
        capture("layout_booking_road_map")
        device.pressBack()
        Thread.sleep(500)
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.text("Найти грузовик")), 15_000L))

        val mapControl = device.wait(Until.findObject(By.textContains("На карте")), 10_000L)
        assertNotNull("Full fleet map action not shown", mapControl)
        val mapButton = generateSequence(mapControl!!) { it.parent }
            .firstOrNull { it.isClickable } ?: mapControl
        mapButton.click()
        assertTrue(device.wait(Until.hasObject(By.textContains("Автомобили на карте")), 12_000L))
        capture("layout_fullscreen_fleet")
        Thread.sleep(1500)
        tapCarMarker()
        assertTrue("Vehicle card did not open from truck marker",
            device.wait(Until.hasObject(By.text("Где находится машина")), 12_000L))
        assertTrue(device.wait(Until.hasObject(By.text("Грузоподъёмность")), 5_000L))
        assertNotNull(device.wait(Until.findObject(By.text("Построить маршрут")), 5_000L))
        assertNotNull(device.wait(Until.findObject(By.text("Подробнее")), 5_000L))
        capture("layout_vehicle_marker_card")
    }
    @Test fun fontSizesInSettingsKeepPrimaryGreenActionVisible() {
        val prefs = context.getSharedPreferences("gruzsher_preferences", 0)
        prefs.edit().putFloat(APP_FONT_SCALE_KEY, 1f).commit()
        try {
            context.startActivity(context.packageManager
                .getLaunchIntentForPackage(context.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.text("Найти грузовик")), 30_000L))
            click("Профиль")
            assertTrue(device.wait(Until.hasObject(By.text("Личный кабинет")), 15_000L))
            click("Настройки")
            assertTrue(device.wait(Until.hasObject(By.text("Размер шрифта")), 15_000L))
            for (percent in listOf("90%", "100%", "110%", "120%")) {
                click(percent)
                assertTrue("Option not displayed after changing font: $percent",
                    device.wait(Until.hasObject(By.text("Размер шрифта")), 5_000L))
                capture("layout_font_" + percent.removeSuffix("%"))
            }
            assertEquals(1.2f, prefs.getFloat(APP_FONT_SCALE_KEY, 1f), 0f)
            device.pressBack() // Close settings bottom sheet
            click("Автомобили")
            assertTrue(device.wait(Until.hasObject(By.text("Найти грузовик")), 15_000L))
            device.swipe(device.displayWidth / 2,
                (device.displayHeight * .78).toInt(), device.displayWidth / 2,
                (device.displayHeight * .35).toInt(), 28)
            click("Выбрать")
            assertTrue(device.wait(Until.hasObject(By.text("Выбор грузовика")), 15_000L))
            val green = device.wait(
                Until.findObject(By.text("Продолжить к автопроверке · ДЕМО")), 15_000L)
            assertNotNull("Green CTA not visible at 120% font", green)
            val button = generateSequence(green!!) { it.parent }
                .firstOrNull { it.isClickable } ?: green
            val bottom = button.visibleBounds.bottom
            val safeEdge = device.displayHeight -
                (18 * context.resources.displayMetrics.density).toInt()
            capture("layout_booking_font_120")
            assertTrue("Green CTA is clipped at 120% ($bottom >= $safeEdge)",
                bottom < safeEdge)
        } finally {
            prefs.edit().putFloat(APP_FONT_SCALE_KEY, 1f).commit()
        }
    }

}
