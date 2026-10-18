package ru.kbr.cargoshare

import coil.compose.AsyncImage
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.SystemClock
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Tab 3 is the compact More hub; an explicit action opens the retained LIVE demo.
    var showLiveDemo by rememberSaveable { mutableStateOf(false) }
    // Routes 0..3 are bottom destinations; route 4 represents LIVE inside More.
    var navigationTrail by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var lastRootBackAt by remember { mutableLongStateOf(0L) }
    fun currentRoute(): Int = if (tab == 3 && showLiveDemo) 4 else tab
    fun navigateTo(destination: Int, openDemo: Boolean = false) {
        val target = if (destination == 3 && openDemo) 4 else destination
        navigationTrail = pushNavigationRoute(navigationTrail, currentRoute(), target)
        tab = destination
        showLiveDemo = destination == 3 && openDemo
        lastRootBackAt = 0L
    }
    var liveSelectedCarId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCarKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = vehicles.firstOrNull { it.id == selectedCarKey }
    var receiptId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val tabStates = rememberSaveableStateHolder()
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
        if (LiveDemoStorage(context.applicationContext).receipt(booking.id) != null) {
            receiptId = booking.id; navigateTo(3, openDemo = true); return
        }
        receiptId = null
        val liveTrip = LiveDemoStorage(context.applicationContext).load()
        if (liveTrip.bookingId == booking.id && booking.id.isNotBlank()) {
            liveSelectedCarId = booking.vehicleId
            navigateTo(3, openDemo = true)
        } else {
            error = "Эта заявка относится к прежнему офлайн-сценарию. Текущую поездку можно продолжить через LIVE Демо."
        }
    }
    fun trainForBooking(booking: Booking) {
        if (!offlineDemo || !BuildConfig.DEBUG) {
            error = "Учебный сценарий доступен только в демонстрационном режиме."
            return
        }
        if (vehicles.none { it.id == booking.vehicleId }) {
            error = "Грузовик этой заявки отсутствует в учебном каталоге."
            return
        }
        val current = LiveDemoStorage(context.applicationContext).load()
        if (current.phase != LivePhase.CHOOSE) {
            liveSelectedCarId = null
            navigateTo(3, openDemo = true)
            error = "Уже открыт другой учебный сценарий. Завершите его или нажмите «Сначала» в LIVE Демо, затем выберите грузовик. Заявка на дату останется без изменений."
            return
        }
        liveSelectedCarId = booking.vehicleId
        navigateTo(3, openDemo = true)
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

    // System edge Back and hardware Back unwind one actual destination.
    // Nested dialogs and the expanded fleet map intercept Back before this handler.
    BackHandler(enabled = selected == null) {
        if (navigationTrail.isNotEmpty()) {
            val previous = navigationTrail.last()
            navigationTrail = navigationTrail.dropLast(1)
            if (tab == 3 && showLiveDemo && previous != 4) receiptId = null
            tab = if (previous == 4) 3 else previous
            showLiveDemo = previous == 4
            lastRootBackAt = 0L
        } else if (tab != 0) {
            receiptId = null
            showLiveDemo = false
            tab = 0
        } else {
            // One unintentional edge swipe on Home must not close GruzSher.
            val now = SystemClock.elapsedRealtime()
            if (now - lastRootBackAt < 2_000L) {
                (context as? Activity)?.finish()
            } else {
                lastRootBackAt = now
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Ещё раз «Назад» — выйти из приложения",
                        duration = SnackbarDuration.Short)
                }
            }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbar) },
        containerColor = PremiumTheme.Paper,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                val titles = listOf("Автомобили", "Поездки", "Профиль", "LIVE Демо")
                val icons = listOf(Icons.Outlined.LocalShipping, Icons.Outlined.ReceiptLong, Icons.Outlined.AccountCircle, Icons.Outlined.PlayArrow)
                titles.forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = {
                        receiptId = null
                        if (index == 3) navigateTo(3, openDemo = true)
                        else navigateTo(index)
                    },
                        icon = { Icon(icons[index], contentDescription = null) }, label = { Text(title, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PremiumTheme.GreenDark,
                            selectedTextColor = PremiumTheme.GreenDark,
                            indicatorColor = PremiumTheme.Mint,
                            unselectedIconColor = PremiumTheme.Muted,
                            unselectedTextColor = PremiumTheme.Muted))
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (error != null) {
                Surface(color = Color(0xFFFFEBE7), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error ?: "Ошибка", Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { error = null }) { Text("Закрыть") }
                    }
                }
            }
            tabStates.SaveableStateProvider(tab) {
            when (tab) {
                0 -> PremiumFleetScreen(vehicles.filterNot { it.id==liveOverview.carId && liveOverview.phase in setOf(LivePhase.WALK,LivePhase.INSPECT,LivePhase.UNLOCK,LivePhase.READY,LivePhase.DRIVING,LivePhase.RETURNING,LivePhase.RETURN) }, busy, onSelect = { selectedCarKey = it.id }, onRefresh = { reloadVehicles() }, onLiveDemo = { receiptId=null; liveSelectedCarId = null; navigateTo(3, openDemo = true) }, active=liveOverview.phase !in setOf(LivePhase.CHOOSE,LivePhase.RECEIPT))
                1 -> TripsDashboard(bookings = bookings, cars = vehicles,
                    live = liveOverview, offline = offlineDemo,
                    onResume = { chooseTrip(it) },
                    onTrain = { trainForBooking(it) },
                    onBrowse = { navigateTo(0) },
                    onTimeline = { id ->
                        if (offlineDemo && BuildConfig.DEBUG) {
                            val current = bookings.find { it.id == id }
                            val store = LiveDemoStorage(context.applicationContext)
                            val journal = (store.receipt(id) ?: store.load().takeIf { it.bookingId == id })?.events.orEmpty()
                            journal.map { BookingTimelineEvent(it, "") } + listOf(BookingTimelineEvent("CREATED", offlineCreatedAt[id] ?: "")) +
                                if (current?.status == "CANCELLED") listOf(BookingTimelineEvent("CANCELLED", offlineCancelledAt[id] ?: "")) else emptyList()
                        } else token?.let { api.timeline(id, it) } ?: emptyList()
                    },
                    onRefresh = { if (!offlineDemo) token?.let { reloadBookings(it) } }, onCancel = { id ->
                    if (offlineDemo && BuildConfig.DEBUG) {
                        if (bookings.any { it.id == id && canCancelBooking(it.status) }) {
                            bookings = bookings.map { if (it.id == id) it.copy(status = "CANCELLED") else it }
                            offlineCancelledAt[id] = Instant.now().toString()
                            if (demoState.tripId == id) demoState = demoState.copy(tripStatus = "CANCELLED", doorsOpen = false)
                            val liveStore = LiveDemoStorage(context.applicationContext)
                            val live = liveStore.load()
                            if (live.bookingId == id && live.phase in setOf(LivePhase.WALK, LivePhase.INSPECT, LivePhase.UNLOCK, LivePhase.READY)) {
                                liveStore.save(LiveDemoSession()); liveOverview = LiveDemoSession()
                            }
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
                    onLive = { receiptId=null; navigateTo(3, openDemo = true) },
                    onTrips = { navigateTo(1) }, onMore = { navigateTo(3) })
                else -> if (!showLiveDemo) GruzSherMoreScreen(
                    onDemo = { receiptId = null; navigateTo(3, openDemo = true) },
                    onFleet = { navigateTo(0) },
                    onTrips = { navigateTo(1) },
                    onProfile = { navigateTo(2) }
                ) else LiveDemoScreen(
                    cars = vehicles,
                    selectedCarId = liveSelectedCarId,
                    selectedBookingId = receiptId,
                    onBackToMap = { receiptId = null; navigateTo(0) },
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
                                    else demoFare(live, current.hourlyPriceRub, System.currentTimeMillis())
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
    }
    selected?.let { vehicle ->
        VehicleBookingSheet(vehicle = vehicle, canBook = token != null || (offlineDemo && BuildConfig.DEBUG),
            offlineTraining = offlineDemo && BuildConfig.DEBUG,
            onDismiss = { selectedCarKey = null },
            onTryLiveDemo = { requestedHours ->
                receiptId = null
                val liveStore = LiveDemoStorage(context.applicationContext)
                val current = liveStore.load()
                if (current.phase != LivePhase.CHOOSE && current.phase != LivePhase.RECEIPT) {
                    selectedCarKey = null
                    liveSelectedCarId = null
                    navigateTo(3, openDemo = true)
                    error = "Другая учебная аренда уже начата. Продолжите её или нажмите «Сначала» в LIVE-демо."
                } else {
                    if (current.phase == LivePhase.RECEIPT) liveStore.save(LiveDemoSession(durationHours = requestedHours))
                    else liveStore.save(current.copy(durationHours = requestedHours))
                    liveSelectedCarId = vehicle.id
                    selectedCarKey = null
                    navigateTo(3, openDemo = true)
                }
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
                        selectedCarKey = null
                        navigateTo(1)
                        true
                    }
                } else if (t == null) {
                    error = "Для бронирования нужен сервер"; false
                } else {
                    runCatching { api.book(vehicle.id, start, end, t, idempotencyKey) }
                        .onSuccess { selectedCarKey = null; reloadBookings(t); navigateTo(1) }
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

@Composable
private fun VehicleBookingSheet(
    vehicle: Vehicle,
    canBook: Boolean,
    offlineTraining: Boolean,
    onDismiss: () -> Unit,
    onTryLiveDemo: (Int) -> Unit,
    onQuote: suspend (ZonedDateTime, ZonedDateTime) -> Pair<Int, Boolean>,
    onBook: suspend (ZonedDateTime, ZonedDateTime, String) -> Boolean,
) {
    FullScreenVehicleBooking(
        vehicle = vehicle, canBook = canBook, offlineTraining = offlineTraining,
        onDismiss = onDismiss,
        onTryLiveDemo = onTryLiveDemo,
        onQuote = onQuote, onBook = onBook
    )
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
