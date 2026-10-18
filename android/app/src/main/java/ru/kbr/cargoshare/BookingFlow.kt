package ru.kbr.cargoshare

/** Pure presentation rules; the server, not the client, owns rental state. */
data class BookingIntent(val vehicleId: String, val start: String, val end: String)

fun bookingStage(status: String): Int = when (status) {
    "PENDING" -> 0
    "CONFIRMED" -> 1
    "ACTIVE" -> 2
    "COMPLETED" -> 3
    else -> -1
}

fun canCancelBooking(status: String): Boolean = status == "PENDING" || status == "CONFIRMED"

fun bookingStatusText(status: String): String = when (status) {
    "PENDING" -> "Ожидает подтверждения"
    "CONFIRMED" -> "Ожидает ручной выдачи"
    "ACTIVE" -> "Автомобиль передан (демо)"
    "COMPLETED" -> "Аренда завершена (демо)"
    "CANCELLED" -> "Отменено"
    "EXPIRED" -> "Время подтверждения истекло"
    else -> "Статус неизвестен"
}

fun bookingEventText(type: String): String = when (type) {
    "CREATED" -> "Заявка создана"
    "CONFIRMED" -> "Заявка подтверждена"
    "HANDOVER" -> "Выдача отмечена администратором (демо)"
    "RETURNED" -> "Возврат отмечен администратором (демо)"
    "CANCELLED" -> "Заявка отменена"
    "EXPIRED" -> "Заявка автоматически истекла"
    else -> "Неизвестное событие"
}
