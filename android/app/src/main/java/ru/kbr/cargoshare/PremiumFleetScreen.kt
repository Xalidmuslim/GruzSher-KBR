package ru.kbr.cargoshare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun fleetClass(v: Vehicle) =
    if (v.type.contains("тент", true)) "Тентованные" else "Цельнометаллические"

@Composable
fun PremiumFleetScreen(
    cars: List<Vehicle>, loading: Boolean, onSelect: (Vehicle) -> Unit,
    onRefresh: () -> Unit, onLiveDemo: () -> Unit, active: Boolean = false
) {
    var filter by rememberSaveable { mutableStateOf("Все") }
    var showEmbeddedMap by rememberSaveable { mutableStateOf(true) }
    var fullMap by rememberSaveable { mutableStateOf(false) }
    // A back gesture from the list restores its preceding embedded-map mode.
    var restoreEmbeddedMapOnBack by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    // Open an address sheet only when a customer explicitly taps a truck pin.
    var addressCarId by rememberSaveable { mutableStateOf<String?>(null) }
    var roadCarId by rememberSaveable { mutableStateOf<String?>(null) }
    var roadRoute by remember { mutableStateOf<RoadRoute?>(null) }
    var roadTap by remember { mutableIntStateOf(0) }
    var roadRequested by remember { mutableStateOf(false) }
    val shown = remember(cars, filter) { cars.filter { filter == "Все" || fleetClass(it) == filter } }
    val carousel = rememberLazyListState()
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val androidContext = LocalContext.current
    val selectedMapCar = shown.firstOrNull { it.id == roadCarId }
    var selectedStreetAddress by remember(roadCarId) { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedMapCar?.id, selectedMapCar?.latitude, selectedMapCar?.longitude) {
        selectedStreetAddress = null
        selectedMapCar?.let { car ->
            selectedStreetAddress = findPickupAddress(androidContext, car).value
        }
    }
    LaunchedEffect(carousel.firstVisibleItemIndex, shown) {
        shown.getOrNull(carousel.firstVisibleItemIndex)?.let { selectedId = it.id }
    }
    BackHandler(enabled = fullMap || (!showEmbeddedMap && addressCarId == null)) {
        if (fullMap) {
            fullMap = false
        } else {
            showEmbeddedMap = true
            restoreEmbeddedMapOnBack = true
        }
    }
    LaunchedEffect(showEmbeddedMap, restoreEmbeddedMapOnBack) {
        if (showEmbeddedMap && restoreEmbeddedMapOnBack) {
            // Header, optional live trip, banner, search, filters, optional
            // loading indicator, then the map. Scroll into the restored map
            // instead of leaving the user looking at carousel content.
            val mapItemIndex = 4 + (if (active) 1 else 0) +
                (if (loading) 1 else 0)
            scroll.scrollToItem(mapItemIndex)
            restoreEmbeddedMapOnBack = false
        }
    }

    if (fullMap) {
        Column(Modifier.fillMaxSize().background(PremiumTheme.Paper)) {
            Row(Modifier.fillMaxWidth().background(PremiumTheme.Paper).zIndex(2f)
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { fullMap = false }) {
                    Icon(Icons.Outlined.ArrowBack, "Назад к автомобилям", tint = PremiumTheme.GreenDark)
                }
                Column(Modifier.weight(1f)) {
                    Text("ГРУЗШЕР", fontSize = 23.sp, fontWeight = FontWeight.Bold,
                        color = PremiumTheme.GreenDark)
                    Text("Автомобили на карте · Нальчик", fontSize = 11.sp,
                        color = PremiumTheme.Muted)
                }
                DemoPill()
            }
            Box(Modifier.fillMaxWidth().background(PremiumTheme.Paper).zIndex(2f)) {
                FleetFilters(filter) { filter = it; selectedId = ""; roadCarId = null; roadRoute = null; roadRequested = false }
            }
            // Map owns the remaining viewport. Controls and selection info sit
            // BELOW it, not in a floating panel covering markers or zoom buttons.
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().background(PremiumTheme.Mint)) {
                NalchikMap(shown, {
                    selectedId = it.id
                    roadCarId = it.id
                    roadRoute = null
                    roadRequested = false
                    addressCarId = it.id
                }, Modifier.fillMaxSize().clipToBounds(), selectedId, roadRoute)
                Surface(Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = .94f)) {
                    Text("Грузовики · ${shown.size}", fontSize = 11.sp,
                        color = PremiumTheme.GreenDark,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            selectedMapCar?.let { car ->
                Surface(
                    onClick = { onSelect(car) },
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Column(Modifier.fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TruckArtwork(car, Modifier.size(98.dp, 77.dp))
                            Column(Modifier.weight(1f)) {
                                Text(car.name, fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp, color = PremiumTheme.Text,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${car.hourlyPriceRub} ₽/ч", fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold, color = PremiumTheme.GreenDark)
                                Text(selectedStreetAddress ?: "Определяем адрес…",
                                    fontSize = 11.sp, color = PremiumTheme.Muted,
                                    maxLines = 2, lineHeight = 14.sp,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { onSelect(car) },
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("Открыть", fontSize = 11.sp)
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                                    modifier = Modifier.size(17.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = {
                                roadCarId = car.id
                                roadRoute = null
                                roadRequested = true
                                roadTap += 1
                            }, modifier = Modifier.height(35.dp),
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, PremiumTheme.Green),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = PremiumTheme.GreenDark)) {
                                Icon(Icons.Outlined.AltRoute, contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (roadRoute != null) "Перестроить маршрут"
                                    else "Построить маршрут",
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (roadRequested && roadCarId == car.id) {
                            key(car.id, roadTap) {
                                RoadDirectionsPanel(car) { roadRoute = it }
                            }
                        }
                    }
                }
            }
        }
        addressCarId?.let { id ->
            shown.firstOrNull { it.id == id }?.let { car ->
                MapVehicleDetailsSheet(vehicle = car, onDismiss = { addressCarId = null },
                onDetails = { addressCarId = null; onSelect(car) },
                    onBuildRoute = {
                        addressCarId = null
                        selectedId = car.id
                        roadCarId = car.id
                        roadRoute = null
                        roadRequested = true
                        roadTap += 1
                        fullMap = true
                    })
            }
        }
        return
    }

    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().background(PremiumTheme.Paper),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "header") { PremiumBrandHeader() }
        if (active) item(key = "active") {
            Button(onClick = onLiveDemo, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.PlayArrow, null)
                Spacer(Modifier.width(5.dp))
                Text("Вернуться к поездке")
            }
        }
        item(key = "banner") { PremiumDemoBanner(cars.firstOrNull(), onClick = onLiveDemo) }
        item(key = "search") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Найти грузовик", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = PremiumTheme.Text, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Refresh, "Обновить автопарк")
                }
                TextButton(onClick = { fullMap = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = PremiumTheme.GreenDark)) {
                    Icon(Icons.Outlined.Map, null, Modifier.size(17.dp))
                    Text(" На карте", fontSize = 12.sp)
                }
            }
        }
        item(key = "filters") { FleetFilters(filter) { filter = it; selectedId = ""; scope.launch { carousel.scrollToItem(0) } } }
        if (loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (showEmbeddedMap) item(key = "map") {
            Box(Modifier.fillMaxWidth().height(258.dp).clip(RoundedCornerShape(20.dp))
                .background(PremiumTheme.Mint)) {
                NalchikMap(shown, { car ->
                    selectedId = car.id
                    addressCarId = car.id
                    scope.launch { carousel.animateScrollToItem(shown.indexOf(car).coerceAtLeast(0)) }
                }, Modifier.fillMaxSize(), selectedVehicleId = selectedId)
                Surface(Modifier.align(Alignment.TopStart).padding(10.dp),
                    shape = RoundedCornerShape(22.dp), color = Color.White) {
                    Text("🚚  Грузовики · ${shown.size}", fontSize = 11.sp,
                        color = PremiumTheme.GreenDark,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
                }
                Surface(Modifier.align(Alignment.BottomStart).padding(9.dp),
                    shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .95f)) {
                    Text("ⓘ Учебные точки — не GPS автомобилей", fontSize = 9.sp,
                        color = PremiumTheme.GreenDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }
        item(key = "available") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Доступны для аренды", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = PremiumTheme.Text, modifier = Modifier.weight(1f))
                TextButton(onClick = { showEmbeddedMap = !showEmbeddedMap }) {
                    Text(if (showEmbeddedMap) "Списком ›" else "С картой ›",
                        fontSize = 11.sp, color = PremiumTheme.Green)
                }
            }
        }
        if (shown.isEmpty()) item(key = "empty") {
            Text("В этой категории нет автомобилей.", modifier = Modifier.padding(20.dp))
        } else if (showEmbeddedMap) {
            item(key = "carousel") {
                LazyRow(state = carousel, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(shown, key = { it.id }) { car ->
                        PremiumFleetCard(car, Modifier.width(178.dp), selectedId == car.id) {
                            onSelect(car)
                        }
                    }
                }
            }
        } else {
            items(shown, key = { "list_${it.id}" }) { car ->
                PremiumFleetListRow(car) { onSelect(car) }
            }
        }
    }
    addressCarId?.let { id ->
        shown.firstOrNull { it.id == id }?.let { car ->
            MapVehicleDetailsSheet(vehicle = car, onDismiss = { addressCarId = null },
                onDetails = { addressCarId = null; onSelect(car) },
                onBuildRoute = {
                    addressCarId = null
                    selectedId = car.id
                    roadCarId = car.id
                    roadRoute = null
                    roadRequested = true
                    roadTap += 1
                    fullMap = true
                })
        }
    }
}

