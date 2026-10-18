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

object PremiumTheme {
    val Green = Color(0xFF07523E)
    val GreenDark = Color(0xFF003C2E)
    val Text = Color(0xFF080E20)
    val Paper = Color(0xFFFBFAF6)
    val Mint = Color(0xFFEAF6EF)
    val Muted = Color(0xFF61708F)
    val Stroke = Color(0xFFE5E8ED)
    val Lavender = Color(0xFFF0E8FF)
    val Purple = Color(0xFF7216FF)
    val Shape = RoundedCornerShape(22.dp)
}

fun artworkSource(vehicle: Vehicle, view: String = "hero"): String {
    val model = when(vehicle.id) { "gazelle-next" -> "gazelle"; "ford-transit" -> "transit"; else -> "largus" }
    return "file:///android_asset/premium/${model}_${view}.webp"
}
fun localTruckArtwork(vehicle: Vehicle): String = artworkSource(vehicle)

@Composable fun TruckArtwork(vehicle: Vehicle, modifier: Modifier = Modifier, view: String = "hero") {
    AsyncImage(model = artworkSource(vehicle, view), contentDescription = "${vehicle.name} · $view · сгенерированное демофото",
        contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(14.dp)).background(PremiumTheme.Mint))
}

@Composable fun DemoNotice(onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = PremiumTheme.GreenDark)
            Spacer(Modifier.width(7.dp))
            Text("ОФЛАЙН-СИМУЛЯЦИЯ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PremiumTheme.GreenDark)
            Text(" · Без реальной брони и оплаты", fontSize = 9.sp, color = PremiumTheme.GreenDark, modifier = Modifier.weight(1f))
            if(onClick != null) Icon(Icons.Outlined.ChevronRight, "О деморежиме", Modifier.size(18.dp))
        }
    }
    if(onClick == null) Surface(shape = RoundedCornerShape(24.dp), color = PremiumTheme.Mint, content = content)
    else Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = PremiumTheme.Mint, content = content)
}

@Composable fun PremiumBrandHeader(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("ГРУЗШЕР", fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, color = PremiumTheme.GreenDark)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = PremiumTheme.GreenDark, modifier = Modifier.size(16.dp))
                Text("Нальчик · Кабардино-Балкария", fontSize = 11.sp, color = PremiumTheme.Muted)
            }
        }
        Surface(shape = RoundedCornerShape(50.dp), color = PremiumTheme.Mint) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ДЕМО", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PremiumTheme.GreenDark)
            }
        }
    }
}

@Composable fun DemoPill() {
    Surface(shape = RoundedCornerShape(50.dp), color = PremiumTheme.Mint) {
        Text("ДЕМО", fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = PremiumTheme.GreenDark,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp))
    }
}

@Composable fun PremiumDemoBanner(car: Vehicle?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Box(Modifier.fillMaxWidth().height(116.dp).background(PremiumTheme.GreenDark)) {
            AsyncImage("file:///android_asset/premium/live_banner.webp", null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Row(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                listOf(PremiumTheme.GreenDark.copy(alpha = .97f),
                    PremiumTheme.GreenDark.copy(alpha = .75f), Color.Transparent)))
                .padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Грузовые автомобили\nв вашем городе",
                        color = Color.White, fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp, lineHeight = 21.sp)
                    Text("Выбор машины и обучение аренде",
                        color = Color.White, fontSize = 10.sp)
                }
                Surface(shape = RoundedCornerShape(50.dp), color = PremiumTheme.Green) {
                    Icon(Icons.Outlined.ArrowForward, "Открыть демо",
                        modifier = Modifier.padding(9.dp).size(19.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable fun StageBanner(title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(20.dp)).background(PremiumTheme.GreenDark)) {
        AsyncImage("file:///android_asset/premium/live_banner.webp", null, contentScale=ContentScale.Crop, modifier=Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(PremiumTheme.GreenDark.copy(alpha=.96f), PremiumTheme.GreenDark.copy(alpha=.78f), Color.Transparent)
        )).padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement=Arrangement.Center) {
            Text("● LIVE Поездка · ДЕМО", color=Color.White, fontSize=10.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, color=Color.White, fontSize=21.sp, lineHeight=25.sp,
                fontWeight=FontWeight.ExtraBold, maxLines=3,
                modifier=Modifier.fillMaxWidth(.81f))
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color=Color.White, fontSize=11.sp, lineHeight=14.sp,
                maxLines=2, modifier=Modifier.fillMaxWidth(.82f))
        }
    }
}

@Composable fun PremiumFeature(value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(17.dp), color = PremiumTheme.Mint) {
        Row(Modifier.padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
            Icon(icon, null, tint=PremiumTheme.Green, modifier=Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(value, color=PremiumTheme.Text, fontSize=12.sp, fontWeight=FontWeight.SemiBold)
        }
    }
}
