package ru.kbr.cargoshare

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Second, optional native map engine. Neither the truck coordinates nor the
 * parking polygons are real telemetry: the existing demo model is unchanged.
 *
 * Liberty is a public, token-free vector style provided by OpenFreeMap. Network
 * availability and the provider's fair-use policy still apply.
 */
private const val LIBRE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private val LIBRE_FOREST = Color(0xFF184F3F)
private const val CENTER_LAT = 43.4853
private const val CENTER_LON = 43.6071

internal class LibreMapHandle(val view: MapView) {
    var map by mutableStateOf<MapLibreMap?>(null)
    var loading by mutableStateOf(true)
}

/** Lifecycle is intentionally paired with the Compose AndroidView's lifetime. */
@Composable
internal fun rememberLibreMap(initialLat: Double, initialLon: Double, zoom: Double): LibreMapHandle {
    val context = LocalContext.current
    val handle = remember(context, initialLat, initialLon, zoom) {
        MapLibre.getInstance(context.applicationContext)
        LibreMapHandle(MapView(context).apply {
            onCreate(null)
            // The fleet map is nested inside a Compose LazyColumn. Without this,
            // vertical single-finger drags can be intercepted as page scrolling.
            // Do not consume touch events: MapLibre must still receive panning,
            // marker taps and pinch-to-zoom.
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_MOVE ->
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL ->
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        })
    }
    DisposableEffect(handle) {
        handle.view.onStart()
        handle.view.onResume()
        handle.view.getMapAsync { map ->
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isZoomGesturesEnabled = true
            map.uiSettings.isScrollGesturesEnabled = true
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            map.setMinZoomPreference(8.0)
            map.setMaxZoomPreference(19.0)
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(initialLat, initialLon)).zoom(zoom).build()
            map.setStyle(LIBRE_STYLE_URL) { style ->
                applyRussianLabels(style)
                handle.map = map
                handle.loading = false
            }
        }
        onDispose {
            handle.map = null
            handle.view.onPause()
            handle.view.onStop()
            handle.view.onDestroy()
        }
    }
    return handle
}

/**
 * Russian OSM labels, where a Russian name is present in vector source data.
 * Original names remain as a fallback; never transliterate unknown locations.
 * Avoid road shields, transport refs and airport codes that require their own
 * dedicated style expressions.
 */
private fun applyRussianLabels(style: org.maplibre.android.maps.Style) {
    val kinds = listOf("place", "settlement", "town", "city", "village",
        "country", "state", "poi", "park", "water", "street", "road_label", "roadname")
    val name = Expression.coalesce(
        Expression.get("name:ru"), Expression.get("name_ru"), Expression.get("name"))
    style.layers.filterIsInstance<SymbolLayer>()
        .filter { layer -> kinds.any { layer.id.contains(it, ignoreCase = true) } }
        .forEach { layer ->
            runCatching { layer.setProperties(PropertyFactory.textField(name)) }
        }
}

private fun zoomLibre(map: MapLibreMap?, increase: Boolean) {
    if (map == null) return
    map.animateCamera(if (increase) CameraUpdateFactory.zoomIn()
        else CameraUpdateFactory.zoomOut())
}

