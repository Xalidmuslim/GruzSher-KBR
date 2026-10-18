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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Immersive ongoing rental dashboard. All telemetry labels explicitly remain simulated.
 * The actions go through the existing validated LiveDemoEngine transitions.
 */
@Composable
fun PremiumTripDashboard(
    session: LiveDemoSession, car: Vehicle?, now: Long,
    onAction: (LiveEvent) -> Unit,
) {
    val fraction = routeFraction(session,now)
    val seconds = elapsedRideSeconds(session,now)
    val fare = demoFare(session,car?.hourlyPriceRub ?: 650,now)
    val parked = session.pauseStartedAt != 0L

    // Compact dashboard instead of 14-step tutorial header above the map.
    Surface(color = PremiumTheme.GreenDark, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFF1B725B), shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Outlined.LocalShipping, null, tint = Color.White,
                    modifier = Modifier.padding(11.dp).size(27.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if(parked) "● РЕЖИМ ОЖИДАНИЯ" else "● ПОЕЗДКА ИДЁТ",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF97EEC7))
                Text(car?.name ?: "Грузовик", fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Демоаренда · управление виртуальное",
                    fontSize = 10.sp, color = Color.White.copy(alpha = .74f))
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().height(295.dp).clip(RoundedCornerShape(24.dp))
            .background(PremiumTheme.Mint)
    ) {
        LiveRouteMap(car, fraction, "грузовик")
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            color = Color.White, shadowElevation = 4.dp, shape = RoundedCornerShape(14.dp)
        ) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Navigation, null, tint = PremiumTheme.Green,
                    modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Нальчик · учебный маршрут",
                    fontSize = 11.sp, color = PremiumTheme.Text, fontWeight = FontWeight.Bold)
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            color = PremiumTheme.GreenDark, shape = RoundedCornerShape(12.dp)
        ) {
            Text("${demoSpeedKmh(session)} км/ч · ДЕМО",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        TripMetric(Icons.Outlined.Schedule, "Время",
            "${seconds / 60}:${(seconds % 60).toString().padStart(2,'0')}",
            Modifier.weight(1f))
        TripMetric(Icons.Outlined.Route, "Путь",
            String.format(Locale.US,"%.2f км",demoDistanceKm(session,now)), Modifier.weight(1f))
        TripMetric(Icons.Outlined.Payments, "Стоимость",
            "$fare ₽", Modifier.weight(1f))
    }
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalParking, null,
                    modifier = Modifier.size(18.dp), tint = PremiumTheme.Green)
                Spacer(Modifier.width(7.dp))
                Text("Движение к стоянке", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = PremiumTheme.Text,
                    modifier = Modifier.weight(1f))
                Text("${((LIVE_ROUTE_MS*(1-fraction))/1000f).roundToInt()} сек",
                    fontSize = 12.sp, color = PremiumTheme.Green)
            }
            LinearProgressIndicator(progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                color = PremiumTheme.Green, trackColor = PremiumTheme.Mint)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TripAction(
            if(parked) "Продолжить" else "Ожидание",
            if(parked) Icons.Outlined.PlayArrow else Icons.Outlined.PauseCircle,
            Modifier.weight(1f)) { onAction(LiveEvent.TOGGLE_WAIT) }
        TripAction("Продлить +2 ч", Icons.Outlined.AddAlarm, Modifier.weight(1f)) {
            onAction(LiveEvent.EXTEND)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TripAction("Поддержка", Icons.Outlined.HeadsetMic, Modifier.weight(1f)) {
            onAction(LiveEvent.ASK_SUPPORT)
        }
        TripAction("Происшествие", Icons.Outlined.ReportProblem, Modifier.weight(1f)) {
            onAction(LiveEvent.REPORT_INCIDENT)
        }
    }
}

@Composable
private fun TripMetric(
    symbol: androidx.compose.ui.graphics.vector.ImageVector,
    name: String, value: String, modifier: Modifier
) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(symbol, null, tint = PremiumTheme.Green, modifier = Modifier.size(18.dp))
            Text(name, fontSize = 10.sp, color = PremiumTheme.Muted)
            Text(value, maxLines = 1, color = PremiumTheme.GreenDark,
                fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun TripAction(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier, onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(17.dp), color = Color.White,
        shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = PremiumTheme.Green, modifier = Modifier.size(24.dp))
            Text(label, fontSize = 11.sp, color = PremiumTheme.Text,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
