package ru.kbr.cargoshare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
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

private val Dark = PremiumTheme.Text
private val Jade = PremiumTheme.Green
private val Paper = PremiumTheme.Paper
private val Pale = PremiumTheme.Mint
private val Quiet = PremiumTheme.Muted
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
    var tab by rememberSaveable { mutableStateOf("Текущие") }
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
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            PremiumBrandHeader()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Мои поездки", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                    color = Dark, modifier = Modifier.weight(1f))
                if (!offline) IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, "Обновить поездки", tint = Jade)
                }
            }
            Surface(shape = RoundedCornerShape(19.dp), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(3.dp)) {
                    listOf("Текущие", "История").forEach { label ->
                        Surface(onClick = { tab = label }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(17.dp), color = if (tab == label) Jade else Color.White) {
                            Row(Modifier.padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (label == "Текущие") Icons.Outlined.Schedule else Icons.Outlined.History,
                                    null, tint = if (tab == label) Color.White else Quiet, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(if (label == "Текущие") "Текущие · $activeCount" else label,
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (tab == label) Color.White else Quiet)
                            }
                        }
                    }
                }
            }
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
                    val car = cars.firstOrNull { it.id == b.vehicleId }
                    val name = car?.name ?: "Грузовой автомобиль"
                    Card(
                        shape = RoundedCornerShape(23.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isCurrent(b) && car != null) {
                                Box(Modifier.fillMaxWidth().height(98.dp)) {
                                    TruckArtwork(car, Modifier.fillMaxSize())
                                    Surface(modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                                        color = Pale, shape = RoundedCornerShape(12.dp)) {
                                        Text(if (isLive) "● LIVE · ДЕМО" else "УЧЕБНАЯ ЗАЯВКА",
                                            color = Jade, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!isCurrent(b) && car != null) TruckArtwork(car, Modifier.size(78.dp, 70.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Dark)
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
                            if (car != null && isCurrent(b)) {
                                Text("${car.type} · ${car.payloadKg} кг · ${car.cargoVolumeM3} м³", color = Quiet, fontSize = 11.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.LocationOn, null, tint = Jade, modifier = Modifier.size(18.dp))
                                    Text(car.pickupLocation, color = Quiet, fontSize = 11.sp, modifier = Modifier.weight(1f))
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
                                val phase = if (isLive) live.phase else null
                                val step = when {
                                    b.status == "COMPLETED" || phase == LivePhase.RECEIPT -> 3
                                    phase in setOf(LivePhase.RETURNING, LivePhase.RETURN) -> 3
                                    phase in setOf(LivePhase.DRIVING, LivePhase.READY) || b.status == "ACTIVE" -> 2
                                    phase in setOf(LivePhase.INSPECT, LivePhase.UNLOCK) -> 1
                                    else -> 0
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    listOf("Бронь", "Осмотр", "Поездка", "Возврат").forEachIndexed { index, label ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Surface(shape = CircleShape, color = if (index <= step) Pale else Paper) {
                                                Icon(if (index < step || b.status == "COMPLETED") Icons.Outlined.CheckCircle else
                                                    if (index == step) Icons.Outlined.LocalShipping else Icons.Outlined.RadioButtonUnchecked,
                                                    null, tint = if (index <= step) Jade else Quiet,
                                                    modifier = Modifier.padding(7.dp).size(21.dp))
                                            }
                                            Text(label, color = if (index <= step) Jade else Quiet, fontSize = 10.sp)
                                        }
                                    }
                                }
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
                            if (b.status == "COMPLETED") {
                                TextButton(onClick={onResume(b)}) { Icon(Icons.Outlined.ReceiptLong,null); Text("Открыть учебный чек") }
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
                                if ((b.status == "PENDING" && !isLive) || (isLive && live.phase in setOf(LivePhase.WALK,LivePhase.INSPECT,LivePhase.UNLOCK,LivePhase.READY))) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDashboard(live: LiveDemoSession, bookings: List<Booking>,
                    onLive: () -> Unit, onTrips: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("gruzsher_preferences", 0)
    }
    val completed = bookings.count { it.status == "COMPLETED" }
    val active = bookings.count(::isCurrent)
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var journal by remember { mutableStateOf(prefs.getString("account_journal", "[]") ?: "[]") }
    val hasActiveTrip = live.bookingId.isNotBlank() &&
        live.phase !in setOf(LivePhase.CHOOSE, LivePhase.RECEIPT)

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("ГРУЗШЕР", color = PremiumTheme.GreenDark, fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            DemoPill()
        }
        LazyColumn(modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 25.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)) {
            item {
                Text("Профиль", fontSize = 25.sp, fontWeight = FontWeight.Bold,
                    color = Dark)
            }
            item {
                Surface(shape = RoundedCornerShape(19.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Pale) {
                            Icon(Icons.Outlined.PersonOutline, null,
                                modifier = Modifier.padding(13.dp).size(29.dp), tint = Jade)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Демо-пользователь", color = Dark,
                                fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Учебный режим · регистрация не подключена",
                                color = Quiet, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DashboardMetric("Текущие", active.toString(), Modifier.weight(1f))
                    DashboardMetric("Завершено", completed.toString(), Modifier.weight(1f))
                }
            }
            if (hasActiveTrip) item {
                Surface(shape = RoundedCornerShape(18.dp), color = Pale) {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onLive).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocalShipping, null, tint = Jade)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Продолжить поездку", fontWeight = FontWeight.Bold, color = Dark)
                            Text(liveLabel(live), color = Quiet, fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Jade)
                    }
                }
            }
            item {
                DashboardSection("Мой аккаунт") {
                    AccountAction("Мои документы", "Учебные образцы · не проверены реально",
                        Icons.Outlined.Badge) { panel = "Документы" }
                    HorizontalDivider(color = Pale)
                    AccountAction("Способ оплаты", "Виртуальная карта · списаний нет",
                        Icons.Outlined.CreditCard) { panel = "Оплата" }
                    HorizontalDivider(color = Pale)
                    AccountAction("История поездок", "Заявки и учебные чеки",
                        Icons.Outlined.History, onTrips)
                }
            }
            item {
                DashboardSection("Помощь и настройки") {
                    AccountAction("Поддержка", "Локальные учебные сообщения",
                        Icons.Outlined.SupportAgent) { panel = "Поддержка" }
                    HorizontalDivider(color = Pale)
                    AccountAction("Настройки", "Русский язык · демонстрационная версия",
                        Icons.Outlined.Settings) { panel = "Настройки" }
                    HorizontalDivider(color = Pale)
                    AccountAction("О приложении", "Возможности и ограничения",
                        Icons.Outlined.Info) { panel = "О приложении" }
                }
            }
        }
    }
    panel?.let { title ->
        ModalBottomSheet(onDismissRequest = { panel = null }, containerColor = Paper) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Dark)
                when (title) {
                    "Документы" -> {
                        Text("Это учебная проверка образцов паспорта и водительских прав. " +
                            "Настоящие документы не загружаются и не проверяются.",
                            fontSize = 13.sp, lineHeight = 19.sp, color = Quiet)
                        Button(onClick = { panel = null; onLive() },
                            modifier = Modifier.fillMaxWidth()) { Text("Открыть тренажёр") }
                    }
                    "Оплата" -> Text("Платежи отключены. Демо-карта •••• 0000 используется " +
                        "только в учебном сценарии. Реальных списаний нет.",
                        fontSize = 13.sp, lineHeight = 19.sp, color = Quiet)
                    "Настройки" -> {
                        Text("Язык интерфейса: русский", color = Dark)
                        Text("Переключение языка и реальные уведомления будут доступны " +
                            "после внедрения соответствующих функций.",
                            fontSize = 12.sp, color = Quiet)
                    }
                    "Поддержка" -> {
                        Text("Сообщения сохраняются только на этом устройстве и не " +
                            "отправляются оператору или экстренным службам.",
                            color = Quiet, fontSize = 12.sp, lineHeight = 18.sp)
                        OutlinedTextField(value = draft, onValueChange = { draft = it.take(500) },
                            label = { Text("Учебная заметка") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Button(enabled = draft.isNotBlank(), onClick = {
                            val saved = runCatching { JSONArray(journal) }.getOrDefault(JSONArray())
                            saved.put(JSONObject().put("type", "Поддержка")
                                .put("message", draft.trim())
                                .put("at", OffsetDateTime.now().toString()))
                            val latest = JSONArray()
                            for (k in maxOf(0, saved.length() - 50) until saved.length())
                                latest.put(saved.getJSONObject(k))
                            journal = latest.toString()
                            prefs.edit().putString("account_journal", journal).apply()
                            draft = ""
                        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить на устройстве") }
                        val messages = runCatching { JSONArray(journal) }.getOrDefault(JSONArray())
                        for (n in messages.length() - 1 downTo maxOf(0, messages.length() - 5)) {
                            val item = messages.optJSONObject(n) ?: continue
                            Surface(shape = RoundedCornerShape(12.dp), color = Color.White) {
                                Text(item.optString("message"), modifier = Modifier.padding(12.dp),
                                    color = Dark, fontSize = 12.sp)
                            }
                        }
                    }
                    else -> Text("ГрузШер ${BuildConfig.VERSION_NAME} · демонстрационная версия. " +
                        "SMS, платежи, замки, GPS машин и обращения к оператору сейчас " +
                        "имитируются. Реальная аренда недоступна.",
                        fontSize = 13.sp, lineHeight = 19.sp, color = Quiet)
                }
            }
        }
    }
}

@Composable private fun AccountAction(title: String, subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Pale) {
            Icon(icon, null, tint = Jade, modifier = Modifier.padding(12.dp).size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Dark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Quiet, fontSize = 11.sp)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Dark)
    }
}

@Composable private fun DashboardMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, color = if (label == "Текущие") Pale else Color(0xFFF0F3FC), shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (label == "Текущие") Icons.Outlined.LocalShipping else Icons.Outlined.CheckCircle,
                null, tint = Jade, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, color = Quiet, fontSize = 12.sp)
                Text(value, color = Dark, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }
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
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
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
