package ru.kbr.cargoshare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Existing vehicle data drives this layout; illustrative mockup claims are NOT shown as facts. */
@Composable
fun PremiumVehicleIntro(vehicle: Vehicle) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vehicle.name, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                color = PremiumTheme.Text, modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(20.dp), color = PremiumTheme.Mint) {
                Text("● Доступен · Демо", color = PremiumTheme.Green, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
            }
        }
        Text(vehicle.type, fontSize = 14.sp, color = PremiumTheme.Muted)
        TruckArtwork(vehicle, Modifier.fillMaxWidth().height(190.dp))
        Surface(color = Color.White, shape = PremiumTheme.Shape) {
            Row(Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BookingSpec(Icons.Outlined.Inventory2, "${vehicle.payloadKg} кг",
                    "Грузоподъёмность", Modifier.weight(1f))
                BookingSpec(Icons.Outlined.ViewInAr, "${vehicle.cargoVolumeM3} м³",
                    "Объём кузова", Modifier.weight(1f))
                BookingSpec(Icons.Outlined.Settings, vehicle.transmission,
                    "Коробка", Modifier.weight(1f))
            }
        }
        Surface(color = Color.White, shape = PremiumTheme.Shape) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, tint = PremiumTheme.Green,
                    modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Место получения", fontWeight = FontWeight.Bold,
                        color = PremiumTheme.Text, fontSize = 13.sp)
                    Text(vehicle.pickupLocation, color = PremiumTheme.Muted, fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PremiumFeature("Без залога", Icons.Outlined.VerifiedUser, Modifier.weight(1f))
            PremiumFeature("Учебная аренда", Icons.Outlined.Info, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BookingSpec(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, subtitle: String, modifier: Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, modifier = Modifier.size(22.dp), tint = PremiumTheme.Green)
        Text(title, fontWeight = FontWeight.Bold,
            fontSize = 12.sp, color = PremiumTheme.Text, maxLines = 1)
        Text(subtitle, fontSize = 9.sp,
            lineHeight = 11.sp, color = PremiumTheme.Muted, maxLines = 2)
    }
}
