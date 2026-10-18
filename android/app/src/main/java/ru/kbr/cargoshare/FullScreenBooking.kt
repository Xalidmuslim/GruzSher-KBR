package ru.kbr.cargoshare

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(PremiumTheme.Paper)) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(start = 11.dp,
                    end = 18.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.ArrowBack, "Назад", tint = PremiumTheme.Text)
                }
                Column(Modifier.weight(1f)) {
                    Text("ГрузШер", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = PremiumTheme.Text)
                    Text("Выбор грузовика", fontSize = 11.sp, color = PremiumTheme.Muted)
                }
                Surface(shape = RoundedCornerShape(30.dp), color = PremiumTheme.Mint) {
                    Text("ДЕМО", color = PremiumTheme.Green, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp).padding(top = 14.dp, bottom = 25.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.fillMaxWidth().height(228.dp)
                    .clip(RoundedCornerShape(24.dp)).background(Color.White)) {
                    TruckArtwork(vehicle, Modifier.fillMaxSize().padding(13.dp))
                    Surface(modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                        shape = RoundedCornerShape(24.dp), color = PremiumTheme.Green) {
                        Text("● Доступен · Демо",
                            color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vehicle.name, fontSize = 28.sp, lineHeight = 33.sp,
                        color = PremiumTheme.Text, fontWeight = FontWeight.ExtraBold)
                    Text(vehicle.type, color = PremiumTheme.Muted, fontSize = 14.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    BookingSpecTile(Icons.Outlined.Inventory2, "${vehicle.payloadKg} кг",
                        "Нагрузка", Modifier.weight(1f))
                    BookingSpecTile(Icons.Outlined.ViewInAr, "${vehicle.cargoVolumeM3} м³",
                        "Объём", Modifier.weight(1f))
                    BookingSpecTile(Icons.Outlined.Settings, vehicle.transmission,
                        "КПП", Modifier.weight(1f))
                }
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, null, tint = PremiumTheme.Green)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Точка получения", color = PremiumTheme.Text,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(vehicle.pickupLocation, fontSize = 12.sp,
                                color = PremiumTheme.Muted)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumFeature("Без залога", Icons.Outlined.Shield, Modifier.weight(1f))
                    PremiumFeature("Учебная аренда", Icons.Outlined.Info, Modifier.weight(1f))
                }
                Surface(color = Color.White, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)) {
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
                        Text(if (offlineTraining) "Срок учебной аренды" else "Продолжительность", fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, color = PremiumTheme.Text)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            (if (offlineTraining) listOf(2,4,8) else listOf(2,4,8,24)).forEach { h ->
                                FilterChip(
                                    selected = hours == h,
                                    onClick = { hours = h },
                                    label = { Text(if(h==24) "Сутки" else "$h ч",
                                        fontSize = 12.sp, maxLines = 1) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = PremiumTheme.Paper,
                                        selectedContainerColor = PremiumTheme.Green,
                                        selectedLabelColor = Color.White)
                                )
                            }
                        }
                    }
                }
                Surface(color = PremiumTheme.Mint, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Тариф аренды", fontSize = 12.sp, color = PremiumTheme.Green)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${vehicle.hourlyPriceRub} ₽/ч",
                                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                                color = PremiumTheme.GreenDark, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.ReceiptLong, null, tint = PremiumTheme.Green)
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
            Surface(color = Color.White, shadowElevation = 12.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Итого за ${hours} ч", fontSize = 11.sp, color = PremiumTheme.Muted)
                            Text(if (checking) "Рассчитываем…" else if (available && quotedPrice != null)
                                    "${quotedPrice} ₽" else "Нет тарифа",
                                fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                                color = PremiumTheme.GreenDark)
                        }
                        Text("Без залога", fontSize = 12.sp, color = PremiumTheme.Green)
                    }
                    if (offlineTraining) {
                        Button(
                            onClick = { onTryLiveDemo(hours) },
                            enabled = canBook && !checking && quotedPrice != null,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PremiumTheme.Green)
                        ) {
                            Icon(Icons.Outlined.VerifiedUser, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Продолжить к автопроверке · ДЕМО",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