@Composable
internal fun MapLibreFleetMap(
    cars: List<Vehicle>,
    onSelect: (Vehicle) -> Unit,
    modifier: Modifier = Modifier,
    selectedVehicleId: String? = null
) {
    val context = LocalContext.current
    val shownPrice = remember(context) {
        context.applicationContext.getSharedPreferences("gruzsher_preferences", 0)
            .getBoolean("map_prices_visible", true)
    }
    val handle = rememberLibreMap(CENTER_LAT, CENTER_LON, 12.7)
    val localZones = rememberLocalParkingZones()
    val zonesVisible = remember(context) {
        context.applicationContext.getSharedPreferences("gruzsher_preferences", 0)
            .getBoolean("map_parking_zones_visible", true)
    }
    val map = handle.map
    val currentOnSelect by rememberUpdatedState(onSelect)
    var status by remember { mutableStateOf<String?>(null) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) status = "Доступ разрешён. Нажмите геолокацию ещё раз для центрирования."
        else status = "Разрешите доступ к местоположению в настройках Android."
    }
    var lastSelected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(map, cars, shownPrice, selectedVehicleId, localZones, zonesVisible) {
        if (map == null) return@LaunchedEffect
        map.clear()
        if (zonesVisible) {
            localZones.filter { it.enabled }.forEach { zone ->
                map.addPolygon(zoneLibrePolygon(zone))
            }
        }
        val icons = IconFactory.getInstance(context)
        cars.forEach { vehicle ->
            val lat = vehicle.latitude
            val lon = vehicle.longitude
            if (lat != null && lon != null) {
                val bitmap = truckMapMarker(context,
                    if (shownPrice) vehicle.hourlyPriceRub else null,
                    selectedVehicleId == vehicle.id) as BitmapDrawable
                map.addMarker(MarkerOptions()
                    .position(LatLng(lat, lon))
                    .title(vehicle.name)
                    .snippet(vehicle.pickupLocation)
                    .icon(icons.fromBitmap(bitmap.bitmap)))
            }
        }
        val selected = cars.firstOrNull { it.id == selectedVehicleId }
        if (selected != null && lastSelected != selectedVehicleId &&
            selected.latitude != null && selected.longitude != null) {
            map.animateCamera(CameraUpdateFactory.newLatLng(
                LatLng(selected.latitude, selected.longitude)))
        }
        lastSelected = selectedVehicleId
    }
    DisposableEffect(map, cars) {
        map?.setOnMarkerClickListener { marker: Marker ->
            cars.firstOrNull { it.name == marker.title }?.let(currentOnSelect)
            true
        }
        onDispose { map?.setOnMarkerClickListener(null) }
    }

    fun centerOnPhone() {
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider -> lm.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
        }.getOrNull()
        if (last != null && System.currentTimeMillis() - last.time < 120_000L) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(last.latitude, last.longitude), 15.5))
            status = "Показано последнее доступное положение телефона"
        } else {
            status = "Свежая геопозиция недоступна. В OSMDroid доступно ожидание GPS."
        }
    }
    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        AndroidView(factory = { handle.view }, modifier = Modifier.fillMaxSize())
        if (handle.loading) {
            Surface(Modifier.align(Alignment.Center), color = Color.White.copy(alpha = .91f),
                shape = RoundedCornerShape(14.dp)) {
                Text("Загружаем MapLibre…", Modifier.padding(12.dp), fontSize = 12.sp)
            }
        }
        SmallFloatingActionButton(onClick = ::centerOnPhone,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            containerColor = Color.White, contentColor = LIBRE_FOREST) {
            Icon(Icons.Outlined.MyLocation, "Моё местоположение")
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallFloatingActionButton(onClick = { zoomLibre(map, true) },
                containerColor = Color.White, contentColor = LIBRE_FOREST) {
                Icon(Icons.Outlined.Add, "Приблизить")
            }
            SmallFloatingActionButton(onClick = { zoomLibre(map, false) },
                containerColor = Color.White, contentColor = LIBRE_FOREST) {
                Icon(Icons.Outlined.Remove, "Отдалить")
            }
        }
        status?.let {
            Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, start = 9.dp, end = 66.dp),
                shape = RoundedCornerShape(11.dp), color = Color.White) {
                Text(it, Modifier.padding(8.dp), fontSize = 11.sp)
            }
        }
        Text("MapLibre · OpenFreeMap · © OpenStreetMap",
            modifier = Modifier.align(Alignment.BottomStart)
                .background(Color.White.copy(alpha = .88f), RoundedCornerShape(7.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            fontSize = 8.sp, color = LIBRE_FOREST)
    }
}

