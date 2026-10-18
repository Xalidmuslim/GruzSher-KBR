package ru.kbr.cargoshare

import coil.compose.AsyncImage
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.time.Instant
import java.time.OffsetDateTime

private val Green = Color(0xFF26594C)
private val Cream = Color(0xFFF6F5F0)
private val Ink = Color(0xFF1B2A27)
private val SoftGreen = Color(0xFFE2EBE5)
private val LocalZone = ZoneId.of("Europe/Moscow")
private val Ru = Locale.forLanguageTag("ru-RU")
private val TimeFmt = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Ru)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light system bar background requires dark status-bar icons.
        window.statusBarColor = android.graphics.Color.rgb(246, 247, 242)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = PremiumTheme.Green, onPrimary = Color.White, background = PremiumTheme.Paper, surface = Color.White, onSurface = PremiumTheme.Text)) {
                AppScreen()
            }
        }
    }
}

@Composable
private fun AppScreen() {
    val api = remember { Api() }
    val scope = rememberCoroutineScope()
    var vehicles by remember { mutableStateOf(if (BuildConfig.DEBUG) offlineDemoCatalog() else emptyList()) }
    val context = LocalContext.current
    val demoStore = remember(context) { DemoSnapshotStore(context.applicationContext) }
    val restoredDemo = remember(demoStore) { demoStore.load() }
    var bookings by remember { mutableStateOf(restoredDemo.first) }
    var demoState by remember { mutableStateOf(restoredDemo.second) }
    var demoNotice by remember { mutableStateOf<String?>(null) }
    var liveOverview by remember { mutableStateOf(LiveDemoStorage(context.applicationContext).load()) }
    var token by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(!BuildConfig.DEBUG) }
    var error by remember { mutableStateOf<String?>(null) }
    var offlineDemo by remember { mutableStateOf(BuildConfig.DEBUG) }
    var tab by remember { mutableIntStateOf(0) }
    var liveSelectedCarId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Vehicle?>(null) }
    LaunchedEffect(tab) {
        if (tab == 1 || tab == 2) liveOverview = LiveDemoStorage(context.applicationContext).load()
    }
    // Volatile simulator state is intentionally never synchronized to the API or stored on disk.
    val offlineIntentKeys = remember { mutableStateMapOf<String, String>() }
    val offlineCreatedAt = remember { mutableStateMapOf<String, String>() }
    val offlineCancelledAt = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(bookings, demoState) {
        if (!BuildConfig.DEMO_SERVER_ENABLED) demoStore.save(bookings, demoState)
    }
    fun chooseTrip(booking: Booking) {
        val liveTrip = LiveDemoStorage(context.applicationContext).load()
        if (liveTrip.bookingId == booking.id && booking.id.isNotBlank()) {
            liveSelectedCarId = booking.vehicleId
            tab = 3
        } else {
            error = "Эта заявка относится к прежнему офлайн-сценарию. Текущую поездку можно продолжить через LIVE Демо."
        }
    }
    fun runDemoAction(action: DemoAction) {
        val result = performDemoAction(demoState, action)
        demoNotice = result.message
        if (result.success) {
            demoState = result.state
            val rideId = result.state.tripId
            if (offlineDemo && rideId != null) {
                bookings = bookings.map { booking ->
                    if (booking.id == rideId && result.state.tripStatus in setOf("CONFIRMED", "ACTIVE", "COMPLETED")) {
                        booking.copy(status = result.state.tripStatus)
                    } else booking
                }
            }
        }
    }

    fun demoSlots(): List<DemoRentalSlot> = bookings.mapNotNull { booking ->
        runCatching { DemoRentalSlot(booking.vehicleId,
            OffsetDateTime.parse(booking.startAt).toInstant(),
            OffsetDateTime.parse(booking.endAt).toInstant(), booking.status) }.getOrNull()
    }

    fun reloadVehicles() {
        if (!BuildConfig.DEMO_SERVER_ENABLED) {
            vehicles = offlineDemoCatalog()
            offlineDemo = true
            error = null
            return
        }
        scope.launch {
            busy = true
            runCatching { api.vehicles() }
                .onSuccess { vehicles = it; offlineDemo = false }
                .onFailure { error = "Не удалось обновить каталог: ${it.localizedMessage}" }
            busy = false
        }
    }
    fun reloadBookings(t: String) {
        scope.launch { runCatching { api.bookings(t) }.onSuccess { bookings = it }.onFailure { error = it.localizedMessage } }
    }
    LaunchedEffect(Unit) {
        if (!BuildConfig.DEMO_SERVER_ENABLED) {
            vehicles = offlineDemoCatalog()
            offlineDemo = true
            busy = false
            return@LaunchedEffect
        }
        try {
            vehicles = api.vehicles()
            offlineDemo = false
            if (BuildConfig.DEBUG) {
                token = api.demoSession()
                bookings = api.bookings(token!!)
            }
        } catch (e: Exception) {
            offlineDemo = BuildConfig.DEBUG
            if (BuildConfig.DEBUG) vehicles = offlineDemoCatalog()
            error = if (BuildConfig.DEBUG) "Нет связи с демосервером. Доступна только учебная симуляция аренды на телефоне." else "Не удалось подключиться к серверу: ${e.localizedMessage}"
        } finally {
            busy = false
        }
    }

    Scaffold(
        containerColor = PremiumTheme.Paper,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                val titles = listOf("Автомобили", "Поездки", "Профиль", "LIVE Демо")
                val icons = listOf(Icons.Outlined.LocalShipping, Icons.Outlined.ReceiptLong, Icons.Outlined.AccountCircle, Icons.Outlined.PlayCircleOutline)
                titles.forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Icon(icons[index], contentDescription = null) }, label = { Text(title, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PremiumTheme.Green,
                            selectedTextColor = PremiumTheme.Green,
                            indicatorColor = PremiumTheme.Lavender,
                            unselectedIconColor = PremiumTheme.Muted,
                            unselectedTextColor = PremiumTheme.Muted))
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (BuildConfig.DEBUG) {
                Text(if (offlineDemo) "ОФЛАЙН-СИМУЛЯЦИЯ • Без реальной брони и оплаты" else "ДЕМО-СЕРВЕР • Пример автомобилей и тарифов", fontSize = 11.sp,
                    color = PremiumTheme.Green, modifier = Modifier.fillMaxWidth().background(PremiumTheme.Mint).padding(horizontal = 14.dp, vertical = 7.dp))
            }
            if (error != null) {
                Surface(color = Color(0xFFFFEBE7), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error ?: "Ошибка", Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { error = null }) { Text("Закрыть") }
                    }
                }
            }
            when (tab) {
                0 -> PremiumFleetScreen(vehicles, busy, onSelect = { selected = it }, onRefresh = { reloadVehicles() }, onLiveDemo = { liveSelectedCarId = null; tab = 3 })
                1 -> TripsDashboard(bookings = bookings, cars = vehicles,
                    live = liveOverview, offline = offlineDemo,
                    onResume = { chooseTrip(it) },
                    onBrowse = { tab = 0 },
                    onTimeline = { id ->
                        if (offlineDemo && BuildConfig.DEBUG) {
                            val current = bookings.find { it.id == id }
                            listOf(BookingTimelineEvent("CREATED", offlineCreatedAt[id] ?: "")) +
                                if (current?.status == "CANCELLED") listOf(BookingTimelineEvent("CANCELLED", offlineCancelledAt[id] ?: "")) else emptyList()
                        } else token?.let { api.timeline(id, it) } ?: emptyList()
                    },
                    onRefresh = { if (!offlineDemo) token?.let { reloadBookings(it) } }, onCancel = { id ->
                    if (offlineDemo && BuildConfig.DEBUG) {
                        if (bookings.any { it.id == id && canCancelBooking(it.status) }) {
                            bookings = bookings.map { if (it.id == id) it.copy(status = "CANCELLED") else it }
                            offlineCancelledAt[id] = Instant.now().toString()
                            if (demoState.tripId == id) demoState = demoState.copy(tripStatus = "CANCELLED", doorsOpen = false)
                        }
                    } else {
                    val t = token
                    if (t != null) scope.launch {
                        runCatching { api.cancel(id, t) }
                            .onSuccess { reloadBookings(t) }
                            .onFailure { error = it.localizedMessage }
                    }
                    }
                })
                2 -> DriverDashboard(live = liveOverview, bookings = bookings,
                    onLive = { tab = 3 }, onTrips = { tab = 1 })
                else -> LiveDemoScreen(
                    cars = vehicles,
                    selectedCarId = liveSelectedCarId,
                    onBackToMap = { tab = 0 },
                    onTripChanged = { live ->
                        liveOverview = live
                        if (offlineDemo && live.bookingId.isNotBlank() && live.carId.isNotBlank()) {
                            val current = vehicles.firstOrNull { it.id == live.carId }
                            val phaseStatus = when (live.phase) {
                                LivePhase.RECEIPT -> "COMPLETED"
                                LivePhase.DRIVING, LivePhase.RETURNING, LivePhase.RETURN -> "ACTIVE"
                                LivePhase.WALK, LivePhase.INSPECT, LivePhase.UNLOCK, LivePhase.READY -> "CONFIRMED"
                                else -> "PENDING"
                            }
                            if (current != null) {
                                val simulatedStart = java.time.OffsetDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(live.approvalStartedAt), LocalZone)
                                val simulatedEnd = simulatedStart.plusHours((live.durationHours + live.extraHours).toLong())
                                val cost = if (live.phase == LivePhase.RECEIPT) demoFare(live, current.hourlyPriceRub, System.currentTimeMillis())
                                    else (live.durationHours + live.extraHours) * current.hourlyPriceRub
                                val record = Booking(live.bookingId, live.carId, phaseStatus,
                                    simulatedStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                    simulatedEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), cost)
                                if (bookings.none { it.id == live.bookingId }) {
                                    bookings = listOf(record) + bookings
                                    offlineCreatedAt[live.bookingId] = Instant.now().toString()
                                } else {
                                    bookings = bookings.map { if (it.id == live.bookingId) record else it }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
    selected?.let { vehicle ->
        VehicleBookingSheet(vehicle = vehicle, canBook = token != null || (offlineDemo && BuildConfig.DEBUG),
            onDismiss = { selected = null },
            onTryLiveDemo = {
                liveSelectedCarId = vehicle.id
                selected = null
                tab = 3
            },
            onQuote = { start, end ->
                if (offlineDemo && BuildConfig.DEBUG) {
                    val h = java.time.Duration.between(start, end).toHours().toInt()
                    val days = h / 24
                    val remainder = h % 24
                    Pair(days * vehicle.dailyPriceRub + minOf(remainder * vehicle.hourlyPriceRub, vehicle.dailyPriceRub),
                        validDemoWindow(start.toInstant(), end.toInstant(), Instant.now()) &&
                            canReserveDemo(demoSlots(), vehicle.id, start.toInstant(), end.toInstant()))
                } else api.quote(vehicle.id, start, end)
            },
            onBook = { start, end, idempotencyKey ->
                val t = token
                if (offlineDemo && BuildConfig.DEBUG) {
                    if (!validDemoWindow(start.toInstant(), end.toInstant(), Instant.now())) {
                        error = "Начало должно быть в будущем (до 90 дней), аренда от 2 часов до 30 дней"; false
                    } else if (!canReserveDemo(demoSlots(), vehicle.id, start.toInstant(), end.toInstant())) {
                        error = "Грузовик занят на выбранное время"; false
                    } else {
                        val alreadySaved = offlineIntentKeys[idempotencyKey]
                        if (alreadySaved == null) {
                            val id = "offline-${UUID.randomUUID()}"
                            offlineIntentKeys[idempotencyKey] = id
                            offlineCreatedAt[id] = Instant.now().toString()
                            val h = java.time.Duration.between(start, end).toHours().toInt()
                            val days = h / 24
                            val remainder = h % 24
                            val cost = days * vehicle.dailyPriceRub + minOf(remainder * vehicle.hourlyPriceRub, vehicle.dailyPriceRub)
                            bookings = listOf(Booking(id, vehicle.id, "PENDING",
                                start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), cost)) + bookings
                            demoState = chooseDemoBooking(demoState, id, "PENDING").state
                        }
                        selected = null
                        tab = 1
                        true
                    }
                } else if (t == null) {
                    error = "Для бронирования нужен сервер"; false
                } else {
                    runCatching { api.book(vehicle.id, start, end, t, idempotencyKey) }
                        .onSuccess { selected = null; reloadBookings(t); tab = 1 }
                        .onFailure { error = it.localizedMessage }
                        .isSuccess
                }
            }
        )
    }
}

@Composable
private fun Catalog(cars: List<Vehicle>, loading: Boolean, onSelect: (Vehicle) -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
                Text("Нальчик и КБР", fontSize = 13.sp, color = Green)
            }
            Spacer(Modifier.height(14.dp))
            Text("Грузовой транспорт\nрядом с вами", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, lineHeight = 37.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text("Аренда без водителя • от 2 часов", color = Color.Gray, fontSize = 14.sp)
        }
        Row(Modifier.padding(horizontal = 22.dp).fillMaxWidth().background(SoftGreen, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Green)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Прозрачная стоимость", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Цена рассчитывается до бронирования", color = Green, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Доступный транспорт", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            TextButton(onClick = onRefresh, enabled = !loading) { Text("Обновить") }
        }
        Spacer(Modifier.height(12.dp))
        if (loading) CircularProgressIndicator(Modifier.padding(24.dp))
        else if (cars.isEmpty()) Text("Пока нет автомобилей", Modifier.padding(24.dp))
        else LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cars, key = { it.id }) { v -> TruckCard(v, onClick = { onSelect(v) }) }
        }
    }
}

