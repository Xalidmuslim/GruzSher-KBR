package ru.kbr.cargoshare

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Dark = Color(0xFF173D33)
private val Jade = Color(0xFF185745)
private val Paper = Color(0xFFF6F7F3)
private val Pale = Color(0xFFE8F1EB)
private val Quiet = Color(0xFF73827B)
private val ErrorRed = Color(0xFFA33F38)
private val Moscow = ZoneId.of("Europe/Moscow")
private val DateRu = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru-RU"))

private fun readableDate(value: String): String =
    runCatching { OffsetDateTime.parse(value).atZoneSameInstant(Moscow).format(DateRu) }
        .getOrElse { value.take(16).replace('T', ' ') }

private fun liveLabel(s: LiveDemoSession): String = when (s.phase) {
    LivePhase.CHOOSE -> "Не начата"
    LivePhase.PHONE -> "Вход в демо"
    LivePhase.DOCUMENTS -> "Учебная проверка"
    LivePhase.PAYMENT -> "Учебная карта"
    LivePhase.CONFIRM -> "Выбор тарифа"
    LivePhase.APPROVAL -> "Ожидание подтверждения"
    LivePhase.WALK -> "Идём к грузовику"
    LivePhase.INSPECT -> "Осмотр автомобиля"
    LivePhase.UNLOCK -> "Открытие машины"
    LivePhase.READY -> "Готов к поездке"
    LivePhase.DRIVING -> "В пути"
    LivePhase.RETURNING -> "Возвращаемся к стоянке"
    LivePhase.RETURN -> "Фотоосмотр при возврате"
    LivePhase.RECEIPT -> "Завершена"
}

private fun ticketLabel(b: Booking, live: LiveDemoSession): String =
    if (b.id == live.bookingId && live.bookingId.isNotBlank()) liveLabel(live)
    else when (b.status) {
        "PENDING" -> "Ожидает подтверждения"
        "CONFIRMED" -> "Подтверждена"
        "ACTIVE" -> "Активная поездка"
        "COMPLETED" -> "Завершена"
        "CANCELLED" -> "Отменена"
        "EXPIRED" -> "Истекла"
        else -> b.status
    }

