package ru.kbr.cargoshare

import kotlin.math.min
import kotlin.math.roundToInt

/** Offline training. Clock-based simulation, never sends SMS, charges cards or commands vehicles. */
enum class LivePhase { CHOOSE, PHONE, DOCUMENTS, PAYMENT, CONFIRM, APPROVAL, WALK, INSPECT, UNLOCK, READY, DRIVING, RETURNING, RETURN, RECEIPT }

data class LiveDemoSession(
    val phase: LivePhase = LivePhase.CHOOSE,
    /** Only completed training rentals unlock fast checkout; never real verification. */
    val returningDemoDriver: Boolean = false,
    val carId: String = "",
    val phone: String = "",
    val codeRequestedAt: Long = 0L,
    val passportStartedAt: Long = 0L,
    val licenceStartedAt: Long = 0L,
    val documentIssue: String = "", // optional simulated validation failure; never real OCR
    val paymentStartedAt: Long = 0L,
    val durationHours: Int = 2,
    val approvalStartedAt: Long = 0L,
    val approachStartedAt: Long = 0L,
    val inspected: Set<String> = emptySet(),
    val damageRecorded: Boolean = false,
    val termsAccepted: Boolean = false,
    val returnConditionChecked: Boolean = false,
    val unlockingAt: Long = 0L,
    val drivingAt: Long = 0L,
    val returnRequestedAt: Long = 0L,
    val returnStartFraction: Float = 0f,
    val finishedAt: Long = 0L,
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
    PICK_CAR, REQUEST_CODE, VERIFY_CODE, SCAN_PASSPORT, SCAN_LICENCE, FAIL_DOCUMENT_CHECK, RETRY_DOCUMENT_CHECK, START_PAYMENT,
    SET_DURATION, ACCEPT_TERMS, CHECK_RETURN_CONDITION, REQUEST_BOOKING, CAPTURE_ANGLE, REPORT_DAMAGE, UNLOCK, START_DRIVE,
    EXTEND, TOGGLE_WAIT, ASK_SUPPORT, REPORT_INCIDENT, REQUEST_RETURN, TAKE_RETURN_PHOTO, LOCK_DOORS,
    FINISH, SET_RATING, RESET
}

data class LiveOutcome(val state: LiveDemoSession, val accepted: Boolean, val message: String)
private fun valid(s: LiveDemoSession, text: String): LiveOutcome =
    LiveOutcome(s.copy(events = (s.events + text).takeLast(30)), true, text)
private fun invalid(s: LiveDemoSession, text: String): LiveOutcome = LiveOutcome(s, false, text)

const val LIVE_ROUTE_MS = 75_000L
const val LIVE_ROUTE_DISTANCE_KM = 0.6 // 600 m in 75 s: approximately 29 km/h
const val LIVE_APPROACH_MS = 12_000L
const val LIVE_OPERATION_MS = 2_500L
const val LIVE_RETURN_MS = 16_000L

fun routeFraction(s: LiveDemoSession, now: Long): Float {
    if (s.drivingAt == 0L) return 0f
    if (s.phase == LivePhase.RETURN || s.phase == LivePhase.RECEIPT) return 1f
    if (s.phase == LivePhase.RETURNING && s.returnRequestedAt > 0L) {
        val portion = ((now - s.returnRequestedAt).toFloat() / LIVE_RETURN_MS).coerceIn(0f, 1f)
        return (s.returnStartFraction + (1f - s.returnStartFraction) * portion).coerceIn(0f, 1f)
    }
    return (((if (s.finishedAt > 0L) s.finishedAt else now) - s.drivingAt - s.pausedSeconds * 1000L -
        if (s.pauseStartedAt > 0) now - s.pauseStartedAt else 0L).coerceAtLeast(0L).toFloat() / LIVE_ROUTE_MS).coerceIn(0f, 1f)
}

/** Route metrics must derive from a single clock and single distance model. */
fun demoDistanceKm(s: LiveDemoSession, now: Long): Double =
    routeFraction(s, now).toDouble() * LIVE_ROUTE_DISTANCE_KM

fun demoSpeedKmh(s: LiveDemoSession): Int =
    if (s.phase != LivePhase.DRIVING || s.pauseStartedAt > 0L) 0
    else (LIVE_ROUTE_DISTANCE_KM * 3_600_000.0 / LIVE_ROUTE_MS).roundToInt()

fun elapsedRideSeconds(s: LiveDemoSession, now: Long): Long =
    if (s.drivingAt == 0L) 0L else
        (((if (s.finishedAt > 0L) s.finishedAt else now) - s.drivingAt - s.pausedSeconds * 1000L -
            if (s.pauseStartedAt > 0) now - s.pauseStartedAt else 0L).coerceAtLeast(0L) / 1000L)

/** Fixed demo package price. Driving seconds are included, not billed a second time.
 * The clock is intentionally a simulator clock and is not connected to banking.
 */
