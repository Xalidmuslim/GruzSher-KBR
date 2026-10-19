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
        val found = device.wait(Until.findObject(By.text(text)), wait)
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
        assertTrue(device.wait(Until.hasObject(By.text("Найти грузовик")), 30_000L))
        capture("layout_home")

        click("Выбрать")
        assertTrue(device.wait(Until.hasObject(By.text("Выбор грузовика")), 15_000L))
        val primary = device.wait(Until.findObject(By.text("Продолжить к автопроверке · ДЕМО")), 15_000L)
        assertNotNull("Primary booking CTA not displayed", primary)
        assertNotNull("Separate route action not displayed",
            device.wait(Until.findObject(By.text("Построить маршрут")), 5_000L))
        val primaryButton = generateSequence(primary!!) { it.parent }
            .firstOrNull { it.isClickable } ?: primary
        val bounds = primaryButton.visibleBounds
        val safeEdge = device.displayHeight - (14 * context.resources.displayMetrics.density).toInt()
        assertTrue("Green booking button clipped by system gesture navigation: $bounds / $safeEdge",
            bounds.bottom < safeEdge)
        capture("layout_booking_green_cta")

        click("Построить маршрут")
        assertTrue(device.wait(Until.hasObject(By.text("Расположение автомобиля на карте")), 15_000L))
        capture("layout_booking_road_map")
        click("Закрыть карту")
        click("Назад")
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
}
