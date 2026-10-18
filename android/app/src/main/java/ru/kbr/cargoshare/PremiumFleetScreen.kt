package ru.kbr.cargoshare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun fleetClass(v: Vehicle): String = when {
    v.type.contains("реф", true) -> "Рефрижераторы"
    v.type.contains("борт", true) -> "Бортовые"
    v.type.contains("цельнометалл", true) -> "Цельнометаллические"
    v.type.contains("тент", true) -> "Тентованные"
    else -> "Фургоны"
}

/** Map-first navigation with a pinned vehicle carousel. Different layout from the legacy scrolling catalog. */
@Composable
fun PremiumFleetScreen(
    cars: List<Vehicle>, loading: Boolean, onSelect: (Vehicle) -> Unit,
    onRefresh: () -> Unit, onLiveDemo: () -> Unit,
) {
    var mapMode by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("Все") }
    var filtersOpen by remember { mutableStateOf(false) }
    val carousel = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val categories = remember(cars) { cars.map(::fleetClass).distinct() }
    LaunchedEffect(categories) { if (filter != "Все" && filter !in categories) filter = "Все" }
    val shown = remember(cars, filter) { cars.filter { filter == "Все" || fleetClass(it) == filter } }
    val located = remember(shown) { shown.filter { it.latitude != null && it.longitude != null } }
    LaunchedEffect(filter) { carousel.scrollToItem(0) }
    Column(
        Modifier.fillMaxSize().background(PremiumTheme.Paper),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 3.dp)) {
            PremiumBrandHeader()
            Spacer(Modifier.height(7.dp))
            // Compact CTA: the map and actual vehicles now dominate the viewport.
            Surface(onClick = onLiveDemo,
                shape = RoundedCornerShape(17.dp), color = PremiumTheme.Green) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = Color.White.copy(alpha = .16f),
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.PlayArrow, null, tint = Color.White,
                            modifier = Modifier.padding(7.dp).size(19.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("LIVE Демо аренды", fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Все этапы аренды от выбора до чека",
                            fontSize = 10.sp, color = Color.White.copy(alpha = .82f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Outlined.ArrowForwardIos, null, tint = Color.White,
                        modifier = Modifier.size(17.dp))
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Найти грузовик", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                        color = PremiumTheme.Text)
                    Text(if (filter == "Все") "${shown.size} автомобиля · Нальчик" else "${shown.size} · $filter",
                        fontSize = 11.sp, color = PremiumTheme.Muted)
                }
                if (categories.size > 1) {
                    Box {
                        IconButton(onClick = { filtersOpen = true }) {
                            Icon(Icons.Outlined.Tune, "Фильтровать автомобили",
                                tint = PremiumTheme.Green)
                        }
                        DropdownMenu(expanded = filtersOpen, onDismissRequest = { filtersOpen = false }) {
                            (listOf("Все") + categories).forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name, color = PremiumTheme.Text, fontSize = 13.sp) },
                                    onClick = { filter = name; filtersOpen = false },
                                    leadingIcon = {
                                        if (filter == name) Icon(Icons.Outlined.Check, null,
                                            tint = PremiumTheme.Green)
                                    }
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    Icon(Icons.Outlined.Refresh, "Обновить автопарк", tint = PremiumTheme.Green)
                }
            }

        }
        // Actual segmented control, not a title with a link.
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FleetTab("На карте", Icons.Outlined.Map, mapMode, Modifier.weight(1f)) { mapMode = true }
            FleetTab("Список", Icons.Outlined.FormatListBulleted, !mapMode, Modifier.weight(1f)) { mapMode = false }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PremiumTheme.Green)
            }
        } else if (shown.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(25.dp),
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.LocalShipping, null,
                    modifier = Modifier.size(45.dp), tint = PremiumTheme.Green)
                Spacer(Modifier.height(12.dp))
                Text("В этой категории пока нет грузовиков",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PremiumTheme.Text)
                TextButton(onClick = { filter = "Все" }) { Text("Показать все") }
            }
        } else if (mapMode && located.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(23.dp)).background(PremiumTheme.Mint)) {
                NalchikMap(located, onSelect, Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    color = Color.White, shadowElevation = 4.dp,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocalShipping, null, modifier = Modifier.size(17.dp),
                            tint = PremiumTheme.Green)
                        Spacer(Modifier.width(6.dp))
                        Text("Грузовики · ${located.size}", fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = PremiumTheme.GreenDark)
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    color = Color.White.copy(alpha = .94f),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text("● Учебные точки — не GPS автомобилей",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = PremiumTheme.Muted, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Доступны для аренды", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = PremiumTheme.Text, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { scope.launch { carousel.animateScrollToItem(
                        (carousel.firstVisibleItemIndex - 1).coerceAtLeast(0)) } },
                    modifier = Modifier.size(30.dp),
                    enabled = carousel.firstVisibleItemIndex > 0
                ) { Icon(Icons.Outlined.ChevronLeft, "Предыдущая машина",
                    tint = PremiumTheme.Green, modifier = Modifier.size(23.dp)) }
                Text("${(carousel.firstVisibleItemIndex + 1).coerceAtMost(shown.size)}/${shown.size}",
                    color = PremiumTheme.Muted, fontSize = 11.sp)
                IconButton(
                    onClick = { scope.launch { carousel.animateScrollToItem(
                        (carousel.firstVisibleItemIndex + 1).coerceAtMost(shown.lastIndex)) } },
                    modifier = Modifier.size(30.dp),
                    enabled = carousel.firstVisibleItemIndex < shown.lastIndex
                ) { Icon(Icons.Outlined.ChevronRight, "Следующая машина",
                    tint = PremiumTheme.Green, modifier = Modifier.size(23.dp)) }
            }
            Spacer(Modifier.height(7.dp))
            LazyRow(
                state = carousel,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(shown, key = { it.id }) { car ->
                    CarouselTruckCard(car) { onSelect(car) }
                }
            }
            Spacer(Modifier.height(8.dp))
        } else {
            LazyColumn(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(11.dp),
                contentPadding = PaddingValues(bottom = 23.dp)) {
                items(shown, key = { it.id }) { car ->
                    PremiumTruckListCard(car) { onSelect(car) }
                }
            }
        }
    }
}

