package ru.kbr.cargoshare

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private val LiveGreen = PremiumTheme.Green
private val LivePaper = PremiumTheme.Paper
private val LiveInk = PremiumTheme.Text
private val LivePale = PremiumTheme.Mint
private val LiveGray = PremiumTheme.Muted

@Composable
fun LiveDemoScreen(
    cars: List<Vehicle>,
    selectedCarId: String?,
    onTripChanged: (LiveDemoSession) -> Unit,
    onBackToMap: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { LiveDemoStorage(context.applicationContext) }
    var session by remember { mutableStateOf(store.load()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf("+79000000000") }
    var code by remember { mutableStateOf("") }
    var supportText by remember { mutableStateOf("") }
    var sentSupport by remember { mutableStateOf<String?>(null) }
    var showReturnConfirm by remember { mutableStateOf(false) }
    val journeyScroll = rememberScrollState()
    // Stage changes must restart at the stage heading, never inherit the preceding stage's offset.
    LaunchedEffect(session.phase) { journeyScroll.scrollTo(0) }

    val car = cars.find { it.id == session.carId } ?: cars.firstOrNull()
    fun perform(event: LiveEvent, value: String = "") {
        val result = applyLive(session, event, now, value)
        message = result.message
        if (result.accepted) {
            session = result.state
            onTripChanged(session)
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(500L)
        }
    }
    LaunchedEffect(now, session) {
        val next = advanceLive(session, now)
        if (next != session) {
            session = next
            onTripChanged(next)
        }
    }
    LaunchedEffect(session) { store.save(session) }
    LaunchedEffect(selectedCarId) {
        if (!selectedCarId.isNullOrEmpty() && selectedCarId != session.carId && session.phase == LivePhase.CHOOSE) {
            val result = applyLive(session, LiveEvent.PICK_CAR, System.currentTimeMillis(), selectedCarId)
            if (result.accepted) {
                session = result.state
                onTripChanged(result.state)
            }
        }
    }
    LaunchedEffect(Unit) { onTripChanged(session) }
    Box(Modifier.fillMaxSize().background(LivePaper)) {
    Column(
        Modifier.fillMaxSize().verticalScroll(journeyScroll)
            .padding(horizontal = 18.dp)
            .padding(bottom = if (session.phase in setOf(LivePhase.DRIVING, LivePhase.RETURNING, LivePhase.RETURN)) 106.dp else 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ГРУЗШЕР", fontSize = 27.sp, fontWeight = FontWeight.Black,
                    color = LiveGreen)
                Text("LIVE Демо · полный цикл аренды", color = LiveGray, fontSize = 12.sp)
            }
            TextButton(onClick = { perform(LiveEvent.RESET) }) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(3.dp))
                Text("Сначала", fontSize = 11.sp)
            }
        }
        Surface(shape = RoundedCornerShape(14.dp), color = LivePale) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = LiveGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Тренажёр: никакие SMS, платежи, команды автомобилю или сообщения оператору не отправляются.",
                    color = LiveGreen, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        if (message != null) {
            Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                Text(message!!, color = LiveInk, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
        }
        if (session.phase != LivePhase.CHOOSE && car != null) {
            LiveVehicleCard(car, session.phase == LivePhase.DRIVING)
        }
        when (session.phase) {
            LivePhase.CHOOSE -> {
                LiveHeader("01", "Найдите грузовик", "Выберите автомобиль. После выбора пройдёте весь путь клиента — от входа до чека.")
                cars.forEach { vehicle ->
                    Surface(onClick = { perform(LiveEvent.PICK_CAR, vehicle.id) },
                        color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocalShipping, null, tint = LiveGreen, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(vehicle.name, fontWeight = FontWeight.Bold, color = LiveInk)
                                Text("${vehicle.type} · от ${vehicle.hourlyPriceRub} ₽/ч", fontSize = 12.sp, color = LiveGray)
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = LiveGreen)
                        }
                    }
                }
            }
            LivePhase.PHONE -> {
                LiveHeader("02", "Подтвердите телефон", "Как при настоящей регистрации: запрос кода, ожидание SMS и ввод цифр.")
                OutlinedTextField(value = phone, onValueChange = { phone = it.take(12) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Телефон (учебный)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true)
                if (session.codeRequestedAt == 0L) {
                    LiveButton("Получить SMS-код", Icons.Outlined.Sms) { perform(LiveEvent.REQUEST_CODE, phone) }
                } else {
                    val remaining = (3L - (now - session.codeRequestedAt) / 1000L).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        LiveWait("Отправляем тестовый SMS-код…", remaining, 3f - remaining.toFloat())
                    } else {
                        LiveNote("Учебное сообщение: ваш код — 2468. Настоящее SMS не отправлялось.")
                        OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(4) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Код из сообщения") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        LiveButton("Подтвердить номер", Icons.Outlined.CheckCircle) { perform(LiveEvent.VERIFY_CODE, code) }
                    }
                }
            }
            LivePhase.DOCUMENTS -> {
                LiveHeader("03", "Проверка водителя", "Два отдельных этапа распознавания образцов. Камера и реальные документы не используются.")
                listOf(Triple("Паспорт", session.passportStartedAt, LiveEvent.SCAN_PASSPORT),
                    Triple("Водительские права", session.licenceStartedAt, LiveEvent.SCAN_LICENCE)).forEach { (name, started, event) ->
                    val complete = started > 0L && now - started >= LIVE_OPERATION_MS
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (complete) Icons.Outlined.CheckCircle else Icons.Outlined.Badge, null, tint = LiveGreen)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold, color = LiveInk)
                                Text(if (complete) "Учебный образец проверен" else if (started > 0) "Идёт анализ изображения…" else "Ожидает проверки",
                                    fontSize = 11.sp, color = LiveGray)
                            }
                            if (started == 0L) TextButton(onClick = { perform(event) }) { Text("Сканировать") }
                            else if (!complete) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                LiveNote("В настоящей версии здесь появятся камера, распознавание и проверка допустимости водительских прав.")
            }
            LivePhase.PAYMENT -> {
                LiveHeader("04", "Способ оплаты", "Учебная банковская карта, проверка подключения и нулевое списание.")
                Surface(shape = RoundedCornerShape(18.dp), color = LiveGreen) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("ГРУЗШЕР · ТЕСТОВАЯ КАРТА", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.height(18.dp))
                        Text("••••  ••••  ••••  0000", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Text("ДЕМО • НЕ ПЛАТЁЖНОЕ СРЕДСТВО", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                    }
                }
                if (session.paymentStartedAt == 0L) LiveButton("Привязать тестовую карту", Icons.Outlined.CreditCard) { perform(LiveEvent.START_PAYMENT) }
                else LiveWait("Проверка виртуальной карты…",
                    (LIVE_OPERATION_MS - (now - session.paymentStartedAt)).coerceAtLeast(0L) / 1000L,
                    (now - session.paymentStartedAt).toFloat() / 1000f)
            }
            LivePhase.CONFIRM -> {
                LiveHeader("05", "Подтверждение аренды", "Как в настоящем сервисе: тариф, условия и запрос на бронирование.")
                if (car != null) {
                    Text("Срок аренды", color = LiveInk, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 4, 8).forEach { h ->
                            FilterChip(selected = session.durationHours == h,
                                onClick = { perform(LiveEvent.SET_DURATION, h.toString()) },
                                label = { Text("$h часа") })
                        }
                    }
                    LiveMetricRow("Предварительная стоимость", "${session.durationHours * car.hourlyPriceRub} ₽")
                    LiveMetricRow("Залог", "0 ₽")
                    LiveMetricRow("Способ оплаты", "Демо-карта •••• 0000")
                    LiveNote("Для настоящего заказа здесь появятся договор, правила аренды и подтверждение платежа.")
                    LiveButton("Забронировать", Icons.Outlined.ArrowForward) { perform(LiveEvent.REQUEST_BOOKING) }
                }
            }
            LivePhase.APPROVAL -> {
                LiveHeader("06", "Ожидаем подтверждение", "Учебный диспетчер обрабатывает запрос. Обычно сюда подключается сервер.")
                LiveWait("Проверяем доступность и допуск…",
                    (4L - (now - session.approvalStartedAt) / 1000L).coerceAtLeast(0L),
                    (now - session.approvalStartedAt).toFloat())
            }
            LivePhase.WALK -> {
                LiveHeader("07", "Идите к автомобилю", "Демо-пешеход приближается к выбранному грузовику. Таймер идёт по настоящим секундам.")
                LiveRouteMap(car, ((now - session.approachStartedAt).toFloat() / LIVE_APPROACH_MS).coerceIn(0f, 1f), "пешком")
                LiveMetricRow("До автомобиля", "${ceil(((LIVE_APPROACH_MS - (now - session.approachStartedAt)).coerceAtLeast(0L) / 1000.0)).toInt()} сек")
                LinearProgressIndicator(progress = { ((now - session.approachStartedAt).toFloat() / LIVE_APPROACH_MS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = LiveGreen)
            }
            LivePhase.INSPECT -> {
                LiveHeader("08", "Осмотрите автомобиль", "Не галочки: нажимайте стороны машины, выполняйте учебную фотосъёмку и фиксируйте повреждения.")
                if (car != null) LiveVehicleCard(car)
                Text("Фото до поездки: ${session.inspected.size} из 4", fontWeight = FontWeight.SemiBold)
                listOf("Спереди", "Сзади", "Слева", "Справа").chunked(2).forEach { angles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        angles.forEach { angle ->
                            val captured = angle in session.inspected
                            OutlinedButton(onClick = { perform(LiveEvent.CAPTURE_ANGLE, angle) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (captured) LivePale else Color.White)) {
                                Icon(if (captured) Icons.Outlined.CheckCircle else Icons.Outlined.PhotoCamera, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(angle, fontSize = 12.sp)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { perform(LiveEvent.REPORT_DAMAGE) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (session.damageRecorded) "Повреждение добавлено в учебный акт" else "Нашёл повреждение — добавить в акт")
                }
                LiveButton("Закончить осмотр и открыть двери", Icons.Outlined.LockOpen,
                    enabled = session.inspected.size == 4) { perform(LiveEvent.UNLOCK) }
            }
            LivePhase.UNLOCK -> {
                LiveHeader("09", "Открываем двери", "В настоящем каршеринге ждём подтверждения телематического блока автомобиля.")
                LiveWait("Отправка и подтверждение виртуальной команды…",
                    ((LIVE_OPERATION_MS - (now - session.unlockingAt)).coerceAtLeast(0L)) / 1000L,
                    (now - session.unlockingAt).toFloat() / 1000f)
            }
            LivePhase.READY -> {
                LiveHeader("10", "Автомобиль открыт", "Осмотр подтверждён. В реальном сервисе здесь водитель садится в автомобиль.")
                Icon(Icons.Outlined.LockOpen, null, tint = LiveGreen, modifier = Modifier.size(80.dp).align(Alignment.CenterHorizontally))
                LiveMetricRow("Статус", "Двери открыты (симуляция)")
                LiveButton("Начать поездку", Icons.Outlined.PlayArrow) { perform(LiveEvent.START_DRIVE) }
            }
            LivePhase.DRIVING -> {
                val progress = routeFraction(session, now)
                val elapsed = elapsedRideSeconds(session, now)
                val fare = demoFare(session, car?.hourlyPriceRub ?: 650, now)
                LiveHeader("11", "Поездка идёт", "Маркер движется по учебной траектории в Нальчике. Данные автомобиля полностью симулируются.")
                LiveRouteMap(car, progress, "грузовик")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveNumber("Время", "${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}", Modifier.weight(1f))
                    LiveNumber("Путь", "${String.format(Locale.US, "%.2f", demoDistanceKm(session, now))} км", Modifier.weight(1f))
                    LiveNumber("Стоимость", "$fare ₽", Modifier.weight(1f))
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = LiveGreen)
                LiveMetricRow("Скорость (учебная)", "${demoSpeedKmh(session)} км/ч")
                LiveMetricRow("Учебный маршрут", "${((LIVE_ROUTE_MS * (1 - progress)) / 1000f).roundToInt()} сек до стоянки")
                if (session.pauseStartedAt > 0L) LiveNote("Режим ожидания: движение и учебный таймер маршрута остановлены.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { perform(LiveEvent.TOGGLE_WAIT) }, modifier = Modifier.weight(1f)) {
                        Text(if (session.pauseStartedAt > 0L) "Продолжить" else "Ожидание", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { perform(LiveEvent.EXTEND) }, modifier = Modifier.weight(1f)) {
                        Text("Продлить +2 ч", fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { perform(LiveEvent.ASK_SUPPORT) }, modifier = Modifier.weight(1f)) { Text("Поддержка", fontSize = 12.sp) }
                    OutlinedButton(onClick = { perform(LiveEvent.REPORT_INCIDENT) }, modifier = Modifier.weight(1f)) { Text("Происшествие", fontSize = 12.sp) }
                }
                if (session.assistance) {
                    LiveNote("Учебная поддержка: «Здравствуйте! Мы видим вашу тестовую поездку. Какой у вас вопрос?»")
                    if (sentSupport != null) LiveNote("Вы: $sentSupport\nДемо-оператор: «Спасибо. В настоящем сервисе с вами свяжется сотрудник»")
                    OutlinedTextField(supportText, onValueChange = { supportText = it.take(140) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Сообщение") })
                    OutlinedButton(onClick = { sentSupport = supportText; supportText = "" }, enabled = supportText.isNotBlank()) { Text("Отправить в демо-чат") }
                }
                if (session.incident) LiveNote("Учебный инцидент №1 отмечен в журнале. Никому не отправлен.")
            }
            LivePhase.RETURNING -> {
                val progress = routeFraction(session, now)
                val seconds = ((LIVE_RETURN_MS - (now - session.returnRequestedAt)).coerceAtLeast(0L) / 1000L)
                LiveHeader("12", "Едем к стоянке", "Вы выбрали завершение поездки. Машина направляется в учебную зону возврата.")
                LiveRouteMap(car, progress, "грузовик")
                LiveMetricRow("Прибытие", "Через ${seconds} сек")
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = LiveGreen)
                LiveNote("Маршрут и геозона условные. После прибытия потребуется фотоосмотр, закрытие дверей и подтверждение окончания.")
            }
            LivePhase.RETURN -> {
                LiveHeader("13", "Возврат грузовика", "Вы приехали в учебную зону завершения. Сделайте фото, закройте машину и завершите аренду.")
                LiveRouteMap(car, 1f, "грузовик")
                LiveMetricRow("Учебная зона возврата", "Разрешено")
                val photoDone = session.returnPhotoAt > 0L && now >= session.returnPhotoAt + LIVE_OPERATION_MS
                val lockDone = session.lockingAt > 0L && now >= session.lockingAt + LIVE_OPERATION_MS
                if (session.returnPhotoAt == 0L) {
                    LiveNote("Шаг 1 из 3 · Сделайте учебный фотоосмотр машины, затем подтвердите закрытие дверей.")
                } else if (!photoDone) LiveWait("Сохраняем учебный фотоотчёт…",
                    (LIVE_OPERATION_MS - (now - session.returnPhotoAt)).coerceAtLeast(0L) / 1000L,
                    (now - session.returnPhotoAt).toFloat() / 1000f)
                else if (session.lockingAt == 0L) {
                    LiveNote("Шаг 2 из 3 · Фотоосмотр завершён. Теперь закройте учебный автомобиль.")
                } else if (!lockDone) LiveWait("Проверяем виртуальное закрытие дверей…",
                    (LIVE_OPERATION_MS - (now - session.lockingAt)).coerceAtLeast(0L) / 1000L,
                    (now - session.lockingAt).toFloat() / 1000f)
                else LiveNote("Шаг 3 из 3 · Двери закрыты. Подтвердите завершение и получите чек.")
            }
            LivePhase.RECEIPT -> {
                LiveHeader("14", "Поездка завершена", "Пример электронного отчёта с реальными показаниями таймера симуляции.")
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = LiveGreen, modifier = Modifier.size(44.dp))
                        Text("Учебный чек • ${session.bookingId}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        LiveMetricRow("Грузовик", car?.name ?: "Автомобиль")
                        LiveMetricRow("Тариф", "${session.durationHours + session.extraHours} ч")
                        LiveMetricRow("Пройдено", "${String.format(Locale.US, "%.2f", demoDistanceKm(session, now))} км (демо)")
                        HorizontalDivider()
                        LiveMetricRow("Расчётная стоимость", "${demoFare(session, car?.hourlyPriceRub ?: 650, now)} ₽")
                        Text("СПИСАНО НА САМОМ ДЕЛЕ: 0 ₽", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LiveGreen)
                        Text("Не является кассовым чеком", color = LiveGray, fontSize = 11.sp)
                    }
                }
                Text("Как прошла поездка?", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { perform(LiveEvent.SET_RATING, star.toString()) }) {
                            Icon(if (star <= session.rating) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$star звёзд", tint = LiveGreen)
                        }
                    }
                }
                LiveButton("Начать новую учебную поездку", Icons.Outlined.Refresh) { perform(LiveEvent.RESET) }
                OutlinedButton(onClick = onBackToMap, modifier = Modifier.fillMaxWidth()) { Text("Вернуться к карте") }
            }
        }
        if (session.phase !in setOf(LivePhase.CHOOSE, LivePhase.RECEIPT)) {
            Spacer(Modifier.height(7.dp))
            Text("ДЕМО • управление автомобилем и расчёт полностью виртуальные",
                modifier = Modifier.fillMaxWidth(), fontSize = 10.sp, color = LiveGray)
        }
        Spacer(Modifier.height(8.dp))
    }
    if (session.phase in setOf(LivePhase.DRIVING, LivePhase.RETURNING, LivePhase.RETURN)) {
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            color = Color.White, shadowElevation = 11.dp, tonalElevation = 2.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val photoReady = session.returnPhotoAt > 0L && now >= session.returnPhotoAt + LIVE_OPERATION_MS
                val lockReady = session.lockingAt > 0L && now >= session.lockingAt + LIVE_OPERATION_MS
                val target = when {
                    session.phase == LivePhase.DRIVING -> "Завершить поездку"
                    session.phase == LivePhase.RETURNING -> "Направляемся на стоянку…"
                    session.returnPhotoAt == 0L -> "Сделать фото при возврате"
                    !photoReady -> "Обрабатываем фото…"
                    session.lockingAt == 0L -> "Закрыть автомобиль"
                    !lockReady -> "Проверяем закрытие…"
                    else -> "Завершить аренду • чек"
                }
                val enabled = session.phase == LivePhase.DRIVING ||
                    (session.phase == LivePhase.RETURN && (session.returnPhotoAt == 0L || photoReady &&
                        (session.lockingAt == 0L || lockReady)))
                Button(
                    onClick = {
                        when {
                            session.phase == LivePhase.DRIVING -> showReturnConfirm = true
                            session.phase == LivePhase.RETURN && session.returnPhotoAt == 0L -> perform(LiveEvent.TAKE_RETURN_PHOTO)
                            session.phase == LivePhase.RETURN && session.lockingAt == 0L -> perform(LiveEvent.LOCK_DOORS)
                            session.phase == LivePhase.RETURN -> perform(LiveEvent.FINISH)
                            else -> Unit
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LiveGreen),
                ) {
                    Icon(if (session.phase == LivePhase.DRIVING) Icons.Outlined.Flag
                        else Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(target, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text("ДЕМО · нет реального управления замками или списаний",
                    color = LiveGray, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
    }
    if (showReturnConfirm) {
        AlertDialog(
            onDismissRequest = { showReturnConfirm = false },
            icon = { Icon(Icons.Outlined.LocalParking, null, tint = LiveGreen) },
            title = { Text("Завершить поездку?") },
            text = {
                Text("Для завершения аренды сначала доберёмся до учебной парковки (около 16 секунд). Затем потребуется сделать фото автомобиля и закрыть двери. Настоящий автомобиль не перемещается.")
            },
            confirmButton = {
                Button(onClick = { showReturnConfirm = false; perform(LiveEvent.REQUEST_RETURN) }) {
                    Text("Ехать к парковке")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReturnConfirm = false }) { Text("Продолжить поездку") }
            }
        )
    }
}

@Composable private fun LiveVehicleCard(car: Vehicle, active: Boolean = false) {
    val dark = if (active) PremiumTheme.GreenDark else Color.White
    val foreground = if (active) Color.White else LiveInk
    Surface(color = dark, shape = RoundedCornerShape(21.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            TruckArtwork(car, Modifier.size(116.dp, 90.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (active) "● В пути · ДЕМО" else "● Учебная аренда",
                    color = if (active) Color(0xFF9CE6BA) else LiveGreen,
                    fontSize = 11.sp)
                Text(car.name, color = foreground, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(car.type, color = if (active) Color.White.copy(alpha = .8f) else LiveGray, fontSize = 11.sp)
                Text("${car.hourlyPriceRub} ₽/ч · без залога",
                    color = if (active) Color.White else LiveGreen, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun LiveHeader(step: String, heading: String, detail: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(23.dp), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ЭТАП $step / 14 · LIVE ДЕМО",
                fontSize = 10.sp, color = LiveGreen, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { ((step.toIntOrNull() ?: 1) / 14f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)),
                color = LiveGreen, trackColor = LivePale)
            Text(heading, fontSize = 23.sp, lineHeight = 27.sp,
                fontWeight = FontWeight.ExtraBold, color = LiveInk)
            Text(detail, fontSize = 12.sp, lineHeight = 18.sp, color = LiveGray)
        }
    }
}
@Composable private fun LiveNumber(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(19.dp),
        shadowElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontSize = 11.sp, color = LiveGray)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                color = LiveGreen, maxLines = 1)
        }
    }
}
@Composable private fun LiveMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LiveGray, fontSize = 12.sp)
        Text(value, color = LiveInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable private fun LiveNote(value: String) {
    Surface(color = LivePale, shape = RoundedCornerShape(13.dp)) {
        Text(value, Modifier.fillMaxWidth().padding(12.dp), fontSize = 12.sp, lineHeight = 18.sp, color = LiveInk)
    }
}
@Composable private fun LiveWait(label: String, seconds: Long, progress: Float) {
    Surface(color = Color.White, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(23.dp), color = LiveGreen, strokeWidth = 2.dp)
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(label, color = LiveInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Осталось: ${seconds.coerceAtLeast(0L)} сек", color = LiveGray, fontSize = 12.sp)
                }
            }
            LinearProgressIndicator(progress = { (progress / 4f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = LiveGreen)
        }
    }
}
@Composable private fun LiveButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true, click: () -> Unit) {
    Button(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp)) {
        Icon(icon, null, Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Map tiles are real OSM. All route geometry and simulated marker coordinates are illustrative. */
@Composable private fun LiveRouteMap(car: Vehicle?, fraction: Float, mode: String) {
    val context = LocalContext.current
    val origin = remember(car?.id) { GeoPoint(car?.latitude ?: 43.4853, car?.longitude ?: 43.6071) }
    val route = remember(origin, mode) {
        if (mode == "пешком") listOf(
            GeoPoint(origin.latitude + 0.0012, origin.longitude - 0.0018), origin
        ) else listOf(origin,
            GeoPoint(origin.latitude + 0.0010, origin.longitude + 0.0008),
            GeoPoint(origin.latitude + 0.0020, origin.longitude + 0.0020),
            GeoPoint(origin.latitude + 0.0032, origin.longitude + 0.0024),
            GeoPoint(origin.latitude + 0.0044, origin.longitude + 0.0027))
    }
    val map = remember(context, car?.id, mode) {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(route[centerIndexForRoute(route.size)])
            minZoomLevel = 9.0
            maxZoomLevel = 18.0
            overlays.add(Polyline().apply {
                setPoints(route)
                outlinePaint.color = AndroidColor.rgb(24, 79, 63)
                outlinePaint.strokeWidth = 8f
            })
            overlays.add(Marker(this).apply {
                position = if (mode == "пешком") route.first() else origin
                title = if (mode == "пешком") "Учебный пешеход" else "Учебный грузовик"
                icon = if (mode == "пешком") walkingMapMarker(context) else truckMapMarker(context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            })
            overlays.add(Marker(this).apply {
                position = if (mode == "пешком") route.last() else GeoPoint(
                    route.last().latitude + 0.00045, route.last().longitude + 0.00045
                )
                title = if (mode == "пешком") "Автомобиль" else "Учебная зона возврата"
                icon = if (mode == "пешком") truckMapMarker(context) else returnZoneMapMarker(context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            })
        }
    }
    DisposableEffect(map) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }
    AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().height(218.dp)
        .clip(RoundedCornerShape(20.dp)),
        update = { view ->
            val f = fraction.coerceIn(0f, 1f)
            val segment = (f * (route.size - 1)).coerceAtMost((route.size - 1).toFloat())
            val low = segment.toInt().coerceAtMost(route.size - 2)
            val part = (segment - low).coerceIn(0f, 1f)
            val start = route[low]
            val end = route[low + 1]
            val point = GeoPoint(start.latitude + (end.latitude - start.latitude) * part,
                start.longitude + (end.longitude - start.longitude) * part)
            val moving = view.overlays.filterIsInstance<Marker>().firstOrNull() ?: return@AndroidView
            moving.position = point
            moving.title = if (mode == "пешком") "Учебный пешеход" else "Учебный грузовик"
            view.invalidate()
        })
    Text("Учебная траектория • движение маркера не соответствует GPS или дорогам",
        color = LiveGray, fontSize = 10.sp)
}
