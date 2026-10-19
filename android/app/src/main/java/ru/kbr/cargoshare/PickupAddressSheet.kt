package ru.kbr.cargoshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
    car: Vehicle, onDismiss: () -> Unit, onSelect: () -> Unit
) {
    val context = LocalContext.current
    var address by remember(car.id, car.latitude, car.longitude) {
        mutableStateOf<PickupAddressDetails?>(null)
    }
    var feedback by remember { mutableStateOf<String?>(null) }
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
            if (isTrainingPickup(car.pickupLocation)) {
                Text("Это учебная точка, не фактическое местоположение автомобиля. " +
                    "Номер дома и место парковки должны подтверждаться владельцем.",
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
