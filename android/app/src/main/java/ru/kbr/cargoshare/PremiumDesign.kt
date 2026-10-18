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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** A composed design system: visual mockups are references, never cropped backgrounds. */
object PremiumTheme {
    val Green = Color(0xFF07523E)
    val GreenDark = Color(0xFF063D30)
    val Text = Color(0xFF102822)
    val Paper = Color(0xFFFBFAF6)
    val Mint = Color(0xFFE6F5ED)
    val Muted = Color(0xFF6F7E83)
    val Stroke = Color(0xFFE9EBE7)
    val Lavender = Color(0xFFEDE8FA)
    val Shape = RoundedCornerShape(22.dp)
}

/** Use only matching media. The Largus has no verified photograph in the repository. */
fun localTruckArtwork(vehicle: Vehicle): String? = when (vehicle.id) {
    "gazelle-next" -> "file:///android_asset/truck_g01.webp"
    "ford-transit" -> "file:///android_asset/truck_g02.webp"
    else -> vehicle.imageUrl.takeIf { it.startsWith("https://") }
}

@Composable
fun TruckArtwork(vehicle: Vehicle, modifier: Modifier = Modifier) {
    val source = localTruckArtwork(vehicle)
    Box(modifier.clip(RoundedCornerShape(17.dp)).background(PremiumTheme.Mint),
        contentAlignment = Alignment.Center) {
        if (source != null) {
            AsyncImage(
                model = source,
                contentDescription = "Изображение: ${vehicle.name}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.LocalShipping, null, tint = PremiumTheme.Green, modifier = Modifier.size(47.dp))
                Text("Фото уточняется", fontSize = 10.sp, color = PremiumTheme.Muted)
            }
        }
    }
}

@Composable
fun PremiumBrandHeader(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ГРУЗШЕР", fontSize = 30.sp, fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp, color = PremiumTheme.GreenDark)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = PremiumTheme.Green, modifier = Modifier.size(16.dp))
                Text("Нальчик · Кабардино-Балкария",
                    fontSize = 11.sp, maxLines = 1, color = PremiumTheme.Muted)
            }
        }
        Surface(shape = RoundedCornerShape(50.dp), color = PremiumTheme.Mint) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = PremiumTheme.Green,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Без залога", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = PremiumTheme.GreenDark)
            }
        }
    }
}

@Composable
fun PremiumDemoBanner(
    car: Vehicle?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumTheme.GreenDark)) {
        Box(Modifier.fillMaxWidth().height(144.dp).background(
            Brush.horizontalGradient(listOf(PremiumTheme.GreenDark, PremiumTheme.Green, Color(0xFF287260))))) {
            Column(Modifier.fillMaxHeight().fillMaxWidth(0.59f)
                .padding(start = 17.dp, top = 19.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.15f)) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color.White,
                        modifier = Modifier.padding(7.dp).size(22.dp))
                }
                Text("LIVE Демо аренды", color = Color.White,
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 20.sp)
                Text("Бронь, осмотр, поездка и возврат",
                    fontSize = 10.sp, lineHeight = 13.sp,
                    color = Color.White.copy(alpha = .9f))
            }
            if (car != null) {
                TruckArtwork(car,
                    Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(128.dp, 102.dp))
            } else {
                Icon(Icons.Outlined.LocalShipping, null, tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(83.dp))
            }
        }
    }
}

@Composable
fun PremiumFeature(value: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(17.dp), color = PremiumTheme.Mint) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PremiumTheme.Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(value, color = PremiumTheme.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
