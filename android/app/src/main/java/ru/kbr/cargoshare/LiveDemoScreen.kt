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
    selectedBookingId: String? = null,
    onTripChanged: (LiveDemoSession) -> Unit,
    onBackToMap: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { LiveDemoStorage(context.applicationContext) }
    var session by remember(selectedBookingId) { mutableStateOf(selectedBookingId?.let(store::receipt) ?: store.load()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf("+79000000000") }
    var code by remember { mutableStateOf("") }
    var supportText by remember { mutableStateOf("") }
    var sentSupport by remember { mutableStateOf<String?>(null) }
    var showReturnConfirm by remember { mutableStateOf(false) }
    var reportKind by remember { mutableStateOf<String?>(null) }
    var reportText by remember { mutableStateOf("") }
    var previewAngle by remember { mutableStateOf<String?>(null) }
    val journeyScroll = rememberScrollState()
    // Stage changes must restart at the stage heading, never inherit the preceding stage's offset.
    LaunchedEffect(session.phase) { journeyScroll.scrollTo(0) }

    val car = cars.find { it.id == session.carId } ?: cars.firstOrNull()
    fun perform(event: LiveEvent, value: String = "") {
        val result = applyLive(session, event, System.currentTimeMillis(), value)
        message = result.message
        if (result.accepted) {
            session = result.state
            if (selectedBookingId == null) onTripChanged(session)
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
            if (selectedBookingId == null) onTripChanged(next)
        }
    }
    LaunchedEffect(session) { if (selectedBookingId == null) store.save(session) else store.saveReceipt(session) }
    LaunchedEffect(selectedCarId) {
        if (!selectedCarId.isNullOrEmpty() && selectedCarId != session.carId && session.phase == LivePhase.CHOOSE) {
            val result = applyLive(session, LiveEvent.PICK_CAR, System.currentTimeMillis(), selectedCarId)
            if (result.accepted) {
                session = result.state
                if (selectedBookingId == null) onTripChanged(result.state)
            }
        }
    }
    LaunchedEffect(Unit) { if (selectedBookingId == null) onTripChanged(session) }
    Box(Modifier.fillMaxSize().background(LivePaper)) {
    Column(
        Modifier.fillMaxSize().verticalScroll(journeyScroll)
            .padding(horizontal = if (session.phase == LivePhase.DRIVING) 12.dp else 18.dp)
            .padding(bottom = if (session.phase in setOf(LivePhase.INSPECT, LivePhase.DRIVING, LivePhase.RETURNING, LivePhase.RETURN)) 106.dp else 30.dp),
        verticalArrangement = Arrangement.spacedBy(if (session.phase == LivePhase.DRIVING) 9.dp else 14.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        DemoNotice()
        PremiumBrandHeader()
        if(session.phase in setOf(LivePhase.PHONE,LivePhase.DOCUMENTS,LivePhase.PAYMENT,LivePhase.CONFIRM)) TextButton(onClick={perform(LiveEvent.RESET)}) {Icon(Icons.Outlined.ArrowBack,null);Text("Изменить автомобиль") }
        if (message != null) {
            Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                Text(message!!, color = LiveInk, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
        }
        if (session.phase in setOf(LivePhase.PHONE, LivePhase.PAYMENT, LivePhase.CONFIRM, LivePhase.WALK, LivePhase.READY) && car != null) {
            LiveVehicleCard(car)
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
                StageBanner("Автопроверка документов", "Этап 03 / 13 · Проверяем учебные образцы")
                LiveNote("ДЕМО · без загрузки персональных данных. Используются только образцы; это не OCR/KYC.")
                DocumentStatusCard("Паспорт", "passport_sample", ((now-session.passportStartedAt).toFloat()/(LIVE_OPERATION_MS*.65f)).coerceIn(0f,1f), session.documentIssue)
                DocumentStatusCard("Водительское удостоверение", "licence_sample", ((now-session.licenceStartedAt).toFloat()/LIVE_OPERATION_MS).coerceIn(0f,1f), session.documentIssue)
                Text("Варианты для демонстрации",fontSize=16.sp,fontWeight=FontWeight.Bold,color=LiveInk)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Нечитаемый документ","Истёк срок","Неверная категория").forEach { issue ->
                        OutlinedButton(onClick={perform(LiveEvent.FAIL_DOCUMENT_CHECK,issue)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(6.dp),shape=RoundedCornerShape(14.dp)) {
                            Text(issue,fontSize=10.sp,lineHeight=13.sp)
                        }
                    }
                }
                if(session.documentIssue.isNotEmpty()) LiveButton("Повторить демопроверку",Icons.Outlined.Refresh) {perform(LiveEvent.RETRY_DOCUMENT_CHECK)}
                LiveNote("После успешной проверки следующий этап откроется автоматически.")
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
                LiveHeader("05", "Условия аренды", "Выберите срок. Учебная бронь создаётся автоматически после подтверждения условий — без оператора.")
                if (car != null) {
                    Text("Срок аренды", color = LiveInk, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 4, 8, 24).forEach { h ->
                            FilterChip(selected = session.durationHours == h,
                                onClick = { perform(LiveEvent.SET_DURATION, h.toString()) },
                                label = { Text("$h часа") })
                        }
                    }
                    LiveMetricRow("Предварительная стоимость", "${demoFare(session,car.hourlyPriceRub,now)} ₽")
                    LiveMetricRow("Залог", "0 ₽")
                    LiveMetricRow("Способ оплаты", "Демо-карта •••• 0000")
                    LiveNote("Для настоящего заказа здесь появятся договор, правила аренды и подтверждение платежа.")
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(session.termsAccepted,{perform(LiveEvent.ACCEPT_TERMS,it.toString())})
                        Text("Принимаю учебные условия. Оплаты и реального договора нет.",fontSize=12.sp,modifier=Modifier.weight(1f))
                    }
                    LiveButton("Подтвердить и идти к автомобилю", Icons.Outlined.ArrowForward, enabled=session.termsAccepted) { perform(LiveEvent.REQUEST_BOOKING) }
                }
            }
            LivePhase.APPROVAL -> {
                // Only shown for an instant when restoring an old v3.3 saved session.
                LiveWait("Восстанавливаем демобронь без ожидания оператора…", 0L, 1f)
            }
            LivePhase.WALK -> {
                LiveHeader("06", "Идите к автомобилю", "Демо-пешеход приближается к выбранному грузовику. Таймер идёт по настоящим секундам.")
                LiveRouteMap(car, ((now - session.approachStartedAt).toFloat() / LIVE_APPROACH_MS).coerceIn(0f, 1f), "пешком")
                LiveMetricRow("До автомобиля", "${ceil(((LIVE_APPROACH_MS - (now - session.approachStartedAt)).coerceAtLeast(0L) / 1000.0)).toInt()} сек")
                LinearProgressIndicator(progress = { ((now - session.approachStartedAt).toFloat() / LIVE_APPROACH_MS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = LiveGreen)
            }
            LivePhase.INSPECT -> {
                Surface(color=LivePale,shape=RoundedCornerShape(21.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Осмотр перед поездкой",fontSize=24.sp,fontWeight=FontWeight.ExtraBold,color=LiveInk)
                        Text("Проверьте автомобиль. Фотографии сгенерированы, осмотр учебный.",fontSize=12.sp,color=LiveGray)
                    }
                }
                Row {Text("${session.inspected.size} из 4 выполнено",modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold);Text("${session.inspected.size*25}%")}
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {repeat(4) {n->Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(10.dp)).background(if(n<session.inspected.size) Color(0xFF009A50) else PremiumTheme.Stroke))}}
                listOf("Спереди" to "front","Сзади" to "rear","Слева" to "left","Справа" to "right").chunked(2).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {row.forEach {(angle,view)->
                        val captured=angle in session.inspected
                        Card(onClick={previewAngle=angle},modifier=Modifier.weight(1f),shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=if(captured) LiveGreen else Color.White)) {
                            Box {if(car!=null) TruckArtwork(car,Modifier.fillMaxWidth().height(120.dp),view)
                                Icon(if(captured) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,angle, tint=if(captured) Color(0xFF00A35D) else LiveGray,modifier=Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.White,RoundedCornerShape(30.dp)).size(30.dp))}
                            Row(Modifier.fillMaxWidth().padding(11.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Outlined.PhotoCamera,null,tint=if(captured) Color.White else LiveGreen)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {Text(angle,fontWeight=FontWeight.Bold,color=if(captured) Color.White else LiveInk);Text(if(captured) "Фото добавлено" else "Добавьте демофото",fontSize=10.sp,color=if(captured) Color.White else LiveGray)}
                                Icon(Icons.Outlined.ChevronRight,null,tint=if(captured) Color.White else LiveGray)
                            }
                        }
                    }}
                }
                LiveNote("На что обратить внимание: кузов, колёса, салон и грузовой отсек. Проверьте чистоту и оборудование.")
                OutlinedButton(onClick={reportKind="damage"},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(16.dp)) {Icon(Icons.Outlined.WarningAmber,null);Text(if(session.damageRecorded) "Повреждение записано · добавить ещё" else "Сообщить о повреждении")}
            }
            LivePhase.UNLOCK -> {
                LiveHeader("08", "Открываем двери", "В настоящем каршеринге ждём подтверждения телематического блока автомобиля.")
                LiveWait("Отправка и подтверждение виртуальной команды…",
                    ((LIVE_OPERATION_MS - (now - session.unlockingAt)).coerceAtLeast(0L)) / 1000L,
                    (now - session.unlockingAt).toFloat() / 1000f)
            }
            LivePhase.READY -> {
                LiveHeader("09", "Автомобиль открыт", "Осмотр подтверждён. В реальном сервисе здесь водитель садится в автомобиль.")
                Icon(Icons.Outlined.LockOpen, null, tint = LiveGreen, modifier = Modifier.size(80.dp).align(Alignment.CenterHorizontally))
                LiveMetricRow("Статус", "Двери открыты (симуляция)")
                LiveButton("Начать поездку", Icons.Outlined.PlayArrow) { perform(LiveEvent.START_DRIVE) }
            }
            LivePhase.DRIVING -> {
                PremiumTripDashboard(session, car, now) { if(it==LiveEvent.REPORT_INCIDENT) reportKind="incident" else perform(it) }
                if (session.assistance) {
                    LiveNote("Учебная поддержка: «Здравствуйте! Мы видим вашу тестовую поездку. Какой у вас вопрос?»")
                    session.events.filter { it.startsWith("Демо-чат:") }.takeLast(6).forEach { LiveNote(it) }
                    OutlinedTextField(supportText, onValueChange = { supportText = it.take(140) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Сообщение") })
                    OutlinedButton(onClick = { perform(LiveEvent.ASK_SUPPORT, supportText); sentSupport = supportText; supportText = "" },
                        enabled = supportText.isNotBlank()) { Text("Отправить в демо-чат") }
                }
                if (session.incident) LiveNote("Демо-инцидент внесён в журнал. Никому не отправлен.")
            }
            LivePhase.RETURNING -> {
                val progress = routeFraction(session, now)
                val seconds = ((LIVE_RETURN_MS - (now - session.returnRequestedAt)).coerceAtLeast(0L) / 1000L)
                LiveHeader("11", "Едем к стоянке", "Вы выбрали завершение поездки. Машина направляется в учебную зону возврата.")
                LiveRouteMap(car, progress, "грузовик")
                LiveMetricRow("Прибытие", "Через ${seconds} сек")
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = LiveGreen)
                LiveNote("Маршрут и геозона условные. После прибытия потребуется фотоосмотр, закрытие дверей и подтверждение окончания.")
            }
            LivePhase.RETURN -> {
                LiveHeader("12", "Возврат грузовика", "Вы приехали в учебную зону завершения. Сделайте фото, закройте машину и завершите аренду.")
                LiveRouteMap(car, 1f, "грузовик")
                Surface(color = LivePale, shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocalParking, null,
                            tint = LiveGreen, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Учебная зона возврата", color = LiveGreen,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Маркер условный. Геозона не проверяется по GPS.",
                                fontSize = 11.sp, color = LiveGray)
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(session.returnConditionChecked,{perform(LiveEvent.CHECK_RETURN_CONDITION,it.toString())})
                    Text("Кузов и салон осмотрены, вещи забраны (демо)",fontSize=12.sp,modifier=Modifier.weight(1f))
                }
                if(session.returnPhotoAt>0L && car!=null) TruckArtwork(car,Modifier.fillMaxWidth().height(120.dp),"rear")
                val photoDone = session.returnPhotoAt > 0L && now >= session.returnPhotoAt + LIVE_OPERATION_MS
                val lockDone = session.lockingAt > 0L && now >= session.lockingAt + LIVE_OPERATION_MS
                Surface(color = Color.White, shape = RoundedCornerShape(21.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Чек-лист возврата", color = LiveInk,
                            fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        PremiumReturnStep(1, "Фотоосмотр автомобиля",
                            session.returnPhotoAt != 0L)
                        PremiumReturnStep(2, "Кузов и салон осмотрены",session.returnConditionChecked)
                        PremiumReturnStep(3, "Закрытие дверей",
                            lockDone)
                        PremiumReturnStep(4, "Завершение аренды", false)
                    }
                }
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
                StageBanner("Поездка завершена", "Спасибо за учебную аренду!")
                Surface(color = Color.White, shape = RoundedCornerShape(23.dp),
                    shadowElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ReceiptLong, null, tint = LiveGreen,
                                modifier = Modifier.size(25.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("Учебный чек", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = LiveInk)
                        }
                        Text("№ ${session.bookingId}", color = LiveGray, fontSize = 12.sp)
                        HorizontalDivider(color = PremiumTheme.Stroke)
                        LiveMetricRow("Грузовик", car?.name ?: "Автомобиль")
                        LiveMetricRow("Тариф", "${session.durationHours + session.extraHours} ч")
                        LiveMetricRow("Время в движении", "${elapsedRideSeconds(session,now)} сек (демо)")
                        LiveMetricRow("Пройдено", "${String.format(Locale.US, "%.2f", demoDistanceKm(session, now))} км (демо)")
                        HorizontalDivider(color = PremiumTheme.Stroke)
                        LiveMetricRow("Стоимость аренды", "${demoFare(session, car?.hourlyPriceRub ?: 650, now)} ₽")
                        Surface(color = LivePale, shape = RoundedCornerShape(13.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("Списано на самом деле", color = LiveGreen,
                                    modifier = Modifier.weight(1f), fontSize = 13.sp)
                                Text("0 ₽", color = LiveGreen,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            }
                        }
                        Text("Демодокумент · не является кассовым чеком",
                            color = LiveGray, fontSize = 11.sp)
                    }
                }
                Text("Как прошла поездка?", fontSize = 19.sp,
                    fontWeight = FontWeight.Bold, color = LiveInk)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { perform(LiveEvent.SET_RATING, star.toString()) }) {
                            Icon(if (star <= session.rating) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$star звёзд", tint = LiveGreen)
                        }
                    }
                }
                if(selectedBookingId==null) LiveButton("Начать новую учебную поездку", Icons.Outlined.Refresh) { perform(LiveEvent.RESET) }
                OutlinedButton(onClick = onBackToMap,
                    shape = RoundedCornerShape(17.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Вернуться к автомобилям")
                }
            }
        }
        if (session.phase !in setOf(LivePhase.CHOOSE, LivePhase.RECEIPT)) {
            Spacer(Modifier.height(7.dp))
            Text("ДЕМО • управление автомобилем и расчёт полностью виртуальные",
                modifier = Modifier.fillMaxWidth(), fontSize = 10.sp, color = LiveGray)
        }
        Spacer(Modifier.height(8.dp))
    }
    if (session.phase in setOf(LivePhase.INSPECT, LivePhase.DRIVING, LivePhase.RETURNING, LivePhase.RETURN)) {
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            color = Color.White, shadowElevation = 11.dp, tonalElevation = 2.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val photoReady = session.returnPhotoAt > 0L && now >= session.returnPhotoAt + LIVE_OPERATION_MS
                val lockReady = session.lockingAt > 0L && now >= session.lockingAt + LIVE_OPERATION_MS
                val target = when {
                    session.phase == LivePhase.INSPECT -> "Подтвердить осмотр и открыть двери"
                    session.phase == LivePhase.DRIVING -> "Завершить поездку"
                    session.phase == LivePhase.RETURNING -> "Направляемся на стоянку…"
                    session.returnPhotoAt == 0L -> "Сделать фото при возврате"
                    !photoReady -> "Обрабатываем фото…"
                    session.lockingAt == 0L -> "Закрыть автомобиль"
                    !lockReady -> "Проверяем закрытие…"
                    else -> "Завершить аренду • чек"
                }
                val enabled = (session.phase == LivePhase.INSPECT && session.inspected.size == 4) || session.phase == LivePhase.DRIVING ||
                    (session.phase == LivePhase.RETURN && (session.returnPhotoAt == 0L || photoReady &&
                        (session.lockingAt == 0L && session.returnConditionChecked || lockReady)))
                Button(
                    onClick = {
                        when {
                            session.phase == LivePhase.INSPECT -> perform(LiveEvent.UNLOCK)
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
    if(previewAngle!=null && car!=null) AlertDialog(onDismissRequest={previewAngle=null}, title={Text(previewAngle!!)},text={
        val view=when(previewAngle) {"Спереди"->"front";"Сзади"->"rear";"Слева"->"left";else->"right"}
        Column {TruckArtwork(car,Modifier.fillMaxWidth().height(190.dp),view);Text("Сгенерированное демофото. Настоящая камера не используется.",fontSize=12.sp)}
    },confirmButton={TextButton(onClick={if(previewAngle !in session.inspected) perform(LiveEvent.CAPTURE_ANGLE,previewAngle!!);previewAngle=null}) {Text(if(previewAngle in session.inspected) "Готово" else "Добавить демофото")}})
    if(reportKind!=null) AlertDialog(onDismissRequest={reportKind=null},title={Text(if(reportKind=="damage") "Повреждение · демо" else "Происшествие · демо")},text={Column {
        if(car!=null) TruckArtwork(car,Modifier.fillMaxWidth().height(120.dp),"left")
        OutlinedTextField(reportText,{reportText=it.take(240)},label={Text("Опишите ситуацию")},modifier=Modifier.fillMaxWidth())
        Text("Учебная запись с иллюстрацией сохранится в журнале поездки. Никуда не отправляется.",fontSize=11.sp)
    }},confirmButton={TextButton(enabled=reportText.isNotBlank(),onClick={perform(if(reportKind=="damage") LiveEvent.REPORT_DAMAGE else LiveEvent.REPORT_INCIDENT,reportText);reportText="";reportKind=null}) {Text("Сохранить в журнал")}})
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
            Text("ЭТАП $step / 13 · LIVE ДЕМО",
                fontSize = 10.sp, color = LiveGreen, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { ((step.toIntOrNull() ?: 1) / 13f).coerceIn(0f, 1f) },
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
@Composable internal fun LiveRouteMap(car: Vehicle?, fraction: Float, mode: String,
    modifier: Modifier = Modifier.fillMaxWidth().height(218.dp)) {
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
            setBuiltInZoomControls(false)
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
    Box(modifier.clip(RoundedCornerShape(20.dp))) {
    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize(),
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
    Column(Modifier.align(Alignment.BottomStart).padding(start=9.dp,bottom=24.dp)) {
        FilledIconButton(onClick={map.controller.zoomIn()},colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=LiveGreen)) {Icon(Icons.Outlined.Add,"Приблизить маршрут")}
        FilledIconButton(onClick={map.controller.zoomOut()},colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=LiveGreen)) {Icon(Icons.Outlined.Remove,"Отдалить маршрут")}
    }
    FilledIconButton(onClick={map.controller.animateTo(route[centerIndexForRoute(route.size)]);map.controller.setZoom(15.0)},modifier=Modifier.align(Alignment.BottomEnd).padding(end=9.dp,bottom=24.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=LiveGreen)) {Icon(Icons.Outlined.MyLocation,"Центр учебного маршрута")}
    Text("Учебная траектория · не GPS · тайлы OSM требуют интернет",modifier=Modifier.align(Alignment.BottomCenter).background(Color.White.copy(alpha=.9f)).padding(4.dp),color=LiveGray,fontSize=8.sp)
    }
}

@Composable
private fun PremiumReturnStep(number: Int, label: String, completed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(color = if (completed) LiveGreen else LivePale,
            shape = RoundedCornerShape(12.dp)) {
            Box(Modifier.size(33.dp), contentAlignment = Alignment.Center) {
                if (completed) Icon(Icons.Outlined.Check, null,
                    tint = Color.White, modifier = Modifier.size(18.dp))
                else Text(number.toString(), color = LiveGreen,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Text(label, fontSize = 13.sp, color = LiveInk,
            fontWeight = if (completed) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable private fun DocumentStatusCard(name:String,asset:String,progress:Float,issue:String) {
    Surface(color=Color.White,shape=RoundedCornerShape(20.dp),shadowElevation=1.dp) {
        Row(Modifier.fillMaxWidth().padding(9.dp),verticalAlignment=Alignment.CenterVertically) {
            AsyncImage("file:///android_asset/premium/$asset.webp","Учебный образец: $name",contentScale=ContentScale.Fit,modifier=Modifier.size(105.dp,125.dp).clip(RoundedCornerShape(15.dp)).background(LivePale))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(name,fontSize=17.sp,fontWeight=FontWeight.Bold,color=LiveInk)
                Text(if(issue.isNotEmpty()) issue else if(progress>=1f) "✓ Проверено · демо" else "Проверяем образец…",fontSize=11.sp,color=if(issue.isEmpty()) LiveGreen else Color(0xFFB3261E))
                LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)),color=Color(0xFF009C50),trackColor=PremiumTheme.Stroke)
                Text("${(progress*100).toInt()}%",fontWeight=FontWeight.Bold,color=LiveInk)
                Text("✓ Данные образца найдены\n✓ Категория и срок действия",fontSize=10.sp,lineHeight=16.sp,color=LiveGray)
            }
        }
    }
}
