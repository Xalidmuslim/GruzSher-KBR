package ru.kbr.cargoshare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

private val ProfileGreen = PremiumTheme.GreenDark
private val ProfileInk = PremiumTheme.Text
private val ProfileMuted = PremiumTheme.Muted
private val ProfileMint = PremiumTheme.Mint
private val ProfilePaper = PremiumTheme.Paper

/**
 * The offline account does not collect or store passports or payment credentials.
 * All editable preferences and support notes remain in local Android preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalProfile(
    live: LiveDemoSession, bookings: List<Booking>,
    onLive: () -> Unit, onTrips: () -> Unit,
    onMore: () -> Unit, onFleet: () -> Unit,
    fontScale: Float, onFontScaleChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences("gruzsher_preferences", 0)
    }
    var alias by remember { mutableStateOf(prefs.getString("profile_alias", "Демо-пользователь") ?: "Демо-пользователь") }
    var draftAlias by rememberSaveable { mutableStateOf(alias) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var adminOpen by rememberSaveable { mutableStateOf(false) }
    var mapPrices by remember { mutableStateOf(prefs.getBoolean("map_prices_visible", true)) }
    var parkingZones by remember { mutableStateOf(prefs.getBoolean("map_parking_zones_visible", true)) }
    var mapEngine by remember { mutableStateOf(normalizeMapEngine(prefs.getString(MAP_ENGINE_KEY, MAP_ENGINE_OSMDROID))) }
    var payment by remember { mutableStateOf(prefs.getString("demo_payment_method", "virtual_0000") ?: "virtual_0000") }
    var supportTopic by rememberSaveable { mutableStateOf("Общий вопрос") }
    var supportDraft by rememberSaveable { mutableStateOf("") }
    var supportJournal by remember {
        mutableStateOf(prefs.getString("account_journal", "[]") ?: "[]")
    }
    var confirmClearSupport by remember { mutableStateOf(false) }
    var confirmResetSettings by remember { mutableStateOf(false) }
    val activeCount = bookings.count { it.status !in setOf("COMPLETED", "CANCELLED", "EXPIRED") }
    val finishedCount = bookings.count { it.status == "COMPLETED" }
    val hasActiveTrip = live.phase !in setOf(LivePhase.CHOOSE, LivePhase.RECEIPT) &&
        live.carId.isNotBlank()
    val favourites = offlineDemoCatalog().filter { prefs.getBoolean("favourite_${it.id}", false) }

    if (adminOpen) {
        AdminZoneEditor(onClose = { adminOpen = false })
        return
    }
    Column(Modifier.fillMaxSize().background(ProfilePaper)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("ГРУЗШЕР", modifier = Modifier.weight(1f), color = ProfileGreen,
                fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            DemoPill()
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 15.dp).padding(bottom = 35.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Личный кабинет", fontSize = 26.sp, color = ProfileInk,
                fontWeight = FontWeight.ExtraBold)
            Surface(shape = RoundedCornerShape(22.dp), color = ProfileGreen) {
                Row(Modifier.fillMaxWidth().clickable {
                    draftAlias = alias; panel = "Мои данные"
                }.padding(17.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .16f)) {
                        Icon(Icons.Outlined.PersonOutline, null, tint = Color.White,
                            modifier = Modifier.padding(13.dp).size(28.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(alias, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Учебный аккаунт · Нальчик", fontSize = 11.sp,
                            color = Color.White.copy(alpha = .84f))
                        Text("Без регистрации и реальных платежей", fontSize = 10.sp,
                            color = Color.White.copy(alpha = .7f))
                    }
                    Icon(Icons.Outlined.Edit, "Изменить имя", tint = Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ProfileStat("Активные", activeCount.toString(), Modifier.weight(1f))
                ProfileStat("Завершённые", finishedCount.toString(), Modifier.weight(1f))
                ProfileStat("Избранное", favourites.size.toString(), Modifier.weight(1f))
            }
            if (hasActiveTrip) {
                Surface(onClick = onLive, color = ProfileMint,
                    shape = RoundedCornerShape(17.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocalShipping, null, tint = ProfileGreen)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Продолжить учебную аренду", color = ProfileGreen,
                                fontWeight = FontWeight.Bold)
                            Text("Вернуться к текущему этапу", fontSize = 11.sp,
                                color = ProfileMuted)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = ProfileGreen)
                    }
                }
            }
            ProfileSection("Личные данные") {
                ProfileMenuItem("Мой профиль", alias, Icons.Outlined.AccountCircle) {
                    draftAlias = alias; panel = "Мои данные"
                }
                HorizontalDivider(color = ProfileMint)
                ProfileMenuItem("Документы", "Состояние учебных образцов", Icons.Outlined.Badge) {
                    panel = "Документы"
                }
                HorizontalDivider(color = ProfileMint)
                ProfileMenuItem("Способы оплаты", "Виртуальные карты · без списаний",
                    Icons.Outlined.CreditCard) { panel = "Оплата" }
            }
            ProfileSection("Мои поездки") {
                ProfileMenuItem("История аренды", "$finishedCount завершено",
                    Icons.Outlined.ReceiptLong, onTrips)
                HorizontalDivider(color = ProfileMint)
                ProfileMenuItem("Избранные автомобили", "${favourites.size} автомобилей",
                    Icons.Outlined.FavoriteBorder) { panel = "Избранное" }
            }
            ProfileSection("Помощь и сервис") {
                ProfileMenuItem("Поддержка", "Вопросы, локальные сообщения, история",
                    Icons.Outlined.SupportAgent) { panel = "Поддержка" }
                HorizontalDivider(color = ProfileMint)
                ProfileMenuItem("Вопросы и ответы", "Бронирование, возврат, тарифы",
                    Icons.Outlined.HelpOutline) { panel = "Вопросы и ответы" }
                HorizontalDivider(color = ProfileMint)
                ProfileMenuItem("Настройки", "Отображение карты и локальные данные",
                    Icons.Outlined.Settings) { panel = "Настройки" }
                HorizontalDivider(color = ProfileMint)
                ProfileMenuItem("Администратор · демо", "Рисование разрешённых и запрещённых зон",
                    Icons.Outlined.Map) { adminOpen = true }
                HorizontalDivider(color = ProfileMint)
                ProfileMenuItem("О приложении", "Версия и ограничения",
                    Icons.Outlined.Info) { panel = "О приложении" }
            }
            OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Outlined.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Все режимы и тренажёр")
            }
        }
    }

    panel?.let { page ->
        ModalBottomSheet(onDismissRequest = { panel = null },
            containerColor = ProfilePaper) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 38.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(page, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = ProfileInk)
                when (page) {
                    "Мои данные" -> {
                        ProfileDataForm(prefs) { savedName ->
                            alias = savedName
                            draftAlias = savedName
                            panel = null
                        }
                    }
                    "Документы" -> {
                        ProfileHint("Здесь виден только результат учебной симуляции. " +
                            "Ни паспорт, ни водительское удостоверение не загружаются и не хранятся.")
                        val passport = live.passportStartedAt > 0L
                        val licence = live.licenceStartedAt > 0L
                        ProfileStatus("Паспорт · учебный образец",
                            if (passport) "Образец выбран в тренажёре" else "Не проходили",
                            passport)
                        ProfileStatus("Водительское удостоверение",
                            if (licence) "Образец выбран в тренажёре" else "Не проходили",
                            licence)
                        ProfileHint("Статус учебного образца не означает проверку личности.")
                        Button(onClick = { panel = null; onLive() },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Перейти к автопроверке в LIVE")
                        }
                    }
                    "Оплата" -> {
                        ProfileHint("Это полностью виртуальные способы оплаты. " +
                            "Нельзя вводить номер настоящей карты или совершить списание.")
                        listOf("virtual_0000" to "Учебная карта •••• 0000",
                            "virtual_1111" to "Учебная карта •••• 1111").forEach { (id, label) ->
                            Surface(onClick = {
                                payment = id
                                prefs.edit().putString("demo_payment_method", id).apply()
                            }, color = Color.White, shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(15.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CreditCard, null,
                                        tint = ProfileGreen)
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, modifier = Modifier.weight(1f),
                                        color = ProfileInk, fontWeight = FontWeight.SemiBold)
                                    RadioButton(selected = payment == id,
                                        onClick = {
                                            payment = id
                                            prefs.edit().putString("demo_payment_method", id).apply()
                                        })
                                }
                            }
                        }
                        Text("Выбранная учебная карта появится в платёжном шаге LIVE.",
                            fontSize = 11.sp, color = ProfileMuted)
                        OutlinedButton(onClick = { panel = null; onLive() },
                            modifier = Modifier.fillMaxWidth()) { Text("Открыть учебную оплату") }
                    }
                    "Избранное" -> {
                        if (favourites.isEmpty()) ProfileHint(
                            "В каталоге нажмите сердечко на понравившемся грузовике.")
                        favourites.forEach { car ->
                            Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(13.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(car.name, modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.SemiBold)
                                    Text("${car.hourlyPriceRub} ₽/ч", color = ProfileGreen)
                                }
                            }
                        }
                        OutlinedButton(onClick = { panel = null; onFleet() },
                            modifier = Modifier.fillMaxWidth()) { Text("Перейти в каталог") }
                    }
                    "Настройки" -> {
                        ProfileHint("Выбранный движок используется для всех карт: " +
                            "автопарк, подход к машине и возврат. Карты требуют интернет.")
                        Text("Картографическая система", fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, color = ProfileInk)
                        listOf(
                            MAP_ENGINE_OSMDROID to ("OpenStreetMap (текущая)" to
                                "Растровая карта OSMDroid · стабильный вариант"),
                            MAP_ENGINE_MAPLIBRE to ("MapLibre" to
                                "Векторная карта OpenFreeMap · без API-ключа")
                        ).forEach { (engine, label) ->
                            Surface(onClick = {
                                mapEngine = engine
                                prefs.edit().putString(MAP_ENGINE_KEY, engine).apply()
                            }, shape = RoundedCornerShape(14.dp),
                                color = if (mapEngine == engine) ProfileMint else Color.White) {
                                Row(Modifier.fillMaxWidth().padding(13.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Map, null, tint = ProfileGreen)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(label.first, fontWeight = FontWeight.SemiBold,
                                            color = ProfileInk)
                                        Text(label.second, fontSize = 11.sp, color = ProfileMuted)
                                    }
                                    RadioButton(selected = mapEngine == engine, onClick = {
                                        mapEngine = engine
                                        prefs.edit().putString(MAP_ENGINE_KEY, engine).apply()
                                    })
                                }
                            }
                        }
                        ProfileHint("При нестабильном интернете или если MapLibre не загружает " +
                            "стиль, выберите OpenStreetMap. Положение грузовиков в демо учебное.")
                        ProfileToggle("Цены на карте", "Показывать стоимость рядом с грузовиками",
                            mapPrices) {
                            mapPrices = it
                            prefs.edit().putBoolean("map_prices_visible", it).apply()
                        }
                        ProfileToggle("Учебные зоны возврата",
                            "Показывать только во время поездки, не при выборе машины", parkingZones) {
                            parkingZones = it
                            prefs.edit().putBoolean("map_parking_zones_visible", it).apply()
                        }
                        ProfileHint("MapLibre использует русские названия, если они " +
                            "есть в картографических данных; для остальных остаётся оригинал. " +
                            "Администратор управляет только локальными учебными зонами.")
                        OutlinedButton(onClick = { confirmResetSettings = true },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Восстановить настройки карты")
                        }
                    }
                    "Поддержка" -> {
                        ProfileHint("Это локальный журнал вопросов, а не чат с оператором. " +
                            "Сообщения никуда не отправляются. При срочной ситуации " +
                            "обращайтесь в соответствующую реальную службу.")
                        Text("Тема обращения", fontWeight = FontWeight.SemiBold,
                            color = ProfileInk)
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()) {
                                Text(supportTopic, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = expanded,
                                onDismissRequest = { expanded = false }) {
                                listOf("Общий вопрос", "Бронирование", "Возврат",
                                    "Документы", "Оплата", "Ошибка приложения").forEach { t ->
                                    DropdownMenuItem(text = { Text(t) }, onClick = {
                                        supportTopic = t; expanded = false
                                    })
                                }
                            }
                        }
                        OutlinedTextField(value = supportDraft,
                            onValueChange = { supportDraft = it.take(500) },
                            label = { Text("Ваш вопрос") },
                            minLines = 3, modifier = Modifier.fillMaxWidth())
                        Button(enabled = supportDraft.isNotBlank(),
                            onClick = {
                                val saved = runCatching { JSONArray(supportJournal) }
                                    .getOrDefault(JSONArray())
                                saved.put(JSONObject().put("topic", supportTopic)
                                    .put("message", supportDraft.trim())
                                    .put("at", OffsetDateTime.now().toString()))
                                val bounded = JSONArray()
                                for (i in maxOf(0, saved.length() - 30) until saved.length())
                                    bounded.put(saved.optJSONObject(i))
                                supportJournal = bounded.toString()
                                prefs.edit().putString("account_journal", supportJournal).apply()
                                supportDraft = ""
                            }, modifier = Modifier.fillMaxWidth()) {
                            Text("Сохранить обращение локально")
                        }
                        val history = runCatching { JSONArray(supportJournal) }
                            .getOrDefault(JSONArray())
                        if (history.length() > 0) {
                            Text("История на устройстве", fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold)
                            for (i in history.length() - 1 downTo maxOf(0, history.length() - 10)) {
                                val note = history.optJSONObject(i) ?: continue
                                ProfileNote(note.optString("topic", "Поддержка"),
                                    note.optString("message"))
                            }
                            TextButton(onClick = { confirmClearSupport = true }) {
                                Text("Очистить локальную историю")
                            }
                        }
                    }
                    "Вопросы и ответы" -> {
                        listOf(
                            "Как начать аренду?" to "Выберите автомобиль, тариф, затем пройдите шаги LIVE. Все операции в этой версии учебные.",
                            "Как завершить поездку?" to "В тренажёре двигайтесь к обозначенной зелёной зоне, пройдите фотоосмотр и закройте виртуальные двери.",
                            "Где можно парковаться?" to "Зелёная и красная зоны на карте иллюстративные. Реальных разрешений и запретов здесь нет.",
                            "Снимаются ли деньги?" to "Нет. Карты, стоимость и подтверждения оплаты в демонстрационном режиме виртуальные.",
                            "Проверяются ли документы?" to "Нет. Это образцы для демонстрации процесса, без загрузки паспорта или распознавания личности.",
                            "Работает ли GPS?" to "Карта OSM показывает местность; положения машин и учебные маршруты не являются реальной телематикой."
                        ).forEach { (q, a) ->
                            ProfileNote(q, a)
                        }
                    }
                    else -> {
                        ProfileHint("ГрузШер ${BuildConfig.VERSION_NAME}. " +
                            "Учебная версия: реальные регистрация, проверка документов, " +
                            "платежи, телематика, поддержка и управление замками не подключены.")
                        ProfileStatus("Платформа", "Нативное приложение Android", true)
                        ProfileStatus("Карты", "OpenStreetMap · тайлы требуют интернет", true)
                        ProfileStatus("История", "Учебные записи сохраняются на устройстве", true)
                        Button(onClick = { panel = null; onMore() },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Все режимы")
                        }
                    }
                }
            }
        }
    }
    if (confirmClearSupport) {
        AlertDialog(onDismissRequest = { confirmClearSupport = false },
            title = { Text("Удалить историю поддержки?") },
            text = { Text("Будут удалены только локальные сообщения. Поездки не затрагиваются.") },
            confirmButton = {
                TextButton(onClick = {
                    supportJournal = "[]"
                    prefs.edit().remove("account_journal").apply()
                    confirmClearSupport = false
                }) { Text("Удалить") }
            }, dismissButton = {
                TextButton(onClick = { confirmClearSupport = false }) { Text("Отмена") }
            })
    }
    if (confirmResetSettings) {
        AlertDialog(onDismissRequest = { confirmResetSettings = false },
            title = { Text("Сбросить настройки карты?") },
            text = { Text("Вернётся OpenStreetMap, цены и учебные зоны будут показаны.") },
            confirmButton = {
                TextButton(onClick = {
                    mapPrices = true; parkingZones = true; mapEngine = MAP_ENGINE_OSMDROID
                    prefs.edit().remove("map_prices_visible")
                        .remove("map_parking_zones_visible")
                        .putString(MAP_ENGINE_KEY, MAP_ENGINE_OSMDROID).apply()
                    confirmResetSettings = false
                }) { Text("Сбросить") }
            }, dismissButton = {
                TextButton(onClick = { confirmResetSettings = false }) { Text("Отмена") }
            })
    }
}

@Composable private fun ProfileStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = Color.White) {
        Column(Modifier.padding(vertical = 13.dp, horizontal = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = ProfileGreen, fontSize = 23.sp,
                fontWeight = FontWeight.Bold)
            Text(label, color = ProfileMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = ProfileInk,
            fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 5.dp))
        Surface(shape = RoundedCornerShape(19.dp), color = Color.White) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
        }
    }
}

@Composable private fun ProfileMenuItem(label: String, subtitle: String,
    symbol: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(color = ProfileMint, shape = RoundedCornerShape(12.dp)) {
            Icon(symbol, null, tint = ProfileGreen,
                modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = ProfileInk, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ProfileMuted, fontSize = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = ProfileMuted)
    }
}

@Composable private fun ProfileHint(message: String) {
    Surface(shape = RoundedCornerShape(15.dp), color = ProfileMint) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(14.dp),
            fontSize = 12.sp, lineHeight = 18.sp, color = ProfileGreen)
    }
}

@Composable private fun ProfileStatus(label: String, status: String, done: Boolean) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
        Row(Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                null, tint = ProfileGreen)
            Spacer(Modifier.width(9.dp))
            Column {
                Text(label, fontWeight = FontWeight.SemiBold, color = ProfileInk)
                Text(status, fontSize = 11.sp, color = ProfileMuted)
            }
        }
    }
}

@Composable private fun ProfileToggle(label: String, detail: String,
    enabled: Boolean, onChange: (Boolean) -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, color = ProfileInk)
                Text(detail, fontSize = 11.sp, color = ProfileMuted)
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@Composable private fun ProfileNote(title: String, message: String) {
    Surface(shape = RoundedCornerShape(15.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = ProfileGreen)
            Text(message, fontSize = 12.sp, lineHeight = 18.sp, color = ProfileInk)
        }
    }
}