fun demoFare(s: LiveDemoSession, hourlyPriceRub: Int, now: Long): Int {
    val hours = (s.durationHours + s.extraHours).coerceAtLeast(0)
    val daily = offlineDemoCatalog().firstOrNull { it.id == s.carId }?.dailyPriceRub
        ?: return hours * hourlyPriceRub
    return (hours / 24) * daily + minOf((hours % 24) * hourlyPriceRub, daily)
}

/** Idempotent time transitions: resilient to Android lifecycle pauses and device restarts. */
fun advanceLive(s: LiveDemoSession, now: Long): LiveDemoSession = when {
    // Pre-v3.4 sessions may have no scan timestamps: start an automatic demo check.
    s.phase == LivePhase.DOCUMENTS && s.documentIssue.isEmpty() &&
      (s.passportStartedAt == 0L || s.licenceStartedAt == 0L) ->
        s.copy(passportStartedAt = now, licenceStartedAt = now)
    s.phase == LivePhase.DOCUMENTS && s.documentIssue.isEmpty() &&
      s.passportStartedAt > 0L && s.licenceStartedAt > 0L &&
      now >= maxOf(s.passportStartedAt, s.licenceStartedAt) + LIVE_OPERATION_MS ->
        s.copy(phase = LivePhase.PAYMENT, events = (s.events + "Демопроверка образцов пройдена автоматически").takeLast(30))
    s.phase == LivePhase.PAYMENT && s.paymentStartedAt > 0L && now >= s.paymentStartedAt + LIVE_OPERATION_MS ->
        s.copy(phase = LivePhase.CONFIRM)
    // Migrate saved v3.3 sessions: no approval wait remains in v3.4.
    s.phase == LivePhase.APPROVAL ->
        s.copy(phase = LivePhase.WALK, approachStartedAt = now)
    s.phase == LivePhase.WALK && now >= s.approachStartedAt + LIVE_APPROACH_MS ->
        s.copy(phase = LivePhase.INSPECT)
    s.phase == LivePhase.UNLOCK && now >= s.unlockingAt + LIVE_OPERATION_MS ->
        s.copy(phase = LivePhase.READY)
    s.phase == LivePhase.RETURNING && now >= s.returnRequestedAt + LIVE_RETURN_MS ->
        s.copy(phase = LivePhase.RETURN, pauseStartedAt = 0L)
    s.phase == LivePhase.DRIVING && routeFraction(s, now) >= 1f ->
        s.copy(phase = LivePhase.RETURN, pauseStartedAt = 0L)
    else -> s
}

