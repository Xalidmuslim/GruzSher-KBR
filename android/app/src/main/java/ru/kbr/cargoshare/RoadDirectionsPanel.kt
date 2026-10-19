package ru.kbr.cargoshare

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared route-status widget for the fullscreen fleet map and a selected car map.
 * Permission is requested by an explicit button. Road length and plotted geometry
 * always come from the SAME successful OSRM response.
 */
@Composable
internal fun RoadDirectionsPanel(
    vehicle: Vehicle,
    onRouteChange: (RoadRoute?) -> Unit
) {
    val context = LocalContext.current
    var granted by remember(vehicle.id) {
        mutableStateOf(hasPhoneLocationPermission(context))
    }
    var refresh by remember(vehicle.id) { mutableIntStateOf(0) }
    var loading by remember(vehicle.id) { mutableStateOf(false) }
    var message by remember(vehicle.id) { mutableStateOf("Определяем GPS…") }
    var route by remember(vehicle.id) { mutableStateOf<RoadRoute?>(null) }
    val latestRouteCallback by rememberUpdatedState(onRouteChange)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = hasPhoneLocationPermission(context)
        refresh += 1
    }

    LaunchedEffect(vehicle.id, refresh, granted) {
        latestRouteCallback(null)
        route = null
        val lat = vehicle.latitude
        val lon = vehicle.longitude
        if (lat == null || lon == null ||
            !lat.isFinite() || !lon.isFinite() ||
            lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            message = "Нет координат автомобиля"
            return@LaunchedEffect
        }
        if (!granted) {
            message = "Нужен доступ к GPS"
            return@LaunchedEffect
        }
        if (!locationServiceEnabled(context)) {
            message = "Геолокация выключена"
            return@LaunchedEffect
        }
        loading = true
        try {
            message = "Определяем GPS…"
            val fix = currentPhoneLocation(context)
            if (fix == null) {
                message = "Не удалось определить GPS"
                return@LaunchedEffect
            }
            message = "Строим маршрут по улицам…"
            val result = fetchDemoDrivingRoute(fix, lat, lon)
            if (result == null) {
                message = "Маршрут по дорогам недоступен"
                return@LaunchedEffect
            }
            route = result
            latestRouteCallback(result)
            message = humanDistance(result.distanceMeters)
        } finally {
            loading = false
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.DirectionsCar, contentDescription = null,
            modifier = Modifier.size(17.dp), tint = PremiumTheme.GreenDark)
        Text("По дорогам", fontSize = 11.sp, color = PremiumTheme.GreenDark,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp, color = PremiumTheme.GreenDark)
        }
        Text(message, color = if (route != null) PremiumTheme.GreenDark else PremiumTheme.Muted,
            fontSize = if (route != null) 18.sp else 10.sp,
            fontWeight = if (route != null) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2)
        if (!loading && route == null) {
            TextButton(
                modifier = Modifier.heightIn(min = 30.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                onClick = {
                    when {
                        !granted -> permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION))
                        !locationServiceEnabled(context) -> {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            refresh += 1
                        }
                        else -> refresh += 1
                    }
                }
            ) {
                Text(if (!granted) "Разрешить" else "Повторить", fontSize = 10.sp)
            }
        }
    }
}
