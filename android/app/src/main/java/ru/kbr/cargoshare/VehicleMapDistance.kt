package ru.kbr.cargoshare

import android.Manifest
import android.content.Intent
import android.location.Location
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The expanded pickup map displays the direct distance from real phone GPS
 * to the selected catalogue coordinate. It never invents a phone fix, car
 * telemetry position or a road-route distance.
 */
@Composable
internal fun VehicleMapDistance(vehicle: Vehicle) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lat = vehicle.latitude
    val lon = vehicle.longitude
    val validCarPoint = lat != null && lon != null &&
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    var permissionGranted by remember { mutableStateOf(hasPhoneLocationPermission(context)) }
    var retry by remember(vehicle.id) { mutableIntStateOf(0) }
    var phoneFix by remember(vehicle.id) { mutableStateOf<Location?>(null) }
    var loading by remember(vehicle.id) { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGranted = hasPhoneLocationPermission(context)
        if (permissionGranted) retry += 1
    }

    LaunchedEffect(vehicle.id, validCarPoint, permissionGranted, retry) {
        phoneFix = null
        if (!validCarPoint || !permissionGranted || !locationServiceEnabled(context)) return@LaunchedEffect
        loading = true
        try {
            phoneFix = currentPhoneLocation(context)
        } finally {
            loading = false
        }
    }

    val directMeters = if (validCarPoint && phoneFix != null)
        airDistanceMeters(phoneFix!!.latitude, phoneFix!!.longitude, lat!!, lon!!)
    else Double.NaN
    val hasDistance = directMeters.isFinite()
    val locationEnabled = locationServiceEnabled(context)

    Row(
        Modifier.fillMaxWidth().padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(Icons.Outlined.NearMe, contentDescription = null,
            tint = PremiumTheme.GreenDark, modifier = Modifier.size(16.dp))
        Text("До машины", fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, color = PremiumTheme.GreenDark)
        Text(when {
            !validCarPoint -> "Нет координат"
            !permissionGranted -> "Нужен GPS"
            !locationEnabled -> "GPS выключен"
            loading -> "Определяем…"
            hasDistance -> humanDistance(directMeters)
            else -> "GPS недоступен"
        }, modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = if (hasDistance) PremiumTheme.GreenDark else PremiumTheme.Muted,
            fontSize = if (hasDistance) 19.sp else 11.sp,
            fontWeight = if (hasDistance) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1)
        if (validCarPoint && !hasDistance && !loading) {
            TextButton(
                onClick = {
                    when {
                        !permissionGranted -> requestPermission.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION))
                        !locationEnabled -> {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            retry += 1
                        }
                        else -> retry += 1
                    }
                },
                modifier = Modifier.heightIn(min = 32.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(if (!permissionGranted) "Доступ" else "Обновить", fontSize = 10.sp)
            }
        }
    }
}
