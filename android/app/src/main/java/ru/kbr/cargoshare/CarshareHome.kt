package ru.kbr.cargoshare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Pine = Color(0xFF154E40)
private val Night = Color(0xFF1C302C)
private val Beige = Color(0xFFF5F6F1)
private val Cloud = Color(0xFFEAF0EB)
private val MutedHome = Color(0xFF78867E)

private fun homeFilter(type: String, choice: String): Boolean = when (choice) {
    "Фургоны" -> type.contains("фургон", ignoreCase = true)
    "Бортовые" -> type.contains("борт", ignoreCase = true)
    "Рефрижераторы" -> type.contains("реф", ignoreCase = true)
    else -> true
}

@Composable
fun CarshareHome(
    cars: List<Vehicle>,
    loading: Boolean,
    onSelect: (Vehicle) -> Unit,
    onRefresh: () -> Unit,
    onLiveDemo: () -> Unit,
) {
    var choice by remember { mutableStateOf("Все") }
    var showMap by remember { mutableStateOf(false) }
    val availableTypes = remember(cars) {
        listOf("Фургоны", "Бортовые", "Рефрижераторы").filter { category ->
            cars.any { homeFilter(it.type, category) }
        }
    }
    LaunchedEffect(availableTypes, choice) {
        if (choice != "Все" && choice !in availableTypes) choice = "Все"
    }
    val results = remember(cars, choice) { cars.filter { homeFilter(it.type, choice) } }
    val located = remember(results) { results.filter { it.latitude != null && it.longitude != null } }
    // One scroll container, no nested LazyColumn — all vehicles remain reachable.
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Beige),
        contentPadding = PaddingValues(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") {
            Column(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ГРУЗШЕР", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold,
                            color = Night, letterSpacing = 0.5.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocationOn, null, Modifier.size(15.dp), tint = Pine)
                            Text(" Нальчик · Кабардино-Балкария", color = MutedHome, fontSize = 12.sp)
                        }
                    }
                    Surface(color = Cloud, shape = RoundedCornerShape(24.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.VerifiedUser, null, tint = Pine, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Без залога", color = Pine, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Text("Аренда грузовиков в Нальчике", fontSize = 21.sp,
                    lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, color = Night)
                Card(
                    onClick = onLiveDemo, shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Pine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(15.dp), color = Color.White.copy(alpha = .14f)) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White,
                                modifier = Modifier.padding(8.dp).size(22.dp))
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text("LIVE Демо аренды", color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Полный маршрут, документы, поездка, возврат",
                                color = Color.White.copy(alpha = .78f), fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Color.White)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Автопарк", color = Night, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${results.size} автомобиля · учебные данные", color = MutedHome, fontSize = 11.sp)
                    }
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Outlined.Refresh, "Обновить автопарк", tint = Pine)
                    }
                }
            }
        }

        if (availableTypes.size > 1) item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(listOf("Все") + availableTypes) { label ->
                    FilterChip(selected = choice == label, onClick = { choice = label },
                        label = { Text(label, fontSize = 12.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pine,
                            selectedLabelColor = Color.White, containerColor = Color.White,
                            labelColor = MutedHome),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = choice == label))
                }
            }
        }

        item(key = "map-toggle") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (showMap) "Карта автомобилей" else "Свободные грузовики",
                    color = Night, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showMap = !showMap }) {
                    Icon(if (showMap) Icons.Outlined.FormatListBulleted else Icons.Outlined.Map,
                        null, tint = Pine, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showMap) "Список" else "Карта", color = Pine, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (showMap) {
            item(key = "map") {
                Column(Modifier.padding(horizontal = 15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(245.dp)
                        .clip(RoundedCornerShape(22.dp)).background(Cloud)) {
                        NalchikMap(located, onSelect, Modifier.fillMaxSize())
                    }
                    Text("Маркеры показывают демонстрационные, а не GPS-координаты грузовиков.",
                        fontSize = 10.sp, color = MutedHome, modifier = Modifier.padding(horizontal = 3.dp))
                }
            }
        }
        if (loading) {
            item(key = "loading") { Box(Modifier.fillMaxWidth().padding(25.dp), Alignment.Center) {
                CircularProgressIndicator(color = Pine)
            } }
        } else if (results.isEmpty()) {
            item(key = "empty") {
                Surface(
                    color = Color.White, shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                ) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("В этой категории пока нет машин", color = Night, fontWeight = FontWeight.Bold)
                        Text("Выберите другую категорию или обновите список.", fontSize = 12.sp, color = MutedHome)
                        TextButton(onClick = { choice = "Все" }) { Text("Показать все", color = Pine) }
                    }
                }
            }
        } else {
            items(results, key = { "vehicle-${it.id}" }) { vehicle ->
                HomeVehicleCard(vehicle, onClick = { onSelect(vehicle) })
            }
        }
        item(key = "footer") {
            Text("Учебный каталог · реальные SMS, документы, замки и платежи не подключены",
                color = MutedHome, fontSize = 10.sp, lineHeight = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun HomeVehicleCard(vehicle: Vehicle, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(138.dp, 126.dp).clip(RoundedCornerShape(17.dp))
                    .background(Cloud), Alignment.Center) {
                    val demoImg = when (vehicle.id) {
                        "gazelle-next" -> "file:///android_asset/truck_g01.webp"
                        "ford-transit" -> "file:///android_asset/truck_g02.webp"
                        "largus-van" -> null // Previous truck image was not a LADA Largus
                        else -> null
                    }
                    if (demoImg != null || vehicle.imageUrl.startsWith("https://")) {
                        AsyncImage(model = vehicle.imageUrl.takeIf { it.startsWith("https://") } ?: demoImg,
                            contentDescription = "Демонстрационное изображение ${vehicle.name}",
                            modifier = Modifier.fillMaxSize().padding(5.dp),
                            contentScale = ContentScale.Fit)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Outlined.DirectionsCar, null, Modifier.size(54.dp), tint = Pine)
                            Text("Фото уточняется", color = MutedHome, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(color = Cloud, shape = RoundedCornerShape(12.dp)) {
                        Text("Доступен · Демо", color = Pine, fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                    }
                    Text(vehicle.name, color = Night, fontWeight = FontWeight.Bold,
                        fontSize = 17.sp, maxLines = 2, lineHeight = 20.sp)
                    Text(vehicle.type, color = MutedHome, fontSize = 11.sp,
                        maxLines = 2, lineHeight = 14.sp, overflow = TextOverflow.Ellipsis)
                    Text("от ${vehicle.hourlyPriceRub} ₽/ч",
                        fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Pine)
                }
            }
            HorizontalDivider(color = Cloud)
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2, null, Modifier.size(16.dp), tint = MutedHome)
                Text("${vehicle.payloadKg} кг", fontSize = 12.sp, color = Night)
                Icon(Icons.Outlined.ViewInAr, null, Modifier.size(16.dp), tint = MutedHome)
                Text("${vehicle.cargoVolumeM3} м³", fontSize = 12.sp, color = Night)
                Spacer(Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, Modifier.size(15.dp), tint = MutedHome)
                Spacer(Modifier.width(3.dp))
                Text(vehicle.pickupLocation, color = MutedHome,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp)
                Spacer(Modifier.width(5.dp))
                Text("Выбрать", color = Pine, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Icon(Icons.Outlined.ChevronRight, null, tint = Pine, modifier = Modifier.size(19.dp))
            }
        }
    }
}