fun applyLive(s: LiveDemoSession, event: LiveEvent, now: Long, value: String = ""): LiveOutcome {
    val c = advanceLive(s, now)
    return when (event) {
        LiveEvent.RESET -> valid(LiveDemoSession(returningDemoDriver =
            c.returningDemoDriver || c.phase == LivePhase.RECEIPT),
            "Начните следующую аренду: повторная учебная регистрация не требуется")
        LiveEvent.PICK_CAR -> if (c.phase != LivePhase.CHOOSE || value !in offlineDemoCatalog().map { it.id }) invalid(c, "Выберите грузовик из учебного каталога")
            else valid(c.copy(
                phase = if (c.returningDemoDriver) LivePhase.CONFIRM else LivePhase.PHONE,
                carId = value, termsAccepted = false, durationHours = 2),
                if (c.returningDemoDriver)
                    "Повторная учебная аренда: номер, документы и виртуальная карта сохранены как пройденные"
                else "Автомобиль выбран")
        LiveEvent.REQUEST_CODE -> if (c.phase != LivePhase.PHONE || !value.matches(Regex("^\\+?7[0-9]{10}$")))
            invalid(c, "Введите учебный номер +7 и 10 цифр")
            else valid(c.copy(phone = value, codeRequestedAt = now), "Имитируем доставку SMS — код появится через 3 секунды")
        LiveEvent.VERIFY_CODE -> if (c.phase != LivePhase.PHONE || c.codeRequestedAt == 0L || now - c.codeRequestedAt < 3000L || value != "2468")
            invalid(c, "Дождитесь учебного SMS и введите показанный код")
            else valid(c.copy(phase = LivePhase.DOCUMENTS, passportStartedAt = now,
                licenceStartedAt = now, documentIssue = ""), "Учебный номер подтверждён · началась автоматическая демопроверка образцов")
        // Legacy manual triggers are disabled. Real document scans are not performed in offline demo.
        LiveEvent.SCAN_PASSPORT, LiveEvent.SCAN_LICENCE ->
            invalid(c, "Демопроверка запускается автоматически; реальные документы не используются")
        LiveEvent.FAIL_DOCUMENT_CHECK ->
            if (c.phase != LivePhase.DOCUMENTS || value !in setOf("Нечитаемый документ", "Истёк срок", "Неверная категория"))
                invalid(c, "Тестовый сценарий недоступен")
            else valid(c.copy(documentIssue = value), "Тест: $value. Повторите проверку")
        LiveEvent.RETRY_DOCUMENT_CHECK ->
            if (c.phase != LivePhase.DOCUMENTS || c.documentIssue.isBlank())
                invalid(c, "Повторная проверка не требуется")
            else valid(c.copy(documentIssue = "", passportStartedAt = now, licenceStartedAt = now),
                "Демопроверка запущена повторно")
        LiveEvent.START_PAYMENT -> if (c.phase != LivePhase.PAYMENT || c.paymentStartedAt != 0L)
            invalid(c, "Демонстрационная карта уже проверяется")
            else valid(c.copy(paymentStartedAt = now), "Проверяем виртуальную карту •••• 0000…")
        LiveEvent.SET_DURATION -> if (c.phase != LivePhase.CONFIRM || value.toIntOrNull() !in listOf(2, 4, 8, 24))
            invalid(c, "Выберите доступный срок")
            else valid(c.copy(durationHours = value.toInt()), "Длительность обновлена")
        LiveEvent.ACCEPT_TERMS -> if(c.phase != LivePhase.CONFIRM) invalid(c,"Условия доступны перед бронированием")
            else valid(c.copy(termsAccepted=value=="true"),"Согласие с учебными условиями обновлено")
        LiveEvent.CHECK_RETURN_CONDITION -> if(c.phase != LivePhase.RETURN) invalid(c,"Проверка доступна в зоне возврата")
            else valid(c.copy(returnConditionChecked=value=="true"),"Состояние кузова и салона подтверждено · демо")
        LiveEvent.REQUEST_BOOKING -> if (c.phase != LivePhase.CONFIRM || !c.termsAccepted)
            invalid(c, "Завершите регистрацию и проверку")
            else valid(c.copy(phase = LivePhase.WALK, approvalStartedAt = now, approachStartedAt = now,
                bookingId = "SIM-${now.toString().takeLast(7)}"),
                "Учебная бронь подтверждена автоматически. Идём к машине")
        LiveEvent.CAPTURE_ANGLE -> if (c.phase != LivePhase.INSPECT || value !in setOf("Спереди", "Сзади", "Слева", "Справа"))
            invalid(c, "Выберите сторону грузовика")
            else if(value in c.inspected) invalid(c,"Эта сторона уже осмотрена")
            else valid(c.copy(inspected = c.inspected + value), "Симуляция снимка: $value")
        LiveEvent.REPORT_DAMAGE -> if (c.phase != LivePhase.INSPECT) invalid(c, "Осмотрите автомобиль")
            else valid(c.copy(damageRecorded = true), "Учебное повреждение: ${value.trim().take(240).ifBlank { "отметка водителя" }} · сгенерированное демофото")
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
            else valid(c.copy(assistance = true), "Демо-чат: ${value.trim().take(240).ifBlank { "Здравствуйте! Чем помочь?" }}")
        LiveEvent.REPORT_INCIDENT -> if (c.phase != LivePhase.DRIVING) invalid(c, "Нет поездки")
            else valid(c.copy(incident = true), "Учебное происшествие: ${value.trim().take(240).ifBlank { "обращение водителя" }}")
        LiveEvent.REQUEST_RETURN -> if (c.phase != LivePhase.DRIVING)
            invalid(c, "Вернуться на стоянку можно только во время учебной поездки")
            else {
                val fraction = routeFraction(c, now)
                val pauseSeconds = if (c.pauseStartedAt > 0L) ((now - c.pauseStartedAt) / 1000L).coerceAtLeast(0L) else 0L
                valid(c.copy(phase = LivePhase.RETURNING, returnRequestedAt = now,
                    returnStartFraction = fraction, pausedSeconds = c.pausedSeconds + pauseSeconds,
                    pauseStartedAt = 0L),
                    "Прокладываем демонстрационный маршрут к зоне возврата — прибытие примерно через 16 секунд")
            }
        LiveEvent.TAKE_RETURN_PHOTO -> if (c.phase != LivePhase.RETURN || c.returnPhotoAt != 0L) invalid(c, "Дождитесь точки возврата")
            else valid(c.copy(returnPhotoAt = now), "Фиксируем фото при возврате…")
        LiveEvent.LOCK_DOORS -> if (c.phase != LivePhase.RETURN || c.returnPhotoAt == 0L || c.lockingAt != 0L || !c.returnConditionChecked || now < c.returnPhotoAt + LIVE_OPERATION_MS)
            invalid(c, "Сначала сфотографируйте машину при возврате")
            else valid(c.copy(lockingAt = now), "Отправляем учебную команду закрытия…")
        LiveEvent.FINISH -> if (c.phase != LivePhase.RETURN || c.lockingAt == 0L || now < c.lockingAt + LIVE_OPERATION_MS)
            invalid(c, "Проверьте фото и дождитесь закрытия дверей")
            else valid(c.copy(phase = LivePhase.RECEIPT, locked = true,
                finishedAt = now, returningDemoDriver = true),
                "Учебная поездка завершена; следующий выбор грузовика будет без повторной регистрации")
        LiveEvent.SET_RATING -> if (c.phase != LivePhase.RECEIPT || value.toIntOrNull() !in 1..5)
            invalid(c, "Укажите оценку")
            else valid(c.copy(rating = value.toInt()), "Спасибо за отзыв!")
    }
}
