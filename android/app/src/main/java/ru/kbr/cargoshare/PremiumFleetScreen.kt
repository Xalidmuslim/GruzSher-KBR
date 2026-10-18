package ru.kbr.cargoshare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

private fun fleetCategory(vehicle: Vehicle): String {
    val category = vehicle.type.lowercase()
    return when {
        "реф" in category -> "Рефрижераторы"
        "борт" in category -> "Бортовые"
        "цельнометалл" in category -> "Цельнометаллические"
        "тент" in category -> "Тентованные"
        else -> "Другие"
    }
}

@Composable
fun PremiumFleetScreen(
    cars: List<Vehicle>,
    loading: Boolean,
    onSelect: (Vehicle) -> Unit,
    onRefresh: () -> Unit,
    onLiveDemo: () -> Unit,
) {
    var category by remember { mutableStateOf("Все") }
    var mapMode by remember { mutableStateOf(true) }
    val categories = remember(cars) {
        cars.map(::fleetCategory).distinct().sorted()
    }
    LaunchedEffect(categories) { if (category != "Все" && category !in categories) category = "Все" }
    val filtered = remember(cars, category) {
        cars.filter { category == "Все" || fleetCategory(it) == category }
    }
    val withCoordinates = remember(filtered) {
        filtered.filter { it.latitude != null && it.longitude != null }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PremiumTheme.Paper),
        contentPadding = PaddingValues(top = 12.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item(key = "brand") {
            Column(Modifier.padding(horizontal = 17.dp)) {
                PremiumBrandHeader()
                Spacer(Modifier.height(15.dp))
                PremiumDemoBanner(car = cars.firstOrNull(), onClick = onLiveDemo)
            }
        }
        item(key = "fleet-title") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Автопарк", fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold, color = PremiumTheme.Text)
                    Text("${cars.size} автомобиля · учебные данные",
                        fontSize = 11.sp, color = PremiumTheme.Muted)
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    Icon(Icons.Outlined.Refresh, "Обновить", tint = PremiumTheme.Green)
                }
            }
        }
        if (categories.size > 1) item(key = "categories") {
            LazyRow(contentPadding = PaddingValues(horizontal = 17.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(listOf("Все") + categories, key = { it }) { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item, fontSize = 11.sp, maxLines = 1) },
                        shape = RoundedCornerShape(17.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White, labelColor = PremiumTheme.Text,
                            selectedContainerColor = PremiumTheme.Green,
                            selectedLabelColor = Color.White)
                    )
                }
            }
        }
        item(key = "view-switch") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (mapMode) "Машины на карте" else "Свободные грузовики",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = PremiumTheme.Text, modifier = Modifier.weight(1f))
                Surface(color = Color.White, shape = RoundedCornerShape(23.dp),
                    shadowElevation = 1.dp) {
                    Row(Modifier.padding(4.dp)) {
                        FleetSwitch("Карта", mapMode, Icons.Outlined.Map) { mapMode = true }
                        FleetSwitch("Список", !mapMode, Icons.Outlined.FormatListBulleted) { mapMode = false }
                    }
                }
            }
        }
        if (mapMode && withCoordinates.isNotEmpty()) {
            item(key = "fleet-map") {
                Column(Modifier.padding(horizontal = 17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.fillMaxWidth().height(254.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(PremiumTheme.Mint)) {
                        NalchikMap(withCoordinates, onSelect, Modifier.fillMaxSize())
                    }
                    Text("Учебные позиции. Это не GPS автомобилей.",
                        fontSize = 10.sp, color = PremiumTheme.Muted,
                        modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (loading) item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                CircularProgressIndicator(color = PremiumTheme.Green)
            }
        }
        if (!loading && filtered.isEmpty()) item(key = "empty") {
            Surface(color = Color.White, shape = PremiumTheme.Shape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Нет автомобилей в выбранной категории",
                        color = PremiumTheme.Text, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { category = "Все" }) { Text("Показать все") }
                }
            }
        }
        items(filtered, key = { "car-${it.id}" }) { vehicle ->
            PremiumFleetVehicleCard(vehicle) { onSelect(vehicle) }
        }
        item(key = "legal") {
            Text("ДЕМО · реальные бронирования, документы, платежи и команды замкам не выполняются",
                color = PremiumTheme.Muted, fontSize = 10.sp, lineHeight = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun FleetSwitch(
    title: String, selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit
) {
    val bg = if (selected) PremiumTheme.Green else Color.White
    val fg = if (selected) Color.White else PremiumTheme.Muted
    Surface(onClick = onClick, color = bg, shape = RoundedCornerShape(19.dp)) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(title, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PremiumFleetVehicleCard(vehicle: Vehicle, onClick: () -> Unit) {
    Card(onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TruckArtwork(vehicle, Modifier.size(116.dp, 111.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(shape = RoundedCornerShape(30.dp), color = PremiumTheme.Mint) {
                        Text("● Доступен · Демо", fontSize = 10.sp, color = PremiumTheme.Green,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Text(vehicle.name, color = PremiumTheme.Text,
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 2, lineHeight = 20.sp)
                    Text(vehicle.type, fontSize = 11.sp, maxLines = 2,
                        color = PremiumTheme.Muted, overflow = TextOverflow.Ellipsis)
                    Text("от ${vehicle.hourlyPriceRub} ₽/ч",
                        fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = PremiumTheme.Green)
                }
            }
            HorizontalDivider(color = PremiumTheme.Stroke)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Icon(Icons.Outlined.Inventory2, null, Modifier.size(16.dp), tint = PremiumTheme.Muted)
                Text("${vehicle.payloadKg} кг", fontSize = 12.sp, color = PremiumTheme.Text)
                Icon(Icons.Outlined.ViewInAr, null, Modifier.size(16.dp), tint = PremiumTheme.Muted)
                Text("${vehicle.cargoVolumeM3} м³", fontSize = 12.sp, color = PremiumTheme.Text)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, Modifier.size(16.dp), tint = PremiumTheme.Green)
                Spacer(Modifier.width(4.dp))
                Text(vehicle.pickupLocation, fontSize = 11.sp, color = PremiumTheme.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Surface(color = PremiumTheme.Green, shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Выбрать", color = Color.White, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                        Icon(Icons.Outlined.ArrowForward, null,
                            Modifier.size(15.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}
