package ru.kbr.cargoshare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ForestDemo = Color(0xFF26594C)
private val CanvasDemo = Color(0xFFF6F5F0)
private val InkDemo = Color(0xFF1B2A27)
private val PaleDemo = Color(0xFFE2EBE5)

@Composable
fun DemoCenter(
    state: DemoState,
    bookings: List<Booking>,
    cars: List<Vehicle>,
    notice: String?,
    onSelectBooking: (Booking) -> Unit,
    onAction: (DemoAction) -> Unit,
    onOpenMap: () -> Unit,
) {
    val booking = bookings.firstOrNull { it.id == state.tripId }
    Column(
        Modifier.fillMaxSize().background(CanvasDemo).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Полное демо", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = InkDemo)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PaleDemo)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Тестовый режим • без реальных операций", fontWeight = FontWeight.Bold, color = ForestDemo)
                Text("Здесь можно пройти весь путь клиента и оператора. Код SMS, документы, карта, деньги, замки и GPS автомобиля — только имитация. Никакие данные не отправляются.",
                    fontSize = 12.sp, lineHeight = 17.sp, color = InkDemo)
            }
        }
        if (!notice.isNullOrBlank()) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = ForestDemo, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(notice, fontSize = 12.sp, color = InkDemo, modifier = Modifier.weight(1f))
                }
            }
        }

        DemoSection("01 · Регистрация и допуск") {
            DemoActionRow("Вход по SMS", "Учебный номер +7 900 000-00-00 • код не отправляется",
                state.loggedIn, onClick = { onAction(DemoAction.SMS_LOGIN) })
            DemoActionRow("Паспорт и водительские права", "Проверка образцов документов, без камеры и загрузки",
                state.documentsVerified, onClick = { onAction(DemoAction.VERIFY_DOCUMENTS) })
            DemoActionRow("Банковская карта", "Только виртуальная карта •••• 0000, без списания",
                state.paymentLinked, onClick = { onAction(DemoAction.LINK_PAYMENT) })
            Text(
                if (state.loggedIn && state.documentsVerified && state.paymentLinked)
                    "Тестовый водитель допущен к учебному бронированию"
                else "Для учебного допуска выполните три шага выше",
                color = ForestDemo, fontSize = 12.sp
            )
        }

        DemoSection("02 · Карта и бронирование") {
            DemoActionRow("Карта Нальчика и GPS телефона", "Настоящая карта; геопозиция только с разрешения пользователя",
                false, onClick = onOpenMap)
            Text("Выберите тестовую заявку для полного прохождения аренды:", fontSize = 12.sp, color = InkDemo)
            if (bookings.none { it.status !in setOf("CANCELLED", "EXPIRED") }) {
                OutlinedButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.LocalShipping, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Выбрать грузовик и забронировать")
                }
            }
            bookings.filter { it.status !in setOf("CANCELLED", "EXPIRED") }.take(5).forEach { trip ->
                val name = cars.firstOrNull { it.id == trip.vehicleId }?.name ?: "Грузовик"
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectBooking(trip) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (state.tripId == trip.id) PaleDemo else CanvasDemo,
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (state.tripId == trip.id) Icons.Outlined.CheckCircle else Icons.Outlined.LocalShipping,
                            null, tint = ForestDemo)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${bookingStatusText(trip.status)} · ${trip.totalRub} ₽ (демо)",
                                fontSize = 11.sp, color = Color.Gray)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = ForestDemo)
                    }
                }
            }
        }
        DemoSection("03 · Оператор и доступ") {
            Text(if (booking == null) "Сначала выберите заявку выше" else
                "${cars.firstOrNull { it.id == booking.vehicleId }?.name ?: "Грузовик"} · ${bookingStatusText(state.tripStatus)}",
                color = ForestDemo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            DemoActionRow("Подтвердить заявку", "Симуляция действия администратора",
                state.tripStatus in setOf("CONFIRMED", "ACTIVE", "COMPLETED"),
                onClick = { onAction(DemoAction.ADMIN_APPROVE) })
            DemoActionRow("Открыть автомобиль", "Только анимация состояния замка • без оборудования",
                state.doorsOpen, onClick = { onAction(DemoAction.OPEN_DOORS) })
            DemoActionRow("Осмотр до поездки", "Тестовый акт и образцы фотографий",
                state.beforePhotos, onClick = { onAction(DemoAction.BEFORE_PHOTOS) })
            DemoActionRow("Начать поездку", "Учебный таймер и статусы аренды",
                state.tripStatus in setOf("ACTIVE", "COMPLETED"),
                onClick = { onAction(DemoAction.START_RIDE) })
        }
        DemoSection("04 · Управление поездкой") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoMetric("Статус", if (state.tripStatus == "ACTIVE") "В пути" else if (state.tripStatus == "COMPLETED") "Готово" else "Ожидание", Modifier.weight(1f))
                DemoMetric("Топливо", "${state.fuelPercent}%", Modifier.weight(1f))
                DemoMetric("Продлено", "+${state.extraHours} ч", Modifier.weight(1f))
            }
            DemoActionRow("Продлить на 2 часа", "Тестовая доплата, не меняет серверную бронь",
                state.extraHours > 0, onClick = { onAction(DemoAction.EXTEND) })
            DemoActionRow("Заправить автомобиль", "Изменяется только виртуальный индикатор топлива",
                state.fuelPercent > 72, onClick = { onAction(DemoAction.REFUEL) })
            DemoActionRow("Закрыть двери", "Виртуальная блокировка автомобиля",
                !state.doorsOpen && state.tripStatus == "ACTIVE",
                onClick = { onAction(DemoAction.CLOSE_DOORS) })
        }
        DemoSection("05 · Возврат и расчёт") {
            DemoActionRow("Фотоосмотр при возврате", "Симуляция фиксации состояния кузова",
                state.returnPhotos, onClick = { onAction(DemoAction.RETURN_PHOTOS) })
            DemoActionRow("Завершить аренду", "Только виртуальный расчёт и закрытие заказа",
                state.receiptReady, onClick = { onAction(DemoAction.FINISH_RIDE) })
            if (state.receiptReady && booking != null) {
                Card(colors = CardDefaults.cardColors(containerColor = PaleDemo), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ReceiptLong, null, tint = ForestDemo)
                            Spacer(Modifier.width(8.dp))
                            Text("Учебный чек", fontWeight = FontWeight.Bold, color = InkDemo)
                        }
                        Text("Сумма по демотарифу: ${booking.totalRub} ₽")
                        Text("Реальное списание: 0 ₽", fontWeight = FontWeight.Bold, color = ForestDemo)
                        Text("Не является кассовым или платёжным документом", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
            DemoActionRow("Оценить поездку ★★★★★", "Учебная оценка после возврата",
                state.rated, onClick = { onAction(DemoAction.RATE_RIDE) })
        }
        DemoSection("06 · Поддержка и безопасность") {
            DemoActionRow("Обратиться в поддержку", "Пример обращения, не доставляется оператору",
                state.supportRequested, onClick = { onAction(DemoAction.SUPPORT) })
            DemoActionRow("Сообщить о происшествии", "Учебный журнал ДТП и повреждений",
                state.incidentCount > 0, onClick = { onAction(DemoAction.INCIDENT) })
            DemoActionRow("Демо-уведомления", "Переключатель только внутри приложения",
                state.notifications, onClick = { onAction(DemoAction.TOGGLE_NOTIFICATIONS) })
            Text("При реальном ДТП используйте экстренные службы и контакт настоящего оператора. Этот деморежим помощь не вызывает.",
                fontSize = 11.sp, color = Color.Gray)
        }
        DemoSection("07 · История операций") {
            if (state.events.isEmpty()) {
                Text("Пока нет событий. Пройдите учебный сценарий.", fontSize = 12.sp, color = Color.Gray)
            } else {
                state.events.asReversed().forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = ForestDemo, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(entry, fontSize = 12.sp, lineHeight = 16.sp, color = InkDemo)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DemoMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = PaleDemo) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = Color.Gray, fontSize = 10.sp)
            Text(value, color = ForestDemo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun DemoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            content = {
                Text(title, color = InkDemo, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                HorizontalDivider(color = PaleDemo)
                content()
            },
        )
    }
}

@Composable
private fun DemoActionRow(title: String, subtitle: String, done: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = if (done) PaleDemo else CanvasDemo,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.ChevronRight,
                contentDescription = null, tint = ForestDemo, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = InkDemo, fontSize = 14.sp)
                Text(subtitle, color = Color.DarkGray, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}
