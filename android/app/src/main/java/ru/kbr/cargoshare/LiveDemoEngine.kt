package ru.kbr.cargoshare

import kotlin.math.min
import kotlin.math.roundToInt

/** Offline training. Clock-based simulation, never sends SMS, charges cards or commands vehicles. */
enum class LivePhase { CHOOSE, PHONE, DOCUMENTS, PAYMENT, CONFIRM, APPROVAL, WALK, INSPECT, UNLOCK, READY, DRIVING, RETURN, RECEIPT }

data class LiveDemoSession(
    val phase: LivePhase = LivePhase.CHOOSE,
    val carId: String = "",
    val phone: String = "",
    val codeRequestedAt: Long = 0L,
    val passportStartedAt: Long = 0L,
    val licenceStartedAt: Long = 0L,
    val paymentStartedAt: Long = 0L,
    val durationHours: Int = 2,
    val approvalStartedAt: Long = 0L,
    val approachStartedAt: Long = 0L,
    val inspected: Set<String> = emptySet(),
    val damageRecorded: Boolean = false,
    val unlockingAt: Long = 0L,
    val drivingAt: Long = 0L,
    val returnPhotoAt: Long = 0L,
    val lockingAt: Long = 0L,
    val locked: Boolean = false,
    val extraHours: Int = 0,
    val pauseStartedAt: Long = 0L,
    val pausedSeconds: Long = 0L,
    val assistance: Boolean = false,
    val incident: Boolean = false,
    val rating: Int = 0,
    val bookingId: String = "",
    val events: List<String> = emptyList()
)

enum class LiveEvent {
    PICK_CAR, REQUEST_CODE, VERIFY_CODE, SCAN_PASSPORT, SCAN_LICENCE, START_PAYMENT,
    SET_DURATION, REQUEST_BOOKING, CAPTURE_ANGLE, REPORT_DAMAGE, UNLOCK, START_DRIVE,
    EXTEND, TOGGLE_WAIT, ASK_SUPPORT, REPORT_INCIDENT, TAKE_RETURN_PHOTO, LOCK_DOORS,
    FINISH, SET_RATING, RESET
}

data class LiveOutcome(val state: LiveDemoSession, val accepted: Boolean, val message: String)
private fun valid(s: LiveDemoSession, text: String): LiveOutcome =
    LiveOutcome(s.copy(events = (s.events + text).takeLast(30)), true, text)
private fun invalid(s: LiveDemoSession, text: String): LiveOutcome = LiveOutcome(s, false, text)

const val LIVE_ROUTE_MS = 75_000L
const val LIVE_APPROACH_MS = 12_000L
const val LIVE_OPERATION_MS = 2_500L

fun routeFraction(s: LiveDemoSession, now: Long): Float =
    if (s.drivingAt == 0L) 0f else
        ((now - s.drivingAt - s.pausedSeconds * 1000L -
            if (s.pauseStartedAt > 0) now - s.pauseStartedAt else 0L).coerceAtLeast(0L).toFloat() / LIVE_ROUTE_MS).coerceIn(0f, 1f)

fun elapsedRideSeconds(s: LiveDemoSession, now: Long): Long =
    if (s.drivingAt == 0L) 0L else
        ((now - s.drivingAt - s.pausedSeconds * 1000L -
            if (s.pauseStartedAt > 0) now - s.pauseStartedAt else 0L).coerceAtLeast(0L) / 1000L)

fun demoFare(s: LiveDemoSession, hourlyPriceRub: Int, now: Long): Int {
    val base = (s.durationHours + s.extraHours) * hourlyPriceRub
    val drivingSurcharge = (min(elapsedRideSeconds(s, now), LIVE_ROUTE_MS / 1000L).toDouble() * hourlyPriceRub / 3600.0).roundToInt()
    return base + drivingSurcharge
}