private fun isCurrent(b: Booking): Boolean =
    b.status !in setOf("COMPLETED", "CANCELLED", "EXPIRED")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsDashboard(
    bookings: List<Booking>,
    cars: List<Vehicle>,
    live: LiveDemoSession,
    offline: Boolean,
    onRefresh: () -> Unit,
    onResume: (Booking) -> Unit,
    onTrain: (Booking) -> Unit,
    onBrowse: () -> Unit,
    onCancel: (String) -> Unit,
    onTimeline: suspend (String) -> List<BookingTimelineEvent>,
) {
    var tab by remember { mutableStateOf("Текущие") }
    var showing by remember { mutableStateOf<Booking?>(null) }
    var cancelPending by remember { mutableStateOf<Booking?>(null) }
    val visible = remember(bookings, tab) {
        val rows = bookings.filter {
            if (tab == "Текущие") isCurrent(it) else !isCurrent(it)
        }
        rows.sortedByDescending { it.startAt }
    }
    val activeCount = bookings.count(::isCurrent)

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 15.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Мои поездки", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Dark)
                Text(if (offline) "Учебные бронирования · без списаний" else "Заявки и история аренды",
                    fontSize = 12.sp, color = Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!offline) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить поездки", tint = Jade)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("Текущие", "История").forEach { label ->
                FilterChip(
                    selected = tab == label,
                    onClick = { tab = label },
                    label = {
                        Text(if (label == "Текущие") "Текущие ($activeCount)" else "История",
                            fontSize = 13.sp)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Jade, selectedLabelColor = Color.White,
                        containerColor = Color.White),
                )
            }
        }
        if (offline) {
            Text("LIVE-поездки обновляются по этапам. Учебные бронирования на дату не подтверждаются оператором.",
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                color = Quiet, fontSize = 11.sp)
        }
        if (visible.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp)
                    .background(Color.White, RoundedCornerShape(23.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Icon(if (tab == "Текущие") Icons.Outlined.LocalShipping
                     else Icons.Outlined.ReceiptLong,
                    null, tint = Jade, modifier = Modifier.size(32.dp))
                Text(if (tab == "Текущие") "Нет текущих бронирований"
                     else "История пока пуста",
                    fontSize = 18.sp, color = Dark, fontWeight = FontWeight.Bold)
                Text(if (tab == "Текущие") "Выберите грузовик и начните учебную аренду."
                     else "Завершённые и отменённые бронирования появятся здесь.",
                    color = Quiet, fontSize = 12.sp)
                if (tab == "Текущие") Button(onClick = onBrowse) {
                    Text("Выбрать грузовик")
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(11.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                items(visible, key = { it.id }) { b ->
                    val isLive = b.id == live.bookingId && live.bookingId.isNotBlank()
                    val name = cars.firstOrNull { it.id == b.vehicleId }?.name ?: "Грузовой автомобиль"
                    Card(
                        shape = RoundedCornerShape(23.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Dark)
                                    Spacer(Modifier.height(5.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (b.status == "CANCELLED" || b.status == "EXPIRED")
                                            Color(0xFFFFEDE9) else Pale,
                                    ) {
                                        Text(if (offline && b.status == "PENDING" && !isLive)
                                                "Создана · Демо" else ticketLabel(b, live),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                            color = if (b.status == "CANCELLED" || b.status == "EXPIRED") ErrorRed else Jade)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${b.totalRub} ₽", color = Dark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("Демо", color = Quiet, fontSize = 10.sp)
                                }
                            }
                            HorizontalDivider(color = Pale)
                            Row {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Начало", color = Quiet, fontSize = 11.sp)
                                    Text(readableDate(b.startAt), color = Dark, fontSize = 12.sp)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Плановый возврат", color = Quiet, fontSize = 11.sp)
                                    Text(readableDate(b.endAt), color = Dark, fontSize = 12.sp)
                                }
                            }
                            if (b.status !in setOf("CANCELLED", "EXPIRED")) {
                                Text("Бронирование  →  Осмотр  →  Поездка  →  Возврат",
                                    color = Quiet, fontSize = 10.sp, maxLines = 2)
                            }
                            if (offline && b.status == "PENDING" && !isLive) {
                                Surface(color = Pale, shape = RoundedCornerShape(15.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Outlined.Info, null,
                                            tint = Jade, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(9.dp))
                                        Text("Это пробная заявка на указанную дату. Машина не бронируется на самом деле. Можно отдельно пройти все этапы аренды в LIVE-демо.",
                                            color = Dark, fontSize = 11.sp, lineHeight = 16.sp)
                                    }
                                }
                                Button(onClick = { onTrain(b) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(15.dp)) {
                                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(19.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Пройти LIVE-демо на этом грузовике",
                                        fontSize = 12.sp, maxLines = 1)
                                }
                            }
                            if (isCurrent(b) && isLive) {
                                Button(onClick = { onResume(b) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(15.dp)) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Продолжить LIVE-поездку")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { showing = b }, modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)) {
                                    Text("События", fontSize = 12.sp)
                                }
                                if (b.status == "PENDING" && !isLive) {
                                    TextButton(onClick = { cancelPending = b }, modifier = Modifier.weight(1f)) {
                                        Text("Отменить", color = ErrorRed, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    showing?.let { b ->
        var timeline by remember(b.id) { mutableStateOf<List<BookingTimelineEvent>?>(null) }
        var error by remember(b.id) { mutableStateOf<String?>(null) }
        val localEvents = if (b.id == live.bookingId) live.events else emptyList()
        LaunchedEffect(b.id) {
            try {
                timeline = onTimeline(b.id)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Не удалось получить историю"
            }
        }
        ModalBottomSheet(onDismissRequest = { showing = null }, containerColor = Paper) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("События аренды", color = Dark, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(ticketLabel(b, live), color = Jade, fontSize = 12.sp)
                if (localEvents.isNotEmpty()) {
                    // Events are stored without timestamps: never invent times for local operations.
                    localEvents.asReversed().take(14).forEach { event ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Jade,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(event, modifier = Modifier.weight(1f), color = Dark, fontSize = 12.sp)
                        }
                    }
                } else {
                    when {
                        error != null -> Text(error.orEmpty(), color = ErrorRed, fontSize = 12.sp)
                        timeline == null -> CircularProgressIndicator(color = Jade, modifier = Modifier.size(24.dp))
                        timeline!!.isEmpty() -> Text("Для этой заявки события пока не записаны.", color = Quiet)
                        else -> timeline!!.takeLast(15).forEach { event ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = Jade,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(9.dp))
                                Column {
                                    Text(bookingEventText(event.type), color = Dark, fontSize = 13.sp)
                                    Text(readableDate(event.at), color = Quiet, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text("Демонстрационный журнал — без фактической оплаты, передачи автомобиля и уведомлений.",
                    color = Quiet, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }

    cancelPending?.let { booking ->
        AlertDialog(
            onDismissRequest = { cancelPending = null },
            title = { Text("Отменить учебную заявку?") },
            text = { Text("Это изменит только данные демонстрационного бронирования.") },
            confirmButton = {
                TextButton(onClick = { onCancel(booking.id); cancelPending = null }) {
                    Text("Отменить заявку", color = ErrorRed)
                }
            },
            dismissButton = { TextButton(onClick = { cancelPending = null }) { Text("Назад") } }
        )
    }
}

@Composable
fun DriverDashboard(
    live: LiveDemoSession,
    bookings: List<Booking>,
    onLive: () -> Unit,
    onTrips: () -> Unit,
) {
    val completed = bookings.count { it.status == "COMPLETED" }
    val active = bookings.count(::isCurrent)
    val loginPassed = live.phase !in setOf(LivePhase.CHOOSE, LivePhase.PHONE)
    val documentPassed = live.phase !in setOf(LivePhase.CHOOSE, LivePhase.PHONE, LivePhase.DOCUMENTS)
    val cardPassed = live.phase !in setOf(LivePhase.CHOOSE, LivePhase.PHONE, LivePhase.DOCUMENTS, LivePhase.PAYMENT)
    var showDetails by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Paper)) {
        // Keep the page title pinned while long account sections scroll.
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Профиль", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Dark)
            Text("Учебная учётная запись · Нальчик", color = Quiet, fontSize = 12.sp)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 5.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
        item {
            Surface(color = Dark, shape = RoundedCornerShape(23.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .14f)) {
                        Icon(Icons.Outlined.PersonOutline, contentDescription = null,
                            modifier = Modifier.padding(15.dp).size(33.dp), tint = Color.White)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Демо-водитель", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Реальная регистрация ещё не подключена",
                            fontSize = 11.sp, lineHeight = 15.sp, color = Color.White.copy(alpha = .76f))
                    }
                    Icon(Icons.Outlined.Shield, null, tint = Color.White.copy(alpha = .8f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardMetric("Текущие", active.toString(), Modifier.weight(1f))
                DashboardMetric("Завершено", completed.toString(), Modifier.weight(1f))
            }
        }
        if (live.bookingId.isNotBlank() && live.phase !in setOf(LivePhase.CHOOSE, LivePhase.RECEIPT)) {
            item {
                Surface(color = Pale, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Ваш LIVE-демосценарий", fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, color = Dark)
                        Text(liveLabel(live), color = Jade, fontSize = 13.sp)
                        Button(onClick = onLive, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)) {
                            Text("Продолжить поездку")
                        }
                    }
                }
            }
        }
        item {
            DashboardSection("Доступ к аренде") {
                AccountStatusLine("Вход по SMS", if (loginPassed) "Учебный вход пройден" else "Не пройден",
                    loginPassed, Icons.Outlined.Sms)
                HorizontalDivider(color = Pale)
                AccountStatusLine("Права и паспорт",
                    if (documentPassed) "Проверены только образцы" else "Демопроверка не пройдена",
                    documentPassed, Icons.Outlined.Badge)
                HorizontalDivider(color = Pale)
                AccountStatusLine("Способ оплаты",
                    if (cardPassed) "Виртуальная карта •••• 0000" else "Демо-карта не подключена",
                    cardPassed, Icons.Outlined.CreditCard)
            }
        }
        item {
            DashboardSection("Мои действия") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = Jade)
                    Spacer(Modifier.width(12.dp))
                    Text("История поездок", color = Dark, modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onTrips) { Text("Открыть") }
                }
                HorizontalDivider(color = Pale)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Jade)
                    Spacer(Modifier.width(12.dp))
                    Text("Тренажёр аренды", color = Dark, modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onLive) { Text("Перейти") }
                }
            }
        }
        item {
            DashboardSection("О приложении") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Что работает в деморежиме?", color = Dark,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Условия, безопасность и ограничения", color = Quiet, fontSize = 11.sp)
                    }
                    IconButton(onClick = { showDetails = !showDetails }) {
                        Icon(if (showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = "Показать ограничения", tint = Jade)
                    }
                }
                if (showDetails) {
                    Text("SMS-код создаётся внутри приложения. Паспорт и права не отправляются на проверку. Карта виртуальная; реальные деньги не списываются. GPS грузовика и управление замками не подключены. Все данные поездок учебные.",
                        color = Quiet, fontSize = 12.sp, lineHeight = 19.sp)
                }
            }
        }
        }
    }
}

@Composable private fun DashboardMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Quiet, fontSize = 12.sp)
            Text(value, color = Jade, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable private fun DashboardSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(21.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(title, color = Dark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Pale)
            content()
        }
    }
}

@Composable private fun AccountStatusLine(
    title: String, detail: String, complete: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Pale, shape = RoundedCornerShape(11.dp)) {
            Icon(icon, contentDescription = null, tint = Jade,
                modifier = Modifier.padding(10.dp).size(20.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Dark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Quiet, fontSize = 11.sp)
        }
        Icon(if (complete) Icons.Outlined.CheckCircle else Icons.Outlined.ChevronRight,
            contentDescription = null, tint = if (complete) Jade else Quiet,
            modifier = Modifier.size(20.dp))
    }
}
