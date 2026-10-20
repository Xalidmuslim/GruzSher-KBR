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
import androidx.compose.ui.text.style.TextAlign
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
    onExpandMap: () -> Unit = {},
    onAction: (LiveEvent) -> Unit,
) {
    val fraction = routeFraction(session,now)
    val seconds = elapsedRideSeconds(session,now)
    val fare = demoFare(session,car?.hourlyPriceRub ?: 650,now)
    val parked = session.pauseStartedAt != 0L

    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Navigation, null, tint = PremiumTheme.Green)
        Spacer(Modifier.width(8.dp))
        Text(if (parked) "Ожидание на стоянке" else "Движение к стоянке",
            modifier = Modifier.weight(1f), color = PremiumTheme.Text,
            fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("ДЕМО", color = PremiumTheme.Green, fontSize = 11.sp)
    }
    Box(
        Modifier.fillMaxWidth().height(203.dp).clip(RoundedCornerShape(24.dp))
            .background(PremiumTheme.Mint)
    ) {
        LiveRouteMap(car, fraction, "грузовик", Modifier.fillMaxSize(),
            onExpand = onExpandMap)
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            color = PremiumTheme.GreenDark, shape = RoundedCornerShape(12.dp)
        ) {
            Text("${demoSpeedKmh(session)} км/ч",
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
        Column(Modifier.padding(horizontal = 9.dp, vertical = 9.dp),
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
    // Stable two-column tiles across 100–150% font scaling. Putting the
    // caption below the icon prevents long Russian words being crushed
    // between a leading glyph and right-side chevron.
    Surface(onClick = onClick, modifier = modifier.height(122.dp).premiumAppear(label, riseDp = 5f),
        shape = RoundedCornerShape(18.dp), color = Color.White,
        shadowElevation = 1.dp) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(color = PremiumTheme.Mint, shape = RoundedCornerShape(28.dp)) {
                Icon(icon, contentDescription = null, tint = PremiumTheme.Green,
                    modifier = Modifier.padding(9.dp).size(24.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label, color = PremiumTheme.Text,
                fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                maxLines = 2, lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
