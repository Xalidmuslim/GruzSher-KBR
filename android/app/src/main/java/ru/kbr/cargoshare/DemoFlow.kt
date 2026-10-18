package ru.kbr.cargoshare

/**
 * Self-contained demonstration ONLY. No SMS, identity check, payment processing,
 * vehicle unlock, real telematics or operator notification is performed.
 */
enum class DemoAction {
    SMS_LOGIN, VERIFY_DOCUMENTS, LINK_PAYMENT, ADMIN_APPROVE,
    OPEN_DOORS, CLOSE_DOORS, BEFORE_PHOTOS, START_RIDE,
    EXTEND, REFUEL, RETURN_PHOTOS, FINISH_RIDE, SUPPORT, INCIDENT,
    TOGGLE_NOTIFICATIONS, RATE_RIDE
}

data class DemoState(
    val loggedIn: Boolean = false,
    val documentsVerified: Boolean = false,
    val paymentLinked: Boolean = false,
    val tripId: String? = null,
    val tripStatus: String = "NONE",
    val doorsOpen: Boolean = false,
    val beforePhotos: Boolean = false,
    val returnPhotos: Boolean = false,
    val extraHours: Int = 0,
    val fuelPercent: Int = 72,
    val receiptReady: Boolean = false,
    val supportRequested: Boolean = false,
    val incidentCount: Int = 0,
    val notifications: Boolean = true,
    val rated: Boolean = false,
    val events: List<String> = emptyList(),
)

data class DemoResult(val state: DemoState, val success: Boolean, val message: String)

private fun success(s: DemoState, message: String): DemoResult =
    DemoResult(s.copy(events = (s.events + message).takeLast(24)), true, message)

private fun blocked(s: DemoState, message: String): DemoResult = DemoResult(s, false, message)

fun chooseDemoBooking(state: DemoState, id: String, status: String): DemoResult {
    if (id.isBlank() || status in setOf("CANCELLED", "EXPIRED")) return blocked(state, "Выберите действующую учебную заявку")
    if (state.tripId == id) return DemoResult(state, true, "Заявка уже выбрана")
    val fresh = state.copy(
        tripId = id, tripStatus = status, doorsOpen = false, beforePhotos = false,
        returnPhotos = false, extraHours = 0, fuelPercent = 72,
        receiptReady = status == "COMPLETED", supportRequested = false,
        incidentCount = 0, rated = false, events = emptyList()
    )
    return success(fresh, "Выбрана учебная поездка № ${id.takeLast(6)}")
}

fun performDemoAction(s: DemoState, action: DemoAction): DemoResult = when (action) {
    DemoAction.SMS_LOGIN -> success(s.copy(loggedIn = true), "Демо-вход по SMS: тестовый код принят, SMS не отправлялось")
    DemoAction.VERIFY_DOCUMENTS -> {
        if (!s.loggedIn) blocked(s, "Сначала выполните учебный вход")
        else success(s.copy(documentsVerified = true), "Демо-проверка образцов паспорта и прав выполнена; настоящие документы не загружены")
    }
    DemoAction.LINK_PAYMENT -> {
        if (!s.loggedIn) blocked(s, "Сначала выполните учебный вход")
        else success(s.copy(paymentLinked = true), "Демо-карта •••• 0000 добавлена; данные банка не запрашивались")
    }
    DemoAction.ADMIN_APPROVE -> {
        if (s.tripStatus != "PENDING") blocked(s, "Для подтверждения нужна заявка в ожидании")
        else if (!s.loggedIn || !s.documentsVerified || !s.paymentLinked)
            blocked(s, "Нужны учебный вход, проверка документов и демо-карта")
        else success(s.copy(tripStatus = "CONFIRMED"), "Демо-оператор одобрил бронирование")
    }
    DemoAction.OPEN_DOORS -> {
        if (s.tripStatus !in setOf("CONFIRMED", "ACTIVE")) blocked(s, "Двери доступны после учебного подтверждения")
        else success(s.copy(doorsOpen = true), "Имитация открытия дверей: физическая команда автомобилю НЕ отправлена")
    }
    DemoAction.CLOSE_DOORS -> {
        if (s.tripStatus !in setOf("CONFIRMED", "ACTIVE")) blocked(s, "Нет действующей аренды")
        else if (!s.doorsOpen) blocked(s, "В учебном автомобиле двери уже закрыты")
        else success(s.copy(doorsOpen = false), "Имитация закрытия дверей: машина не управлялась")
    }
    DemoAction.BEFORE_PHOTOS -> {
        if (s.tripStatus != "CONFIRMED" || !s.doorsOpen) blocked(s, "Сначала подтвердите аренду и откройте учебный автомобиль")
        else success(s.copy(beforePhotos = true), "Демо-осмотр перед поездкой: образцы фотографий приняты")
    }
    DemoAction.START_RIDE -> {
        if (s.tripStatus != "CONFIRMED" || !s.doorsOpen || !s.beforePhotos)
            blocked(s, "Для старта необходимы подтверждение, открытие дверей и фотоосмотр")
        else success(s.copy(tripStatus = "ACTIVE"), "Учебная аренда началась; двигатель не запускается")
    }
    DemoAction.EXTEND -> {
        if (s.tripStatus != "ACTIVE") blocked(s, "Продление доступно во время учебной поездки")
        else success(s.copy(extraHours = (s.extraHours + 2).coerceAtMost(24)), "Демо-продление на 2 часа; реальная бронь не изменена")
    }
    DemoAction.REFUEL -> {
        if (s.tripStatus != "ACTIVE") blocked(s, "Заправка доступна только в учебной поездке")
        else success(s.copy(fuelPercent = (s.fuelPercent + 15).coerceAtMost(100)), "Демо-заправка: уровень топлива изменён только в симуляции")
    }
    DemoAction.RETURN_PHOTOS -> {
        if (s.tripStatus != "ACTIVE") blocked(s, "Сначала начните учебную аренду")
        else success(s.copy(returnPhotos = true), "Демо-фотоотчёт о возврате принят, файлы не загружались")
    }
    DemoAction.FINISH_RIDE -> {
        if (s.tripStatus != "ACTIVE") blocked(s, "Нет активной учебной поездки")
        else if (!s.returnPhotos || s.doorsOpen) blocked(s, "Перед завершением выполните осмотр возврата и закройте двери")
        else success(s.copy(tripStatus = "COMPLETED", receiptReady = true),
            "Демо-поездка завершена, виртуальный чек готов; списаний не было")
    }
    DemoAction.SUPPORT -> success(s.copy(supportRequested = true), "Демо-обращение в поддержку создано; оператору ничего не отправлено")
    DemoAction.INCIDENT -> {
        if (s.tripStatus !in setOf("CONFIRMED", "ACTIVE")) blocked(s, "Для демо-инцидента выберите текущую поездку")
        else success(s.copy(incidentCount = s.incidentCount + 1), "Демо-инцидент зарегистрирован только на телефоне")
    }
    DemoAction.TOGGLE_NOTIFICATIONS -> success(s.copy(notifications = !s.notifications),
        if (s.notifications) "Демо-уведомления выключены" else "Демо-уведомления включены")
    DemoAction.RATE_RIDE -> {
        if (!s.receiptReady) blocked(s, "Оценить можно только завершённую учебную поездку")
        else success(s.copy(rated = true), "Спасибо за демо-оценку — 5 звёзд")
    }
}
