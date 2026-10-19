package ru.kbr.cargoshare

import android.Manifest
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private val Forest = Color(0xFF184F3F)
private val DeepInk = Color(0xFF192923)
private val Parchment = Color(0xFFF6F7F2)
private val Fog = Color(0xFFEAF0EA)
private val Muted = Color(0xFF6C7D76)

// Coordinates are operator-confirmed in /v1/admin/vehicles. Demo vehicles without
// coordinates remain visible in the catalog, but never get a fabricated map pin.
private val NalchikCenter = GeoPoint(43.4853, 43.6071)


@Composable
fun PremiumCatalog(
    cars: List<Vehicle>,
    loading: Boolean,
    onSelect: (Vehicle) -> Unit,
    onRefresh: () -> Unit,
    onLiveDemo: () -> Unit,
) {
    var filter by remember { mutableStateOf("Все") }
    val filtered = remember(cars, filter) { cars.filter { categoryMatches(it.type, filter) } }
    val positioned = remember(filtered) { filtered.filter { it.latitude != null && it.longitude != null } }

    Column(Modifier.fillMaxSize().background(Parchment)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("ГРУЗШЕР", color = DeepInk, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = 0.6.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = Forest, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Нальчик и КБР", color = Muted, fontSize = 12.sp)
                }
            }
            Surface(shape = RoundedCornerShape(100), color = Fog) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalShipping, null, tint = Forest, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Без залога", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        OutlinedButton(
            onClick = onLiveDemo,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)
                .defaultMinSize(minHeight = 68.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Fog),
        ) {
            Icon(Icons.Outlined.PlayCircle, contentDescription = null, tint = Forest, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Пройти аренду — LIVE Демо", fontSize = 14.sp,
                    color = Forest, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 18.sp)
                Text("От выбора машины до возврата", fontSize = 11.sp,
                    color = Muted, maxLines = 2, lineHeight = 14.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Forest, modifier = Modifier.size(20.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            items(listOf("Все", "Фургоны", "Бортовые", "Рефрижераторы")) { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Forest, selectedLabelColor = Color.White,
                        containerColor = Color.White, labelColor = Muted,
                    ),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = filter == option),
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(292.dp).padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(24.dp)).background(Fog)
        ) {
            NalchikMap(positioned, onSelect, modifier = Modifier.fillMaxSize())
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.94f),
                shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Map, null, modifier = Modifier.size(15.dp), tint = Forest)
                    Spacer(Modifier.width(6.dp))
                    Text("Карта Нальчика", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DeepInk)
                }
            }
            if (positioned.isEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    shape = RoundedCornerShape(15.dp), color = Color.White.copy(alpha = 0.96f),
                ) {
                    Text(
                        "Машины появятся на карте после указания их координат администратором",
                        color = Muted, fontSize = 11.sp, lineHeight = 15.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Аренда грузовиков", color = DeepInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("${filtered.size} вариантов · демонстрационный каталог", color = Muted, fontSize = 11.sp)
            }
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Обновить каталог", tint = Forest)
            }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Forest)
            }
        } else if (filtered.isEmpty()) {
            Text("В этой категории пока нет автомобилей", Modifier.padding(24.dp), color = Muted)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { vehicle ->
                    PremiumTruckCard(vehicle, onClick = { onSelect(vehicle) })
                }
            }
        }
    }
}