@Composable
internal fun MapLibreRouteMap(
    car: Vehicle?, fraction: Float, mode: String, modifier: Modifier,
    compact: Boolean, onExpand: (() -> Unit)?
) {
    val context = LocalContext.current
    val showZones = remember(context) {
        context.applicationContext.getSharedPreferences("gruzsher_preferences", 0)
            .getBoolean("map_parking_zones_visible", true)
    }
    val lat = car?.latitude ?: CENTER_LAT
    val lon = car?.longitude ?: CENTER_LON
    val route = remember(car?.id, mode) {
        if (mode == "пешком") listOf(
            LatLng(lat + .0012, lon - .0018), LatLng(lat, lon)
        ) else listOf(
            LatLng(lat, lon), LatLng(lat + .0010, lon + .0008),
            LatLng(lat + .0020, lon + .0020),
            LatLng(lat + .0032, lon + .0024), LatLng(lat + .0044, lon + .0027)
        )
    }
    val handle = rememberLibreMap(
        route[route.size / 2].latitude,
        route[route.size / 2].longitude,
        if (compact) 14.5 else 15.0)
    val map = handle.map
    val localZones = rememberLocalParkingZones()
    var moving by remember(map) { mutableStateOf<Marker?>(null) }
    val last = route.last()
    LaunchedEffect(map, route, mode, showZones, compact, localZones) {
        if (map == null) return@LaunchedEffect
        map.clear()
        if (!compact && showZones) localZones.filter { it.enabled }.forEach { zone ->
            map.addPolygon(zoneLibrePolygon(zone))
        }
        val icons = IconFactory.getInstance(context)
        map.addPolyline(PolylineOptions().addAll(route)
            .color(AndroidColor.rgb(24, 79, 63)).width(5f))
        if (mode != "пешком" && !compact && showZones) {
            map.addPolygon(PolygonOptions()
                .addAll(libCircle(last.latitude, last.longitude,
                    DEMO_PARKING_ZONE_RADIUS_METERS))
                .fillColor(AndroidColor.argb(72, 0, 172, 102))
                .strokeColor(AndroidColor.rgb(0, 132, 77)))
            map.addPolygon(PolygonOptions()
                .addAll(libCircle(last.latitude + .0018, last.longitude + .0022, 45.0))
                .fillColor(AndroidColor.argb(65, 216, 53, 53))
                .strokeColor(AndroidColor.rgb(193, 42, 42)))
        }
        val first = (if (mode == "пешком") walkingMapMarker(context)
            else truckMapMarker(context)) as BitmapDrawable
        moving = map.addMarker(MarkerOptions()
            .position(route.first())
            .title(if (mode == "пешком") "Учебный пешеход" else "Учебный грузовик")
            .icon(icons.fromBitmap(first.bitmap)))
        val destination = (if (mode == "пешком") truckMapMarker(context)
            else returnZoneMapMarker(context)) as BitmapDrawable
        map.addMarker(MarkerOptions().position(last)
            .title(if (mode == "пешком") "Автомобиль" else "Зона учебного возврата")
            .icon(icons.fromBitmap(destination.bitmap)))
    }
    LaunchedEffect(map, moving, fraction, route) {
        val current = moving ?: return@LaunchedEffect
        val geo = map ?: return@LaunchedEffect
        val part = fraction.coerceIn(0f, 1f) * (route.size - 1)
        val i = part.toInt().coerceAtMost(route.size - 2)
        val p = (part - i).coerceIn(0f, 1f)
        val start = route[i]
        val end = route[i + 1]
        current.position = LatLng(
            start.latitude + (end.latitude - start.latitude) * p,
            start.longitude + (end.longitude - start.longitude) * p)
        geo.updateMarker(current)
    }

    Box(modifier.clip(RoundedCornerShape(20.dp))) {
        AndroidView(factory = { handle.view }, modifier = Modifier.fillMaxSize())
        if (handle.loading) {
            Surface(Modifier.align(Alignment.Center), color = Color.White.copy(alpha = .88f),
                shape = RoundedCornerShape(11.dp)) {
                Text("MapLibre загружается…", Modifier.padding(12.dp), fontSize = 12.sp)
            }
        }
        if (onExpand != null) {
            FilledIconButton(onClick = onExpand,
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White, contentColor = LIBRE_FOREST)) {
                Icon(Icons.Outlined.Fullscreen, "Развернуть карту")
            }
        }
        if (!compact) {
            Column(Modifier.align(Alignment.TopStart).padding(start = 11.dp, top = 68.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick = { zoomLibre(map, true) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White, contentColor = LIBRE_FOREST)) {
                    Icon(Icons.Outlined.Add, "Приблизить")
                }
                FilledIconButton(onClick = { zoomLibre(map, false) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White, contentColor = LIBRE_FOREST)) {
                    Icon(Icons.Outlined.Remove, "Отдалить")
                }
            }
            FilledIconButton(onClick = {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    route[route.size / 2], 15.0))
            }, modifier = Modifier.align(Alignment.TopEnd)
                .padding(end = 12.dp, top = if (onExpand == null) 12.dp else 67.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White, contentColor = LIBRE_FOREST)) {
                Icon(Icons.Outlined.MyLocation, "Центр учебного маршрута")
            }
        }
        Text(if (compact) "MapLibre · © OSM"
            else if (mode == "пешком") "Учебная траектория · не GPS"
            else if (showZones) "Зелёная зона — можно · вне зоны — нельзя"
            else "Учебная траектория · не GPS",
            modifier = Modifier.align(Alignment.BottomCenter)
                .background(Color.White.copy(alpha = .89f),
                    RoundedCornerShape(8.dp)).padding(4.dp),
            fontSize = if (compact) 8.sp else 10.sp, color = LIBRE_FOREST)
    }
}

/** Illustrative circles only; not legal parking restrictions. */
private fun libCircle(lat: Double, lon: Double, radius: Double): List<LatLng> {
    val r = radius / 111_195.0
    val lonScale = cos(Math.toRadians(lat)).coerceAtLeast(.01)
    return (0..36).map { i ->
        val a = 2.0 * PI * i / 36.0
        LatLng(lat + r * sin(a), lon + r * cos(a) / lonScale)
    }
}

/** Distinct, user-configured local demonstration polygons. */
private fun zoneLibrePolygon(zone: LocalParkingZone): PolygonOptions =
    PolygonOptions().addAll(zone.vertices.map {
        LatLng(it.latitude, it.longitude)
    }).fillColor(if (zone.rule == ParkingRule.ALLOWED)
        AndroidColor.argb(65, 0, 152, 78) else AndroidColor.argb(75, 220, 48, 48))
        .strokeColor(if (zone.rule == ParkingRule.ALLOWED)
            AndroidColor.rgb(0, 133, 69) else AndroidColor.rgb(194, 38, 38))