@Composable
private fun FleetTab(title: String,
    symbol: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = if (selected) PremiumTheme.GreenDark else Color.White,
        shadowElevation = if (selected) 2.dp else 0.dp) {
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(symbol, null, modifier = Modifier.size(18.dp),
                tint = if (selected) Color.White else PremiumTheme.Muted)
            Spacer(Modifier.width(6.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else PremiumTheme.Muted)
        }
    }
}

@Composable
private fun CarouselTruckCard(vehicle: Vehicle, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(290.dp),
        shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            TruckArtwork(vehicle, Modifier.size(width = 112.dp, height = 111.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("● ДОСТУПЕН · ДЕМО", fontSize = 9.sp, color = PremiumTheme.Green,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Text(vehicle.name, fontSize = 16.sp, lineHeight = 18.sp, maxLines = 2,
                    fontWeight = FontWeight.ExtraBold, color = PremiumTheme.Text)
                Text(vehicle.type, fontSize = 10.sp, maxLines = 1,
                    color = PremiumTheme.Muted, overflow = TextOverflow.Ellipsis)
                Text("${vehicle.hourlyPriceRub} ₽/ч", fontSize = 19.sp,
                    color = PremiumTheme.Green, fontWeight = FontWeight.ExtraBold)
                Surface(color = PremiumTheme.Green, shape = RoundedCornerShape(11.dp)) {
                    Text("Выбрать  →", color = Color.White, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
            }
        }
    }
}

@Composable
private fun PremiumTruckListCard(vehicle: Vehicle, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp),
        shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TruckArtwork(vehicle, Modifier.size(143.dp, 145.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("● ДОСТУПЕН · ДЕМО", color = PremiumTheme.Green, fontSize = 10.sp)
                    Text(vehicle.name, fontSize = 19.sp, lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold, color = PremiumTheme.Text)
                    Text(vehicle.type, color = PremiumTheme.Muted, fontSize = 11.sp)
                    Text("от ${vehicle.hourlyPriceRub} ₽ / час",
                        color = PremiumTheme.Green, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }
            HorizontalDivider(color = PremiumTheme.Stroke)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2, null, Modifier.size(16.dp), tint = PremiumTheme.Muted)
                Text(" ${vehicle.payloadKg} кг   ", fontSize = 12.sp, color = PremiumTheme.Text)
                Icon(Icons.Outlined.ViewInAr, null, Modifier.size(16.dp), tint = PremiumTheme.Muted)
                Text(" ${vehicle.cargoVolumeM3} м³", fontSize = 12.sp, color = PremiumTheme.Text)
                Spacer(Modifier.weight(1f))
                Text("Выбрать", fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, color = PremiumTheme.Green)
                Icon(Icons.Outlined.ChevronRight, null, tint = PremiumTheme.Green)
            }
        }
    }
}
