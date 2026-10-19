package ru.kbr.cargoshare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full vehicle information on BOTH main and expanded fleet map pin taps.
 * Preserve the same catalogue values as the dedicated vehicle page; never
 * claim real fleet telemetry, availability or GPS location for demo records.
 * Scrollable content and navigation-bar insets keep all controls reachable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FleetMapVehicleSheet(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onOpenDetails: () -> Unit,
    onBuildRoute: () -> Unit
) {
    val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * .79f
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var address by remember(vehicle.id) { mutableStateOf<PickupAddressDetails?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(vehicle.id, vehicle.latitude, vehicle.longitude, vehicle.pickupLocation) {
        address = findPickupAddress(context, vehicle)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = PremiumTheme.Paper
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = maxContentHeight)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TruckArtwork(
                    vehicle,
                    Modifier.size(width = 133.dp, height = 104.dp)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(vehicle.name, color = PremiumTheme.Text,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 23.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(vehicle.type, color = PremiumTheme.Muted,
                        fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2)
                    Text("${vehicle.hourlyPriceRub} ₽/ч",
                        fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        color = PremiumTheme.GreenDark)
                    Text("Демо · учебные координаты",
                        fontSize = 10.sp, color = PremiumTheme.Muted,
                        maxLines = 2)
                }
            }

            // Actions sit near the top: no need to scroll through vehicle specs
            // or through a long address before building a road route.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBuildRoute,
                    modifier = Modifier.weight(1f).height(43.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = vehicle.latitude != null && vehicle.longitude != null,
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumTheme.Green)
                ) {
                    Icon(Icons.Outlined.AltRoute, contentDescription = null,
                        modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Построить маршрут", fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onOpenDetails,
                    modifier = Modifier.weight(.73f).height(43.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, PremiumTheme.Stroke)
                ) {
                    Text("Подробнее", fontSize = 11.sp, maxLines = 1,
                        color = PremiumTheme.GreenDark)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = PremiumTheme.GreenDark)
                }
            }

            Surface(shape = RoundedCornerShape(15.dp), color = androidx.compose.ui.graphics.Color.White) {
                Column(Modifier.fillMaxWidth().padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FleetVehicleFact("Грузоподъёмность", "${vehicle.payloadKg} кг",
                            Modifier.weight(1f))
                        FleetVehicleFact("Объём кузова", "${vehicle.cargoVolumeM3} м³",
                            Modifier.weight(1f))
                    }
                    HorizontalDivider(color = PremiumTheme.Paper)
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FleetVehicleFact("Коробка передач", vehicle.transmission,
                            Modifier.weight(1f))
                        FleetVehicleFact("Сутки аренды", "${vehicle.dailyPriceRub} ₽",
                            Modifier.weight(1f))
                    }
                }
            }

            Surface(shape = RoundedCornerShape(15.dp), color = androidx.compose.ui.graphics.Color.White) {
                Column(Modifier.fillMaxWidth().padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Местоположение автомобиля",
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        color = PremiumTheme.GreenDark)
                    Text(address?.value ?: "Определяем ближайший адрес…",
                        fontSize = 12.sp, lineHeight = 16.sp,
                        color = PremiumTheme.Text)
                    Text(pickupCoordinates(vehicle.latitude, vehicle.longitude),
                        fontSize = 10.sp, color = PremiumTheme.Muted)
                    Text(if (isTrainingPickup(vehicle.pickupLocation))
                        "Учебная точка, а не подтверждённая стоянка"
                        else "Место выдачи из каталога — уточните перед поездкой",
                        fontSize = 10.sp, lineHeight = 13.sp,
                        color = PremiumTheme.Muted)
                }
            }
            if (vehicle.description.isNotBlank()) {
                Text("Описание", fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = PremiumTheme.GreenDark)
                Text(vehicle.description, fontSize = 12.sp, lineHeight = 17.sp,
                    color = PremiumTheme.Text)
            }
        }
    }
}

@Composable
private fun FleetVehicleFact(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = PremiumTheme.Muted, fontSize = 10.sp,
            lineHeight = 13.sp)
        Text(value, color = PremiumTheme.Text, fontSize = 13.sp,
            lineHeight = 17.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