@Composable
private fun PremiumTruckCard(vehicle: Vehicle, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val demoImage = when (vehicle.id) {
                    "gazelle-next" -> "file:///android_asset/truck_g01.webp"
                    "ford-transit" -> "file:///android_asset/truck_g02.webp"
                    "largus-van" -> "file:///android_asset/truck_g03.webp"
                    else -> ""
                }
                Box(
                    Modifier.size(width = 116.dp, height = 104.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Fog),
                    contentAlignment = Alignment.Center,
                ) {
                    if (vehicle.imageUrl.startsWith("https://") || demoImage.isNotEmpty()) {
                        AsyncImage(
                            model = vehicle.imageUrl.takeIf { it.startsWith("https://") } ?: demoImage,
                            contentDescription = "Изображение ${vehicle.name} (демо)",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(Icons.Outlined.LocalShipping, null, tint = Forest, modifier = Modifier.size(62.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(vehicle.name, color = DeepInk, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(vehicle.type, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${vehicle.payloadKg} кг  ·  ${vehicle.cargoVolumeM3} м³", color = Muted, fontSize = 12.sp)
                    Text("от ${vehicle.hourlyPriceRub} ₽ / ч", color = Forest, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 11.dp), color = Fog)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, tint = Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text(vehicle.pickupLocation, modifier = Modifier.weight(1f), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Выбрать", color = Forest, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Icon(Icons.Outlined.ChevronRight, null, tint = Forest, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun NalchikMap(cars: List<Vehicle>, onSelect: (Vehicle) -> Unit, modifier: Modifier = Modifier, selectedVehicleId: String? = null) {
    // One shared setting controls both inline/fullscreen fleet and LIVE routes.
    if (rememberMapEngine() == MAP_ENGINE_MAPLIBRE) {
        MapLibreFleetMap(cars, onSelect, modifier, selectedVehicleId)
        return
    }
    val context = LocalContext.current
    // The discovery map deliberately excludes parking zones and their observers.
    val showPrices = remember(context) {
        context.applicationContext.getSharedPreferences("gruzsher_preferences", 0)
            .getBoolean("map_prices_visible", true)
    }
    // A saveable primitive camera buffer, not Compose observable states. MapListener
    // fires dozens of times per pinch/drag; recomposing the screen on every tick
    // used to make the map background and overlays flicker.
    val camera = rememberSaveable { doubleArrayOf(NalchikCenter.latitude, NalchikCenter.longitude, 13.0) }
    var lastSelection by rememberSaveable { mutableStateOf(selectedVehicleId) }
    val map = remember(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            // OSMDroid normally renders a grey grid while loading newly zoomed tiles.
            // Use a flat map-like fallback instead of a changing grey grid.
            setBackgroundColor(android.graphics.Color.rgb(238, 242, 234))
            overlayManager.tilesOverlay.setLoadingBackgroundColor(android.graphics.Color.rgb(238, 242, 234))
            overlayManager.tilesOverlay.setLoadingLineColor(android.graphics.Color.rgb(238, 242, 234))
            setMultiTouchControls(true)
            // Compose's vertical list must not consume panning gestures inside the map.
            // Returning false lets osmdroid handle pan, zoom and pin clicks itself.
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            setBuiltInZoomControls(false) // OSMDroid defaults obscure truck pins
            controller.setZoom(camera[2])
            controller.setCenter(GeoPoint(camera[0], camera[1]))
            addMapListener(object : org.osmdroid.events.MapListener {
                override fun onScroll(event: org.osmdroid.events.ScrollEvent): Boolean {
                    camera[0] = mapCenter.latitude
                    camera[1] = mapCenter.longitude
                    return false
                }
                override fun onZoom(event: org.osmdroid.events.ZoomEvent): Boolean {
                    camera[2] = event.zoomLevel
                    return false
                }
            })
            minZoomLevel = 8.0
            maxZoomLevel = 19.0
        }
    }
    val locationOverlay = remember(map) {
        MyLocationNewOverlay(GpsMyLocationProvider(context), map).apply {
            val person = phoneLocationMarker(context)
            setDirectionArrow(person, person)
            setPersonIcon(person)
            setPersonHotspot(person.width / 2f, person.height / 2f)
        }
    }
    // The GPS-backed location provider requires precise location permission.
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val scope = rememberCoroutineScope()
    var locationMessage by remember { mutableStateOf<String?>(null) }
    fun centerOnGps() {
        locationMessage = "Получаем координаты телефона…"
        scope.launch {
            delay(15_000L)
            if (locationMessage == "Получаем координаты телефона…") {
                locationMessage = "GPS пока не определил координаты. Проверьте службы геолокации и повторите."
            }
        }
        // A fresh GPS fix may not exist when the user first taps the button.
        // Schedule map navigation on the UI thread as soon as coordinates arrive.
        locationOverlay.enableMyLocation()
        val immediateFix = locationOverlay.myLocation
        if (immediateFix != null) {
            map.controller.animateTo(immediateFix)
            map.controller.setZoom(15.5)
            locationMessage = null
        } else {
            locationOverlay.runOnFirstFix {
                map.post {
                    locationOverlay.myLocation?.let { point ->
                        map.controller.animateTo(point)
                        map.controller.setZoom(15.5)
                        locationMessage = null
                    }
                }
            }
        }
    }
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (permissionGranted) centerOnGps()
        else locationMessage = "Для определения позиции по GPS разрешите точное местоположение."
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val markerSignature = remember(cars) {
        cars.joinToString("|") { "${it.id}:${it.latitude}:${it.longitude}:${it.name}:${it.hourlyPriceRub}" }
    }
    val markerIcons = remember(context, markerSignature, showPrices) {
        cars.associate { vehicle ->
            // Apply the user's saved display preference, never mutate catalogue prices.
            val displayedRate = if (showPrices) vehicle.hourlyPriceRub else null
            vehicle.id to Pair(
                truckMapMarker(context, hourlyRub = displayedRate),
                truckMapMarker(context, hourlyRub = displayedRate, selected = true)
            )
        }
    }
    LaunchedEffect(selectedVehicleId, map, markerSignature) {
        val selected = cars.firstOrNull { it.id == selectedVehicleId }
        map.overlays.filterIsInstance<Marker>().forEach { marker ->
            val vehicle = cars.firstOrNull { it.name == marker.title }
            if (vehicle != null) {
                marker.icon = if (vehicle.id == selectedVehicleId)
                    markerIcons[vehicle.id]?.second else markerIcons[vehicle.id]?.first
            }
        }
        if (selectedVehicleId != lastSelection &&
            selected?.latitude != null && selected.longitude != null) {
            map.controller.animateTo(GeoPoint(selected.latitude, selected.longitude))
        }
        lastSelection = selectedVehicleId
        map.invalidate()
    }
    DisposableEffect(map, permissionGranted) {
        if (permissionGranted) {
            if (!map.overlays.contains(locationOverlay)) map.overlays.add(locationOverlay)
            locationOverlay.enableMyLocation()
        }
        onDispose { locationOverlay.disableMyLocation() }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    Box(modifier.clipToBounds()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize().clipToBounds(), update = { view ->
            if (view.tag != markerSignature) {
            view.overlays.removeAll { it is Marker }
            cars.forEach { vehicle ->
                val lat = vehicle.latitude
                val lon = vehicle.longitude
                if (lat != null && lon != null) {
                    val marker = Marker(view).apply {
                        position = GeoPoint(lat, lon)
                        title = vehicle.name
                        snippet = "${vehicle.hourlyPriceRub} ₽ / ч · Демоданные"
                        // Every truck shows its own hourly tariff from the actual catalogue.
                        icon = if (selectedVehicleId == vehicle.id)
                            markerIcons[vehicle.id]?.second else markerIcons[vehicle.id]?.first
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { tapped, _ ->
                            view.overlays.filterIsInstance<Marker>().forEach { previous ->
                                val original = cars.firstOrNull { it.name == previous.title }
                                if (original != null) {
                                    previous.icon = if (previous === tapped)
                                        markerIcons[original.id]?.second else markerIcons[original.id]?.first
                                }
                            }
                            view.invalidate()
                            currentOnSelect(vehicle)
                            true
                        }
                    }
                    view.overlays.add(marker)
                }
            }
            view.tag = markerSignature
            view.invalidate()
            }
        })
        SmallFloatingActionButton(
            onClick = {
                if (!permissionGranted) {
                    requestPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    centerOnGps()
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
            containerColor = Color.White, contentColor = Forest,
        ) { Icon(Icons.Outlined.MyLocation, "Моё местоположение") }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { map.controller.zoomIn() },
                containerColor = Color.White, contentColor = Forest,
                modifier = Modifier.size(42.dp)
            ) { Icon(Icons.Outlined.Add, "Приблизить") }
            SmallFloatingActionButton(
                onClick = { map.controller.zoomOut() },
                containerColor = Color.White, contentColor = Forest,
                modifier = Modifier.size(42.dp)
            ) { Icon(Icons.Outlined.Remove, "Отдалить") }
        }
        locationMessage?.let { msg ->
            Surface(Modifier.align(Alignment.BottomCenter).padding(8.dp), shape = RoundedCornerShape(12.dp), color = Color.White) {
                Text(msg, modifier = Modifier.padding(10.dp), fontSize = 11.sp, color = DeepInk)
            }
        }
    }
}