@Composable
private fun TruckCard(v: Vehicle, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Box(Modifier.fillMaxWidth().height(100.dp).background(SoftGreen, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (v.imageUrl.startsWith("https://")) {
                    AsyncImage(model = v.imageUrl, contentDescription = "Фото автомобиля ${v.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = Green, modifier = Modifier.size(75.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(v.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(v.type, fontSize = 13.sp, color = Color.Gray)
                }
                Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = Green)
            }
            Spacer(Modifier.height(8.dp))
            Text("${v.payloadKg} кг  ·  ${v.cargoVolumeM3} м³  ·  ${v.transmission}", fontSize = 13.sp, color = Color.DarkGray)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("от ${v.hourlyPriceRub} ₽", fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Green)
                Spacer(Modifier.width(5.dp))
                Text("/ час", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.weight(1f))
                Text("${v.dailyPriceRub} ₽ / сутки", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleBookingSheet(
    vehicle: Vehicle,
    canBook: Boolean,
    onDismiss: () -> Unit,
    onTryLiveDemo: () -> Unit,
    onQuote: suspend (ZonedDateTime, ZonedDateTime) -> Pair<Int, Boolean>,
    onBook: suspend (ZonedDateTime, ZonedDateTime, String) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var start by remember(vehicle.id) {
        mutableStateOf(ZonedDateTime.now(LocalZone).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0))
    }
    var hours by remember(vehicle.id) { mutableIntStateOf(4) }
    var price by remember(vehicle.id) { mutableStateOf<Int?>(null) }
    var available by remember(vehicle.id) { mutableStateOf(true) }
    var quotedIntent by remember(vehicle.id) { mutableStateOf<BookingIntent?>(null) }
    var message by remember(vehicle.id) { mutableStateOf<String?>(null) }
    var requesting by remember(vehicle.id) { mutableStateOf(false) }
    var bookingSubmitting by remember(vehicle.id) { mutableStateOf(false) }
    var lastBookingIntent by remember(vehicle.id) { mutableStateOf<BookingIntent?>(null) }
    var stableIntentKey by remember(vehicle.id) { mutableStateOf(UUID.randomUUID().toString()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 36.dp)) {
            Text(vehicle.name, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            Text(vehicle.type, fontSize = 14.sp, color = Color.Gray)
            Spacer(Modifier.height(16.dp))
            if (vehicle.description.isNotBlank()) {
                Text(vehicle.description, color = Color.DarkGray, fontSize = 13.sp)
            }
            Text("${vehicle.payloadKg} кг · ${vehicle.cargoVolumeM3} м³ · ${vehicle.transmission}", color = Green)
            Text(vehicle.pickupLocation, color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onTryLiveDemo,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Пройти аренду в реальном времени (ДЕМО)") }
            Spacer(Modifier.height(16.dp))
            Text("Начало аренды", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, year, month, day ->
                        start = java.time.LocalDate.of(year, month + 1, day)
                            .atTime(start.toLocalTime()).atZone(LocalZone)
                        price = null
                        quotedIntent = null
                    }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                }) { Text(start.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Ru))) }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        start = start.withHour(h).withMinute(m)
                        price = null
                        quotedIntent = null
                    }, start.hour, start.minute, true).show()
                }) { Text(start.format(DateTimeFormatter.ofPattern("HH:mm", Ru))) }
            }
            Spacer(Modifier.height(16.dp))
            Text("На сколько часов?", fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 4, 8, 24).forEach { h ->
                    FilterChip(selected = hours == h, onClick = { hours = h; price = null; quotedIntent = null }, label = { Text(if (h == 24) "Сутки" else "$h ч") })
                }
            }
            Spacer(Modifier.height(16.dp))
            if (price != null) {
                Text(if (available) "Стоимость: $price ₽" else "Автомобиль занят в выбранное время", color = Green,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            if (message != null) Text(message.orEmpty(), color = Color.Red, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val requestedStart = start
                val requestedEnd = start.plusHours(hours.toLong())
                val intent = BookingIntent(vehicle.id,
                    requestedStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    requestedEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                if (!validDemoWindow(requestedStart.toInstant(), requestedEnd.toInstant(), Instant.now())) {
                    price = null
                    quotedIntent = null
                    message = "Укажите будущую дату (не более 90 дней) и срок аренды от 2 часов"
                } else {
                    requesting = true
                    scope.launch {
                        runCatching { onQuote(requestedStart, requestedEnd) }
                            .onSuccess { (amount, isAvailable) ->
                                val currentEnd = start.plusHours(hours.toLong())
                                if (intent == BookingIntent(vehicle.id, start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                        currentEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))) {
                                    price = amount
                                    available = isAvailable
                                    quotedIntent = intent
                                    message = null
                                }
                            }
                            .onFailure { message = it.localizedMessage; quotedIntent = null }
                        requesting = false
                    }
                }
            }, enabled = !requesting && !bookingSubmitting, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Проверить цену и доступность")
            }
            if (price != null && available && canBook && quotedIntent == BookingIntent(vehicle.id,
                    start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    start.plusHours(hours.toLong()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    if (!bookingSubmitting) {
                        val end = start.plusHours(hours.toLong())
                        val intent = BookingIntent(vehicle.id, start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        if (intent != lastBookingIntent) {
                            lastBookingIntent = intent
                            stableIntentKey = UUID.randomUUID().toString()
                        }
                        bookingSubmitting = true
                        scope.launch {
                            onBook(start, end, stableIntentKey)
                            bookingSubmitting = false
                        }
                    }
                }, enabled = !bookingSubmitting,
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (bookingSubmitting) "Отправляем заявку…" else "Отправить заявку на аренду")
                }
            }
            Text(if (canBook) "ТОЛЬКО ДЕМО: заявка не поступает оператору, локально сохраняется на телефоне и не даёт доступ к машине." else "Это просмотр и расчёт демотарифа. Чтобы бронировать, подключите сервер и войдите.",
                fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingsPage(
    bookings: List<Booking>, cars: List<Vehicle>,
    onTimeline: suspend (String) -> List<BookingTimelineEvent>,
    onRefresh: () -> Unit,
    onCancel: (String) -> Unit,
    onDemoSelect: (Booking) -> Unit,
) {
    var selectedBooking by remember { mutableStateOf<Booking?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Поездки", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = Ink)
                Text("Демонстрационные заявки — без выдачи автомобиля", color = Color.Gray, fontSize = 12.sp)
            }
            TextButton(onClick = onRefresh) { Text("Обновить") }
        }
        if (bookings.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ReceiptLong, null, tint = Green, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Бронирований пока нет", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Выберите автомобиль на карте и рассчитайте аренду", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)) {
                items(bookings, key = { it.id }) { booking ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(cars.find { it.id == booking.vehicleId }?.name ?: "Грузовой автомобиль",
                                        fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(bookingStatusText(booking.status), color = Green, fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold)
                                }
                                Text("${booking.totalRub} ₽", fontWeight = FontWeight.Bold, color = Ink)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Начало: ${formatBookingTime(booking.startAt)}", fontSize = 12.sp, color = Color.Gray)
                            Text("Возврат: ${formatBookingTime(booking.endAt)}", fontSize = 12.sp, color = Color.Gray)
                            if (booking.status != "CANCELLED") {
                                Spacer(Modifier.height(14.dp))
                                RentalProgress(booking.status)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { selectedBooking = booking },
                                    modifier = Modifier.weight(1f)) { Text("История", fontSize = 12.sp) }
                                if (booking.status !in setOf("CANCELLED", "EXPIRED")) {
                                    TextButton(onClick = { onDemoSelect(booking) }, modifier = Modifier.weight(1f)) {
                                        Text("Демо", fontSize = 12.sp)
                                    }
                                }
                                if (canCancelBooking(booking.status)) {
                                    TextButton(onClick = { onCancel(booking.id) },
                                        modifier = Modifier.weight(1f)) { Text("Отменить", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedBooking?.let { booking ->
        var timeline by remember(booking.id) { mutableStateOf<List<BookingTimelineEvent>?>(null) }
        var timelineError by remember(booking.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(booking.id) {
            runCatching { onTimeline(booking.id) }
                .onSuccess { timeline = it }
                .onFailure { timelineError = it.localizedMessage ?: "Не удалось получить историю" }
        }
        ModalBottomSheet(onDismissRequest = { selectedBooking = null }, containerColor = Cream) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
                Text("История аренды", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(bookingStatusText(booking.status), fontSize = 13.sp, color = Green)
                Spacer(Modifier.height(16.dp))
                when {
                    timelineError != null -> Text(timelineError!!, color = Color.Red)
                    timeline == null -> CircularProgressIndicator()
                    timeline!!.isEmpty() -> Text("Для старой заявки события не записаны", color = Color.Gray)
                    else -> timeline!!.forEach { event ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Green, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(bookingEventText(event.type), fontWeight = FontWeight.Medium)
                                Text(formatBookingTime(event.at), fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Демонстрационная история. Подтверждения операций не означают оплату или физическую выдачу машины.",
                    fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

private fun formatBookingTime(value: String): String =
    runCatching {
        java.time.OffsetDateTime.parse(value).atZoneSameInstant(LocalZone).format(TimeFmt)
    }.getOrElse { value.take(16).replace('T', ' ') }

@Composable
private fun RentalProgress(status: String) {
    val steps = listOf("Заявка", "Одобрение", "Выдача", "Возврат")
    val index = bookingStage(status)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        steps.forEachIndexed { i, title ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Surface(shape = RoundedCornerShape(50), color = if (i <= index) Green else SoftGreen,
                    modifier = Modifier.size(25.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontSize = 11.sp,
                            color = if (i <= index) Color.White else Green, fontWeight = FontWeight.Bold)
                    }
                }
                Text(title, fontSize = 10.sp, color = if (i <= index) Green else Color.Gray,
                    modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
private fun ProfilePage() {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Text("Профиль", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Демонстрационный режим", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text("Вход по SMS, загрузка водительского удостоверения, платежи и договор аренды подключаются перед реальным запуском.",
                    color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                Text("Регион: Нальчик · Кабардино-Балкария", color = Green)
            }
        }
    }
}