/** Idempotent time transitions: resilient to Android lifecycle pauses and device restarts. */
fun advanceLive(s: LiveDemoSession, now: Long): LiveDemoSession = when {
    s.phase == LivePhase.DOCUMENTS && s.passportStartedAt > 0L &&
      s.licenceStartedAt > 0L && now >= maxOf(s.passportStartedAt, s.licenceStartedAt) + LIVE_OPERATION_MS ->
        s.copy(phase = LivePhase.PAYMENT)
    s.phase == LivePhase.PAYMENT && s.paymentStartedAt > 0L && now >= s.paymentStartedAt + LIVE_OPERATION_MS ->
        s.copy(phase = LivePhase.CONFIRM)
    s.phase == LivePhase.APPROVAL && now >= s.approvalStartedAt + 4_000L ->
        s.copy(phase = LivePhase.WALK, approachStartedAt = now)
    s.phase == LivePhase.WALK && now >= s.approachStartedAt + LIVE_APPROACH_MS ->
        s.copy(phase = LivePhase.INSPECT)
    s.phase == LivePhase.UNLOCK && now >= s.unlockingAt + LIVE_OPERATION_MS ->
        s.copy(phase = LivePhase.READY)
    s.phase == LivePhase.DRIVING && routeFraction(s, now) >= 1f ->
        s.copy(phase = LivePhase.RETURN, pauseStartedAt = 0L)
    else -> s
}

