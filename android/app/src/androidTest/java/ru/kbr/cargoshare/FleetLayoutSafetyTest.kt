package ru.kbr.cargoshare

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Live UI checks for map vehicle card and overlapping / cut-off action buttons. */
@RunWith(AndroidJUnit4::class)
class FleetLayoutSafetyTest {
    @get:Rule val composeRule = createComposeRule()

    private val exampleCar = Vehicle(
        id = "layout-qa-1",
        name = "ГАЗель NEXT",
        type = "Тентованный фургон",
        payloadKg = 1500,
        cargoVolumeM3 = 12,
        hourlyPriceRub = 550,
        dailyPriceRub = 3300,
        transmission = "Механика",
        pickupLocation = "Учебная стоянка",
        description = "Описание автомобиля и условий аренды. ".repeat(22),
        imageUrl = "",
        latitude = 43.4945,
        longitude = 43.6071
    )

    @Test fun vehicleMapCardShowsAllKnownVehicleFactsAndRouteActionWithScrollableDescription() {
        var routeClicked = false
        var detailsClicked = false
        composeRule.setContent {
            FleetMapVehicleSheet(exampleCar, onDismiss = {},
                onOpenDetails = { detailsClicked = true },
                onBuildRoute = { routeClicked = true })
        }
        composeRule.onNodeWithText("ГАЗель NEXT").assertIsDisplayed()
        composeRule.onNodeWithText("550 ₽/ч").assertIsDisplayed()
        composeRule.onNodeWithText("Построить маршрут").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue("Road routing button not clickable", routeClicked) }
        composeRule.onNodeWithText("1500 кг").assertExists()
        composeRule.onNodeWithText("12 м³").assertExists()
        composeRule.onNodeWithText("Механика").assertExists()
        composeRule.onNodeWithText("3300 ₽").assertExists()
        composeRule.onNodeWithText("Описание").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Подробнее").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue("Details button not clickable", detailsClicked) }
    }

    @Test fun bookingFooterRouteButtonAndPrimaryGreenCtaRemainVisibleAndSeparate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        context.startActivity(
            context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        assertTrue("Main screen never opened",
            device.wait(Until.hasObject(By.text("Найти грузовик")), 45_000))
        val choose = device.wait(Until.findObject(By.text("Выбрать")), 15_000)
        assertTrue("No selectable truck card", choose != null)
        val clickable = generateSequence(choose) { it?.parent }.firstOrNull { it?.isClickable == true }
        (clickable ?: choose).click()
        assertTrue("Vehicle detail never opened",
            device.wait(Until.hasObject(By.text("Выбор грузовика")), 15_000))
        val build = device.wait(Until.findObject(By.text("Построить маршрут")), 15_000)
        val next = device.wait(Until.findObject(By.text("Продолжить к автопроверке · ДЕМО")), 15_000)
        assertTrue("Route button missing", build != null)
        assertTrue("Primary CTA missing", next != null)
        val routeRect = build!!.visibleBounds
        val nextRect = next!!.visibleBounds
        assertTrue("Route action overlaps green CTA", routeRect.bottom <= nextRect.top)
        assertTrue("Green CTA falls below display", nextRect.bottom <= device.displayHeight - 12)
        assertTrue("Route CTA falls outside display", routeRect.top >= 0)
        device.executeShellCommand("mkdir -p /sdcard/Download/GruzSherQA")
        device.executeShellCommand(
            "screencap -p /sdcard/Download/GruzSherQA/12_safe_booking_footer.png")
    }
}
