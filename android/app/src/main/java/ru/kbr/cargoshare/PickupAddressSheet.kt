package ru.kbr.cargoshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.location.Location
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.GpsFixed
import kotlin.math.roundToInt
import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** User-visible fallback when reverse geocoding is unavailable. */
internal fun pickupCoordinates(lat: Double?, lon: Double?): String =
    if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() ||
        lat !in -90.0..90.0 || lon !in -180.0..180.0
    ) "Координаты не указаны"
    else String.format(Locale.US, "%.5f, %.5f", lat, lon)

/** Never present a synthetic demo pickup label as a verified street address. */
internal fun isTrainingPickup(label: String): Boolean =
    label.isBlank() || listOf("учебн", "демо", "вымышлен", "test", "не gps")
        .any { label.contains(it, ignoreCase = true) }

internal data class PickupAddressDetails(val value: String, val source: String)

/**
 * Offline demos contain fictitious pickup points. On supported phones Geocoder
 * offers the nearest mappable street/address, NOT proof of a parked vehicle.
 * An operator-provided pickupLocation takes precedence for non-training vehicles.
 */
internal suspend fun findPickupAddress(context: Context, vehicle: Vehicle): PickupAddressDetails {
    if (!isTrainingPickup(vehicle.pickupLocation))
        return PickupAddressDetails(vehicle.pickupLocation, "Адрес из каталога (уточните точку выдачи)")
    val lat = vehicle.latitude
    val lon = vehicle.longitude
    if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() ||
        lat !in -90.0..90.0 || lon !in -180.0..180.0
    ) return PickupAddressDetails("Уточните местоположение у владельца", "Адрес не указан")
    return withContext(Dispatchers.IO) {
        val found = runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            @Suppress("DEPRECATION")
            Geocoder(context.applicationContext, Locale.forLanguageTag("ru-RU"))
                .getFromLocation(lat, lon, 1)?.firstOrNull()
        }.getOrNull()
        val result = found?.let {
            val street = it.thoroughfare?.trim().orEmpty()
            val building = it.subThoroughfare?.trim().orEmpty()
            val city = it.locality?.trim().orEmpty()
            val parts = listOf(city, street, building).filter(String::isNotBlank)
            if (street.isNotBlank()) parts.joinToString(", ") else null
        }
        if (result != null) PickupAddressDetails(result,
            "Ближайший адрес по карте, не подтверждён как место стоянки")
        else PickupAddressDetails("Адрес возле метки определить не удалось",
            "Ориентируйтесь по координатам учебной точки")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickupAddressSheet(
    car: Vehicle, onDismiss: () -> Unit, onSelect: () -> Unit,
    onBuildRoute: () -> Unit
) {
    val context = LocalContext.current
    var address by remember(car.id, car.latitude, car.longitude) {
        mutableStateOf<PickupAddressDetails?>(null)
    }
    var feedback by remember { mutableStateOf<String?>(null) }
    var gpsPermission by remember { mutableStateOf(hasPhoneLocationPermission(context)) }
    var refreshLocation by remember(car.id) { mutableIntStateOf(0) }
    var gpsLocation by remember(car.id) { mutableStateOf<Location?>(null) }
    var gpsBusy by remember(car.id) { mutableStateOf(false) }
    var gpsStatus by remember(car.id) { mutableStateOf<String?>(null) }
    val askGpsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        gpsPermission = hasPhoneLocationPermission(context)
        if (!gpsPermission) gpsStatus = "Нет доступа к геолокации телефона."
        else refreshLocation += 1
    }
    LaunchedEffect(car.id, gpsPermission, refreshLocation) {
        gpsLocation = null
        gpsStatus = null
        if (!gpsPermission) {
            gpsStatus = "Для расчёта расстояния нужен доступ к местоположению."
            return@LaunchedEffect
        }
        if (!locationServiceEnabled(context)) {
            gpsStatus = "Геолокация отключена в настройках телефона."
            return@LaunchedEffect
        }
        gpsBusy = true
        try {
            gpsLocation = currentPhoneLocation(context)
            if (gpsLocation == null)
                gpsStatus = "Не удалось получить свежие координаты GPS. Попробуйте на открытом воздухе."
        } finally {
            gpsBusy = false
        }
    }
    LaunchedEffect(car.id, car.latitude, car.longitude, car.pickupLocation) {
        address = findPickupAddress(context, car)
    }
    val coordinates = pickupCoordinates(car.latitude, car.longitude)
    ModalBottomSheet(onDismissRequest = onDismiss,
        containerColor = PremiumTheme.Paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 19.dp)
            .padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null,
                    modifier = Modifier.size(26.dp), tint = PremiumTheme.GreenDark)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Где находится грузовик", fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, color = PremiumTheme.Text)
                    Text(car.name + " · " + car.hourlyPriceRub + " ₽/ч",
                        color = PremiumTheme.Muted, fontSize = 12.sp)
                }
            }
            Button(onClick = onBuildRoute,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PremiumTheme.Green)) {
                Icon(Icons.Outlined.AltRoute, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Построить маршрут по улицам",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Surface(color = Color.White, shape = RoundedCornerShape(17.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Адрес / ориентир", fontSize = 11.sp,
                        color = PremiumTheme.Muted)
                    if (address == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Уточняем ближайший адрес…", fontSize = 13.sp,
                            color = PremiumTheme.Muted)
                    } else {
                        Text(address!!.value, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, color = PremiumTheme.Text)
                        Text(address!!.source, color = PremiumTheme.Muted,
                            fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    HorizontalDivider()
                    Text("Координаты метки", fontSize = 11.sp,
                        color = PremiumTheme.Muted)
                    Text(coordinates, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, color = PremiumTheme.GreenDark)
                }
            }
            // The phone's real current position is used. The catalogue's truck
            // position remains a static training point until telematics connects.
            Surface(color = PremiumTheme.Mint, shape = RoundedCornerShape(17.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.GpsFixed, null,
                            tint = PremiumTheme.GreenDark, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Расстояние от вас", fontWeight = FontWeight.Bold,
                            color = PremiumTheme.GreenDark, modifier = Modifier.weight(1f))
                    }
                    val current = gpsLocation
                    val lat = car.latitude
                    val lon = car.longitude
                    if (current != null && lat != null && lon != null &&
                        lat in -90.0..90.0 && lon in -180.0..180.0) {
                        val meters = airDistanceMeters(current.latitude, current.longitude, lat, lon)
                        Text(humanDistance(meters), fontSize = 27.sp,
                            fontWeight = FontWeight.ExtraBold, color = PremiumTheme.GreenDark)
                        Text("По прямой от текущей геопозиции телефона до метки автомобиля. " +
                            "Это не расстояние по дорогам.",
                            color = PremiumTheme.Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        if (current.hasAccuracy()) {
                            Text("Точность координат телефона: примерно ±${current.accuracy.roundToInt()} м",
                                color = PremiumTheme.Muted, fontSize = 10.sp)
                        }
                    } else if (gpsBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Получаем координаты телефона…",
                            fontSize = 12.sp, color = PremiumTheme.Muted)
                    } else {
                        Text(gpsStatus ?: "Местоположение пока недоступно",
                            fontSize = 12.sp, color = PremiumTheme.Muted)
                        OutlinedButton(onClick = {
                            if (!hasPhoneLocationPermission(context)) {
                                askGpsPermission.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION))
                            } else if (!locationServiceEnabled(context)) {
                                val opened = runCatching {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.isSuccess
                                if (!opened) gpsStatus = "Включите геолокацию в настройках Android."
                            } else {
                                refreshLocation += 1
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (!gpsPermission) "Разрешить доступ к GPS"
                                else if (!locationServiceEnabled(context)) "Включить геолокацию"
                                else "Повторить определение")
                        }
                    }
                }
            }
            if (isTrainingPickup(car.pickupLocation)) {
                Text("Координаты грузовика пока учебные, не передаются реальным GPS-трекером. " +
                    "Реальная геопозиция телефона не подтверждает фактическую стоянку грузовика.",
                    fontSize = 11.sp, color = PremiumTheme.Muted, lineHeight = 16.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val value = if (address?.value?.let { it !=
                        "Адрес возле метки определить не удалось" } == true)
                        address!!.value + " · " + coordinates else coordinates
                    clipboard.setPrimaryClip(ClipData.newPlainText("ГрузШер: местоположение", value))
                    feedback = "Адрес и координаты скопированы"
                }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.ContentCopy, null,
                        modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Копировать", fontSize = 12.sp)
                }
                OutlinedButton(onClick = {
                    val lat = car.latitude
                    val lon = car.longitude
                    if (lat != null && lon != null) {
                        val coords = String.format(Locale.US, "%f,%f", lat, lon)
                        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(coords + "(" + car.name + ")"))
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        val opened = runCatching {
                            context.startActivity(Intent.createChooser(intent, "Открыть точку на карте"))
                        }.isSuccess
                        if (!opened) feedback = "Приложение для карт не найдено"
                    } else feedback = "Координаты отсутствуют"
                }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Map, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("На карте", fontSize = 12.sp)
                }
            }
            feedback?.let {
                Text(it, color = PremiumTheme.GreenDark, fontSize = 12.sp)
            }
            Button(onClick = onSelect, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp)) {
                Text("Посмотреть автомобиль")
            }
        }
    }
}