fun applyLive(s: LiveDemoSession, event: LiveEvent, now: Long, value: String = ""): LiveOutcome {
    val c = advanceLive(s, now)
    return when (event) {
        LiveEvent.RESET -> valid(LiveDemoSession(), "Учебный сценарий сброшен")
        LiveEvent.PICK_CAR -> if (c.phase != LivePhase.CHOOSE || value.isBlank()) invalid(c, "Выберите грузовик")
            else valid(c.copy(phase = LivePhase.PHONE, carId = value), "Автомобиль выбран")
        LiveEvent.REQUEST_CODE -> if (c.phase != LivePhase.PHONE || !value.matches(Regex("^\\+?7[0-9]{10}$")))
            invalid(c, "Введите учебный номер +7 и 10 цифр")
            else valid(c.copy(phone = value, codeRequestedAt = now), "Имитируем доставку SMS — код появится через 3 секунды")
        LiveEvent.VERIFY_CODE -> if (c.phase != LivePhase.PHONE || c.codeRequestedAt == 0L || now - c.codeRequestedAt < 3000L || value != "2468")
            invalid(c, "Дождитесь учебного SMS и введите показанный код")
            else valid(c.copy(phase = LivePhase.DOCUMENTS), "Учебный номер подтверждён")
        LiveEvent.SCAN_PASSPORT -> if (c.phase != LivePhase.DOCUMENTS || c.passportStartedAt != 0L)
            invalid(c, "Сканирование недоступно")
            else valid(c.copy(passportStartedAt = now), "Обрабатываем образец паспорта…")
        LiveEvent.SCAN_LICENCE -> if (c.phase != LivePhase.DOCUMENTS || c.licenceStartedAt != 0L)
            invalid(c, "Сканирование недоступно")
            else valid(c.copy(licenceStartedAt = now), "Обрабатываем образец водительских прав…")
        LiveEvent.START_PAYMENT -> if (c.phase != LivePhase.PAYMENT || c.paymentStartedAt != 0L)
            invalid(c, "Демонстрационная карта уже проверяется")
            else valid(c.copy(paymentStartedAt = now), "Проверяем виртуальную карту •••• 0000…")
        LiveEvent.SET_DURATION -> if (c.phase != LivePhase.CONFIRM || value.toIntOrNull() !in listOf(2, 4, 8))
            invalid(c, "Выберите доступный срок")
            else valid(c.copy(durationHours = value.toInt()), "Длительность обновлена")
        LiveEvent.REQUEST_BOOKING -> if (c.phase != LivePhase.CONFIRM)
            invalid(c, "Завершите регистрацию и проверку")
            else valid(c.copy(phase = LivePhase.APPROVAL, approvalStartedAt = now, bookingId = "SIM-${now.toString().takeLast(7)}"),
                "Запрос отправлен учебному оператору")
        LiveEvent.CAPTURE_ANGLE -> if (c.phase != LivePhase.INSPECT || value !in setOf("Спереди", "Сзади", "Слева", "Справа"))
            invalid(c, "Выберите сторону грузовика")
            else valid(c.copy(inspected = c.inspected + value), "Симуляция снимка: $value")
        LiveEvent.REPORT_DAMAGE -> if (c.phase != LivePhase.INSPECT) invalid(c, "Осмотрите автомобиль")
            else valid(c.copy(damageRecorded = true), "Учебное повреждение добавлено в акт")
        LiveEvent.UNLOCK -> if (c.phase != LivePhase.INSPECT || c.inspected.size != 4)
            invalid(c, "Нужно осмотреть автомобиль со всех четырёх сторон")
            else valid(c.copy(phase = LivePhase.UNLOCK, unlockingAt = now), "Отправляем симулированную команду открытия…")
        LiveEvent.START_DRIVE -> if (c.phase != LivePhase.READY)
            invalid(c, "Дождитесь виртуального открытия дверей")
            else valid(c.copy(phase = LivePhase.DRIVING, drivingAt = now), "Поездка началась, маркер движется по учебному маршруту")
        LiveEvent.EXTEND -> if (c.phase != LivePhase.DRIVING) invalid(c, "Продление доступно во время поездки")
            else valid(c.copy(extraHours = (c.extraHours + 2).coerceAtMost(24)), "Учебная аренда продлена на 2 часа")
        LiveEvent.TOGGLE_WAIT -> if (c.phase != LivePhase.DRIVING) invalid(c, "Ожидание доступно во время поездки")
            else if (c.pauseStartedAt == 0L) valid(c.copy(pauseStartedAt = now), "Автомобиль остановлен: ожидание")
            else valid(c.copy(pauseStartedAt = 0L, pausedSeconds = c.pausedSeconds + (now - c.pauseStartedAt) / 1000L),
                "Движение продолжено")
        LiveEvent.ASK_SUPPORT -> if (c.phase != LivePhase.DRIVING) invalid(c, "Поддержка доступна во время поездки")
            else valid(c.copy(assistance = true), "Учебный оператор: «Здравствуйте! Чем помочь?»")
        LiveEvent.REPORT_INCIDENT -> if (c.phase != LivePhase.DRIVING) invalid(c, "Нет поездки")
            else valid(c.copy(incident = true), "Учебное обращение о происшествии сохранено")
        LiveEvent.TAKE_RETURN_PHOTO -> if (c.phase != LivePhase.RETURN || c.returnPhotoAt != 0L) invalid(c, "Дождитесь точки возврата")
            else valid(c.copy(returnPhotoAt = now), "Фиксируем фото при возврате…")
        LiveEvent.LOCK_DOORS -> if (c.phase != LivePhase.RETURN || c.returnPhotoAt == 0L || now < c.returnPhotoAt + LIVE_OPERATION_MS)
            invalid(c, "Сначала сфотографируйте машину при возврате")
            else valid(c.copy(lockingAt = now), "Отправляем учебную команду закрытия…")
        LiveEvent.FINISH -> if (c.phase != LivePhase.RETURN || c.lockingAt == 0L || now < c.lockingAt + LIVE_OPERATION_MS)
            invalid(c, "Проверьте фото и дождитесь закрытия дверей")
            else valid(c.copy(phase = LivePhase.RECEIPT, locked = true), "Учебная поездка завершена; реального списания нет")
        LiveEvent.SET_RATING -> if (c.phase != LivePhase.RECEIPT || value.toIntOrNull() !in 1..5)
            invalid(c, "Укажите оценку")
            else valid(c.copy(rating = value.toInt()), "Спасибо за отзыв!")
    }
}
