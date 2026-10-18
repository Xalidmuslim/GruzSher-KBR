package ru.kbr.cargoshare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** Accessible navigation hub, retaining the full 13-stage demo behind an explicit action. */
@Composable fun GruzSherMoreScreen(onDemo: () -> Unit, onFleet: () -> Unit,
                                   onTrips: () -> Unit, onProfile: () -> Unit) {
    Column(Modifier.fillMaxSize().background(PremiumTheme.Paper)
        .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ГРУЗШЕР", fontSize = 25.sp, fontWeight = FontWeight.Black,
                color = PremiumTheme.GreenDark, modifier = Modifier.weight(1f))
            DemoPill()
        }
        Text("Ещё", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            color = PremiumTheme.Text)
        MoreAction("Тренажёр аренды", "Все этапы: от выбора машины до чека",
            Icons.Outlined.PlayArrow, onDemo)
        MoreAction("Автомобили", "Каталог, тарифы и карта Нальчика",
            Icons.Outlined.LocalShipping, onFleet)
        MoreAction("Мои поездки", "Текущие и завершённые учебные заявки",
            Icons.Outlined.ReceiptLong, onTrips)
        MoreAction("Профиль и настройки", "Данные демопользователя",
            Icons.Outlined.AccountCircle, onProfile)
        Surface(shape = RoundedCornerShape(17.dp), color = PremiumTheme.Mint) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("О демонстрационной версии", fontWeight = FontWeight.Bold,
                    color = PremiumTheme.GreenDark)
                Text("Платежи, документы, GPS машин, замки и поддержка имитируются. " +
                    "Реальная аренда через приложение пока недоступна.",
                    fontSize = 12.sp, lineHeight = 17.sp, color = PremiumTheme.GreenDark)
                Text("Версия ${BuildConfig.VERSION_NAME}", fontSize = 11.sp,
                    color = PremiumTheme.Muted)
            }
        }
    }
}

@Composable private fun MoreAction(title: String, detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    Surface(onClick = action, shape = RoundedCornerShape(18.dp), color = Color.White,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = PremiumTheme.GreenDark, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = PremiumTheme.Text)
                Text(detail, fontSize = 11.sp, color = PremiumTheme.Muted)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = PremiumTheme.GreenDark)
        }
    }
}
