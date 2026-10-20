package ru.kbr.cargoshare

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Full-height booking detail; replaces the old half-screen modal.
 * Pricing and creation always come from the existing validated callbacks.
 */
@Composable
fun FullScreenVehicleBooking(
    vehicle: Vehicle,
    canBook: Boolean,
    offlineTraining: Boolean,
    onDismiss: () -> Unit,
    onTryLiveDemo: (Int) -> Unit,
    onQuote: suspend (ZonedDateTime, ZonedDateTime) -> Pair<Int, Boolean>,
    onBook: suspend (ZonedDateTime, ZonedDateTime, String) -> Boolean,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.of("Europe/Moscow") }
    val scope = rememberCoroutineScope()
    var start by remember(vehicle.id) {
        mutableStateOf(ZonedDateTime.now(zone).plusDays(1)
            .withHour(10).withMinute(0).withSecond(0).withNano(0))
    }
    var hours by remember(vehicle.id) { mutableIntStateOf(4) }
    var quotedPrice by remember(vehicle.id) { mutableStateOf<Int?>(null) }
    var available by remember(vehicle.id) { mutableStateOf(false) }
    var checking by remember(vehicle.id) { mutableStateOf(true) }
    var submitting by remember(vehicle.id) { mutableStateOf(false) }
    var note by remember(vehicle.id) { mutableStateOf<String?>(null) }
    var booked by remember(vehicle.id) { mutableStateOf(false) }
    var pickupMapExpanded by remember(vehicle.id) { mutableStateOf(false) }
    var detailRoadRoute by remember(vehicle.id) { mutableStateOf<RoadRoute?>(null) }
    var detailRouteRequested by remember(vehicle.id) { mutableStateOf(false) }
    var detailRouteTap by remember(vehicle.id) { mutableIntStateOf(0) }
    var pickupAddress by remember(vehicle.id) { mutableStateOf<PickupAddressDetails?>(null) }
    LaunchedEffect(vehicle.id, vehicle.latitude, vehicle.longitude, vehicle.pickupLocation) {
        pickupAddress = null
        pickupAddress = findPickupAddress(context, vehicle)
    }
    val end = remember(start, hours) { start.plusHours(hours.toLong()) }
    LaunchedEffect(vehicle.id, start, hours) {
        quotedPrice = null
        checking = true
        note = null
        if (offlineTraining) {
            // No scheduled reservation or real availability check in offline LIVE mode.
            val days = hours / 24
            val remainder = hours % 24
            quotedPrice = days * vehicle.dailyPriceRub +
                minOf(remainder * vehicle.hourlyPriceRub, vehicle.dailyPriceRub)
            available = true
            checking = false
            return@LaunchedEffect
        }
        val valid = validDemoWindow(start.toInstant(), end.toInstant(), Instant.now())
        if (!valid) {
            checking = false
            available = false
            note = "Выберите дату в будущем (до 90 дней) и срок от 2 часов."
        } else try {
            val (amount, yes) = onQuote(start, end)
            quotedPrice = amount
            available = yes
            checking = false
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            checking = false
            available = false
            note = e.localizedMessage ?: "Не удалось проверить тариф."
        }
    }
    Dialog(onDismissRequest = onDismiss,
        // Let Android fit the dialog to the actual visible window before Compose
        // lays out the fixed booking footer. Edge-to-edge fullscreen dialogs may
        // otherwise report zero navigation insets and crop the primary CTA.
        properties = DialogProperties(usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true)) {
        // Reserve system safe drawing area on the full dialog, not on its
        // scrollable body, so the footer can't slide below Android gesture bar.
        Column(Modifier.fillMaxSize().background(PremiumTheme.Paper)
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(start = 11.dp,
                    end = 18.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.ArrowBack, "Назад", tint = PremiumTheme.Text)
                }
                Column(Modifier.weight(1f)) {
                    Text("ГРУЗШЕР", fontSize = 25.sp, fontWeight = FontWeight.Black,
                        color = PremiumTheme.GreenDark)
                    Text("Выбор грузовика", fontSize = 11.sp, color = PremiumTheme.Muted)
                }
                Surface(shape = RoundedCornerShape(30.dp), color = PremiumTheme.Mint) {
                    Text("ДЕМО", color = PremiumTheme.Green, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp).padding(top = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (offlineTraining) {
                    Surface(color = PremiumTheme.Mint, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = PremiumTheme.Green)
                            Text("Офлайн-симуляция · без реальной брони и оплаты",
                                fontSize = 11.sp, color = PremiumTheme.GreenDark)
                        }
                    }
                }
                PremiumGallery(vehicle)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vehicle.name, fontSize = 27.sp, lineHeight = 32.sp,
                        color = PremiumTheme.Text, fontWeight = FontWeight.ExtraBold)
                    Text(vehicle.type, color = PremiumTheme.Muted, fontSize = 14.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.weight(1f), color = PremiumTheme.Mint,
                            shape = RoundedCornerShape(30.dp)) {
                            Text(if (checking) "Проверяем доступность…" else if (available) "● Доступен · Демо" else "Недоступен на это время",
                                color = PremiumTheme.Green, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${vehicle.hourlyPriceRub} ₽/ч", fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold, color = PremiumTheme.GreenDark)
                    }
                }
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    BookingSpecTile(Icons.Outlined.Inventory2, "${vehicle.payloadKg} кг",
                        "Нагрузка", Modifier.weight(1f))
                    BookingSpecTile(Icons.Outlined.ViewInAr, "${vehicle.cargoVolumeM3} м³",
                        "Объём", Modifier.weight(1f))
                    BookingSpecTile(Icons.Outlined.Settings, vehicle.transmission,
                        "КПП", Modifier.weight(1f))
                }
                }
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Place, null, tint = PremiumTheme.Green,
                            modifier = Modifier.size(25.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Адрес автомобиля", color = PremiumTheme.Text,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(pickupAddress?.value ?: "Определяем адрес…",
                                fontSize = 12.sp, lineHeight = 16.sp,
                                color = PremiumTheme.Text, fontWeight = FontWeight.SemiBold)
                            Text(pickupCoordinates(vehicle.latitude, vehicle.longitude),
                                fontSize = 10.sp, color = PremiumTheme.GreenDark)
                            if (isTrainingPickup(vehicle.pickupLocation)) {
                                Text("Ближайший адрес — ориентир, стоянка учебная",
                                    color = PremiumTheme.Muted, fontSize = 10.sp, lineHeight = 13.sp)
                            } else {
                                Text(pickupAddress?.source ?: "Местоположение из каталога",
                                    color = PremiumTheme.Muted, fontSize = 10.sp, lineHeight = 13.sp)
                            }
                        }
                        Box(Modifier.width(122.dp).height(96.dp)
                            .clip(RoundedCornerShape(14.dp))) {
                            LiveRouteMap(vehicle, 0f, "пешком",
                                Modifier.fillMaxSize(), compact = true)
                            Surface(shape = RoundedCornerShape(9.dp), color = Color.White.copy(alpha = .94f),
                                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)) {
                                Icon(Icons.Outlined.Fullscreen, "Открыть карту на весь экран",
                                    tint = PremiumTheme.GreenDark,
                                    modifier = Modifier.padding(5.dp).size(18.dp))
                            }
                            // Intercept mini-map taps intentionally: this is a preview,
                            // not a separately scrollable map inside the booking sheet.
                            Box(Modifier.fillMaxSize().clickable { pickupMapExpanded = true })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumFeature("Без залога", Icons.Outlined.Shield, Modifier.weight(1f))
                    PremiumFeature("Учебная аренда", Icons.Outlined.Info, Modifier.weight(1f))
                }
                Surface(color = Color.White, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (!offlineTraining) {
                        Text("Дата и время получения", fontSize = 17.sp,
                            color = PremiumTheme.Text, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(13.dp),
                                onClick = {
                                    DatePickerDialog(context, { _, y, m, d ->
                                        start = java.time.LocalDate.of(y,m+1,d)
                                            .atTime(start.toLocalTime()).atZone(zone)
                                    },start.year,start.monthValue-1,start.dayOfMonth).show()
                                }
                            ) {
                                Icon(Icons.Outlined.CalendarMonth, null,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(start.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                                    fontSize = 12.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                shape = RoundedCornerShape(13.dp),
                                onClick = {
                                    TimePickerDialog(context, { _, h, m ->
                                        start = start.withHour(h).withMinute(m)
                                    },start.hour,start.minute,true).show()
                                }
                            ) {
                                Icon(Icons.Outlined.Schedule, null,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(start.format(DateTimeFormatter.ofPattern("HH:mm")),
                                    fontSize = 12.sp)
                            }
                        }
                        }
                        Text("Выберите тариф", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = PremiumTheme.Text)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            (if (offlineTraining) listOf(2,4,8) else listOf(2,4,8,24)).forEach { h ->
                                val selected = hours == h
                                Surface(onClick = { hours = h }, modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) PremiumTheme.Green else Color.White,
                                    border = BorderStroke(1.dp, if (selected) PremiumTheme.Green else PremiumTheme.Stroke)) {
                                    Column(Modifier.padding(horizontal = 3.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(if (h == 24) "Сутки" else "$h ч", fontSize = 19.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (selected) Color.White else PremiumTheme.Text)
                                        Text("${if (h == 24) vehicle.dailyPriceRub else minOf(h * vehicle.hourlyPriceRub, vehicle.dailyPriceRub)} ₽",
                                            fontSize = 11.sp, maxLines = 1,
                                            color = if (selected) Color.White else PremiumTheme.Muted)
                                        if (selected) Text("Выбрано", fontSize = 10.sp,
                                            color = Color.White.copy(alpha = .8f))
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(color = PremiumTheme.Mint, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.Schedule, null, tint = PremiumTheme.Green,
                                modifier = Modifier.size(27.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$hours ч аренды", fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold, color = PremiumTheme.GreenDark)
                                Text(if (offlineTraining) "Учебный сценарий · время начнётся после осмотра"
                                    else "${start.format(DateTimeFormatter.ofPattern("dd.MM, HH:mm"))} — ${end.format(DateTimeFormatter.ofPattern("dd.MM, HH:mm"))}",
                                    fontSize = 11.sp, color = PremiumTheme.Muted)
                            }
                        }
                        Text(if (offlineTraining) "Расчёт учебного тарифа · списания нет"
                             else if (checking) "Проверяем стоимость и доступность…"
                             else if (!available) "Недоступно на это время"
                             else "Стоимость рассчитана для выбранного времени",
                            color = PremiumTheme.Muted, fontSize = 11.sp)
                    }
                }
                if (note != null) Text(note.orEmpty(), fontSize = 12.sp,
                    color = Color(0xFF9A4739))
                Text(if (offlineTraining) "Далее — учебный вход, автоматическая проверка образцов, карта и осмотр. Без ожидания диспетчера."
                     else "Для реального бронирования нужны проверка допуска и серверное подтверждение.",
                    fontSize = 11.sp, color = PremiumTheme.Muted, lineHeight = 16.sp)
            }
            Surface(color = Color.White, shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()) {
                // Compact fixed footer. Safe drawing inset is applied to
                // the dialog root; only a modest extra gap is needed here.
                // Both rows size to their CONTENT even when text is enlarged.
                Column(Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text("Итого за ${hours} ч", fontSize = 10.sp,
                                color = PremiumTheme.Muted, maxLines = 1)
                            Text(if (checking) "Рассчитываем…"
                                 else if (available && quotedPrice != null)
                                     "${quotedPrice} ₽" else "Нет тарифа",
                                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                                color = PremiumTheme.GreenDark, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        if (vehicle.latitude != null && vehicle.longitude != null) {
                            OutlinedButton(
                                onClick = {
                                    detailRoadRoute = null
                                    detailRouteRequested = true
                                    detailRouteTap += 1
                                    pickupMapExpanded = true
                                },
                                modifier = Modifier.heightIn(min = 34.dp),
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(
                                    horizontal = 9.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, PremiumTheme.Green),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = PremiumTheme.GreenDark)
                            ) {
                                Icon(Icons.Outlined.AltRoute, contentDescription = null,
                                    modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Построить маршрут", fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (offlineTraining) {
                        Button(
                            onClick = { onTryLiveDemo(hours) },
                            enabled = canBook && !checking && quotedPrice != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PremiumTheme.Green)
                        ) {
                            Icon(Icons.Outlined.VerifiedUser, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Продолжить к автопроверке · ДЕМО",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, lineHeight = 16.sp,
                                overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                    Button(
                        onClick = {
                            if (!submitting) {
                                submitting = true
                                val key = UUID.randomUUID().toString()
                                scope.launch {
                                    try {
                                        booked = onBook(start,end,key)
                                        note = if (booked) "Учебная заявка создана. Откройте раздел «Поездки»."
                                            else "Заявка не создана. Проверьте выбранное время."
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (e: Exception) { note = e.localizedMessage ?: "Ошибка создания заявки" }
                                    finally { submitting = false }
                                }
                            }
                        },
                        enabled = canBook && !checking && available && quotedPrice != null && !submitting,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumTheme.Green)
                    ) {
                        Text(if (submitting) "Создаём заявку…" else if (booked) "Создать ещё заявку"
                             else "Забронировать · ДЕМО", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(7.dp))
                        Icon(Icons.Outlined.ArrowForward, null, modifier = Modifier.size(17.dp))
                    }
                    }
                    if (!offlineTraining) {
                        OutlinedButton(onClick = { onTryLiveDemo(hours.coerceAtMost(8)) },
                            modifier = Modifier.fillMaxWidth().height(45.dp),
                            shape = RoundedCornerShape(15.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Пройти учебный сценарий", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    if (pickupMapExpanded) {
        Dialog(onDismissRequest = { pickupMapExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false)) {
            Column(Modifier.fillMaxSize().background(PremiumTheme.Paper)
                .windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().background(Color.White)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pickupMapExpanded = false }) {
                        Icon(Icons.Outlined.ArrowBack, "Закрыть карту",
                            tint = PremiumTheme.GreenDark)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(vehicle.name, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            color = PremiumTheme.GreenDark)
                        Text("Расположение автомобиля на карте", fontSize = 11.sp,
                            color = PremiumTheme.Muted)
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    // Full-bleed map continues behind the floating address glass.
                    // Tapping the only vehicle does not discard a route.
                    NalchikMap(listOf(vehicle), onSelect = {
                        detailRoadRoute = null
                        detailRouteRequested = true
                        detailRouteTap += 1
                    }, modifier = Modifier.fillMaxSize(), selectedVehicleId = vehicle.id,
                        roadRoute = detailRoadRoute)

                    // Card is short and the route button is a separate action
                    // below it. OS safeDrawing handles Android gesture navigation.
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White.copy(alpha = .96f),
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 4.dp
                        ) {
                            Column(Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Адрес автомобиля", color = PremiumTheme.GreenDark,
                                    fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(pickupAddress?.value ?: "Определяем адрес…",
                                    color = PremiumTheme.Text, fontSize = 12.sp,
                                    maxLines = 2, lineHeight = 15.sp,
                                    overflow = TextOverflow.Ellipsis)
                                if (detailRouteRequested) {
                                    key(vehicle.id, detailRouteTap) {
                                        RoadDirectionsPanel(vehicle) { detailRoadRoute = it }
                                    }
                                } else {
                                    Text(pickupCoordinates(vehicle.latitude, vehicle.longitude),
                                        color = PremiumTheme.Muted, fontSize = 10.sp)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                detailRoadRoute = null
                                detailRouteRequested = true
                                detailRouteTap += 1
                            },
                            modifier = Modifier.align(Alignment.End).heightIn(min = 38.dp),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, PremiumTheme.GreenDark),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = PremiumTheme.GreenDark)
                        ) {
                            Icon(Icons.Outlined.AltRoute, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(if (detailRoadRoute != null) "Перестроить маршрут"
                                 else "Построить маршрут", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingSpecTile(symbol: androidx.compose.ui.graphics.vector.ImageVector,
    value: String, label: String, modifier: Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(vertical = 13.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(symbol, null, tint = PremiumTheme.Green, modifier = Modifier.size(21.dp))
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = PremiumTheme.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(label, color = PremiumTheme.Muted, fontSize = 10.sp)
        }
    }
}
