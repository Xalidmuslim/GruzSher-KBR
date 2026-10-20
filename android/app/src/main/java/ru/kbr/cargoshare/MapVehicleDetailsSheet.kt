package ru.kbr.cargoshare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full vehicle preview when the customer taps a truck marker on either fleet
 * map. This is a real scrollable vehicle-information sheet, not an address-only
 * placeholder. Car coordinates remain training/demo positions.
 *
 * Height is bounded by the visible device screen; nothing can spill into the
 * gesture navigation region. The route action is placed near the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapVehicleDetailsSheet(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onBuildRoute: () -> Unit
) {
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PremiumTheme.Paper,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxSheetHeight)
                .premiumAppear(vehicle.id, riseDp = 5f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TruckArtwork(vehicle, Modifier.size(135.dp, 112.dp))
                Column(Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vehicle.name, color = PremiumTheme.Text,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(vehicle.type, color = PremiumTheme.Muted,
                        fontSize = 11.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text("● Доступен · ДЕМО", color = PremiumTheme.Green,
                        fontSize = 11.sp)
                    Text("${vehicle.hourlyPriceRub} ₽/ч",
                        color = PremiumTheme.GreenDark, fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold)
                    Text("${vehicle.dailyPriceRub} ₽/сутки",
                        color = PremiumTheme.Muted, fontSize = 10.sp)
                }
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onBuildRoute,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumTheme.Green)
                ) {
                    Icon(Icons.Outlined.AltRoute, contentDescription = null,
                        modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Построить маршрут", fontSize = 11.sp,
                        maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDetails,
                    modifier = Modifier.weight(0.76f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, PremiumTheme.Green)
                ) {
                    Text("Подробнее", color = PremiumTheme.GreenDark,
                        fontSize = 11.sp, maxLines = 1)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                        tint = PremiumTheme.GreenDark, modifier = Modifier.size(15.dp))
                }
            }
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MapVehicleStat("${vehicle.payloadKg} кг", "Грузоподъёмность",
                        Modifier.weight(1f))
                    MapVehicleStat("${vehicle.cargoVolumeM3} м³", "Объём",
                        Modifier.weight(1f))
                    MapVehicleStat(vehicle.transmission, "КПП",
                        Modifier.weight(1f))
                }
            }
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Где находится машина",
                        color = PremiumTheme.GreenDark,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(vehicle.pickupLocation.ifBlank { "Адрес уточняется" },
                        color = PremiumTheme.Text, fontSize = 12.sp,
                        maxLines = 3, lineHeight = 16.sp)
                    Text(pickupCoordinates(vehicle.latitude, vehicle.longitude),
                        color = PremiumTheme.Muted, fontSize = 11.sp)
                    Text("Метка автомобиля учебная, не данные GPS-трекера.",
                        color = PremiumTheme.Muted, fontSize = 10.sp,
                        lineHeight = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun MapVehicleStat(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontWeight = FontWeight.Bold, color = PremiumTheme.Text,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, color = PremiumTheme.Muted, fontSize = 9.sp,
            maxLines = 2, lineHeight = 11.sp)
    }
}
