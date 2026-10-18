package ru.kbr.cargoshare

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs only in the separate referencepreview application. No real data/network transactions. */
@RunWith(AndroidJUnit4::class)
class RentalAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun tap(text: String, timeout:Long=15_000) {
        val selector=By.text(text)
        if (!device.wait(Until.hasObject(selector),timeout)) {
            runCatching { UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(text)) }
        }
        val node=device.wait(Until.findObject(selector),timeout)
        assertNotNull("Missing action: $text",node)
        node.click()
    }
    private fun screen(name:String) {
        val file=File(context.getExternalFilesDir(null),"acceptance/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        file.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
    @Test fun archiveRatingDoesNotReplaceActiveSessionAndOldSessionsMigrate() {
        val store=LiveDemoStorage(context)
        val active=LiveDemoSession(phase=LivePhase.DRIVING,carId="ford-transit",bookingId="QA-ACTIVE",drivingAt=1_800_000_000_000L)
        store.save(active)
        val receipt=LiveDemoSession(phase=LivePhase.RECEIPT,carId="gazelle-next",bookingId="QA-OLD",rating=4,finishedAt=1_800_000_030_000L)
        store.saveReceipt(receipt)
        assertEquals("QA-ACTIVE",store.load().bookingId)
        assertEquals(4,store.receipt("QA-OLD")!!.rating)
        store.saveReceipt(receipt.copy(rating=5))
        assertEquals(active,store.load())
        assertEquals(5,store.receipt("QA-OLD")!!.rating)
        store.save(LiveDemoSession())
    }
    @Test fun completeRentalAndCaptureReferenceScreens() {
        context.getSharedPreferences("gruzsher_live_demo_v1",0).edit().clear().commit()
        context.getSharedPreferences("gruzsher_demo_v2",0).edit().clear().commit()
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("Найти грузовик")),30_000))
        screen("01_home_fleet")
        tap("Выбрать →")
        screen("02_vehicle_details")
        screen("10_booking_legacy")
        tap("Продолжить к автопроверке · ДЕМО")
        tap("Получить SMS-код")
        assertTrue(device.wait(Until.hasObject(By.text("Подтвердить номер")),10_000))
        device.findObjects(By.clazz("android.widget.EditText")).last().text="2468"
        tap("Подтвердить номер")
        screen("03_auto_documents")
        tap("Привязать тестовую карту")
        assertTrue(device.wait(Until.hasObject(By.textContains("Принимаю учебные условия")),10_000))
        device.findObject(By.clazz("android.widget.CheckBox")).click()
        tap("Подтвердить и идти к автомобилю")
        assertTrue(device.wait(Until.hasObject(By.text("Осмотр перед поездкой")),25_000))
        listOf("Спереди","Сзади","Слева","Справа").forEachIndexed { i,side ->
            tap(side);tap("Добавить демофото")
            if(i==1) screen("04_pretrip_inspection")
        }
        tap("Подтвердить осмотр и открыть двери")
        tap("Начать поездку")
        tap("Ожидание")
        screen("05_active_trip")
        tap("Продлить +2 ч")
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.text("Вернуться к поездке")),10_000))
        tap("Вернуться к поездке")
        assertTrue(device.wait(Until.hasObject(By.text("Продолжить")),10_000))
        tap("Продолжить")
        tap("Завершить поездку")
        tap("Ехать к парковке")
        assertTrue(device.wait(Until.hasObject(By.text("Возврат грузовика")),30_000))
        screen("06_return_flow")
        tap("Сделать фото при возврате")
        val checked=device.findObject(By.clazz("android.widget.CheckBox"));assertNotNull(checked);checked.click()
        tap("Закрыть автомобиль")
        tap("Завершить аренду • чек")
        assertTrue(device.wait(Until.hasObject(By.text("Учебный чек")),10_000))
        screen("09_receipt_legacy")
        val store=LiveDemoStorage(context)
        assertEquals(LivePhase.RECEIPT,store.load().phase)
        assertTrue(store.load().locked)
        assertNotNull(store.receipt(store.load().bookingId))
        tap("Вернуться к автомобилям")
        tap("Поездки");screen("07_trips_history")
        tap("Профиль");screen("08_profile")
        device.pressBack();device.pressBack()
        assertTrue(device.hasObject(By.text("Найти грузовик")))
    }
}