@Composable
private fun FleetFilters(selected: String, onChange: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(listOf("Все", "Тентованные", "Цельнометаллические")) { name ->
            FilterChip(selected = selected == name, onClick = { onChange(name) },
                label = { Text(name, fontSize = 11.sp) },
                leadingIcon = { Icon(
                    if (name == "Все") Icons.Outlined.LocalShipping else Icons.Outlined.Inventory2,
                    null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(22.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PremiumTheme.Green, selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White, containerColor = Color.White))
        }
    }
}

@Composable
private fun PremiumFleetCard(car: Vehicle, modifier: Modifier, selected: Boolean,
                             onClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("gruzsher_preferences", 0) }
    var favourite by remember(car.id) {
        mutableStateOf(prefs.getBoolean("favourite_${car.id}", false))
    }
    // Wrap the actual content. Previously heightIn(min=247.dp) combined with
    // fillMaxSize + weight(1f) spacer created a large empty middle section,
    // especially when titles fit on one line.
    Card(onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border = if (selected) BorderStroke(2.dp, PremiumTheme.Green) else null) {
        Column(Modifier.fillMaxWidth().padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(101.dp)) {
                TruckArtwork(car, Modifier.fillMaxSize())
                Surface(Modifier.align(Alignment.TopStart).padding(4.dp),
                    shape = RoundedCornerShape(14.dp), color = PremiumTheme.Mint) {
                    Text("● Доступен", fontSize = 9.sp, color = PremiumTheme.Green,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
                IconButton(onClick = {
                    favourite = !favourite
                    prefs.edit().putBoolean("favourite_${car.id}", favourite).apply()
                }, modifier = Modifier.align(Alignment.TopEnd).size(35.dp)
                    .background(Color.White, RoundedCornerShape(50))) {
                    Icon(if (favourite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        "В избранное", tint = PremiumTheme.Green, modifier = Modifier.size(19.dp))
                }
            }
            Text(car.name, color = PremiumTheme.Text, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, lineHeight = 17.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Text(car.type, color = PremiumTheme.Muted, fontSize = 9.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${car.payloadKg} кг  ◇  ${car.cargoVolumeM3} м³",
                color = PremiumTheme.Muted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${car.hourlyPriceRub} ₽/ч", fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold, color = PremiumTheme.GreenDark,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(12.dp), color = PremiumTheme.Green) {
                    Text("Выбрать", fontSize = 10.sp, color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PremiumFleetListRow(car: Vehicle, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TruckArtwork(car, Modifier.size(118.dp, 104.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(car.name, color = PremiumTheme.Text, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, maxLines = 2)
                Text(car.type, color = PremiumTheme.Muted, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${car.payloadKg} кг · ${car.cargoVolumeM3} м³", fontSize = 11.sp,
                    color = PremiumTheme.Muted)
                Text("${car.hourlyPriceRub} ₽/ч", fontSize = 17.sp,
                    color = PremiumTheme.GreenDark, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Outlined.ChevronRight, "Открыть", tint = PremiumTheme.Green)
        }
    }
}
