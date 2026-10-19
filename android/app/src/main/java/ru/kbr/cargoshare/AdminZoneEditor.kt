package ru.kbr.cargoshare

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import java.util.UUID

/**
 * A local DEMO-only editor. No real role, publication, server sync or legal
 * permission is inferred from drawing a polygon on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminZoneEditor(onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { LocalParkingZoneStore(context) }
    val zones = rememberLocalParkingZones()
    val handle = rememberLibreMap(43.4853, 43.6071, 13.5)
    val map = handle.map
    var rule by rememberSaveable { mutableStateOf(ParkingRule.ALLOWED) }
    var title by rememberSaveable { mutableStateOf("") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    val draft = remember { mutableStateListOf<ZonePoint>() }
    var listVisible by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf("Коснитесь карты, чтобы добавить вершины зоны.") }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler { onClose() }
    val currentOnClick by rememberUpdatedState<(LatLng) -> Unit>({ tap ->
        if (draft.size < 40) {
            draft.add(ZonePoint(tap.latitude, tap.longitude))
            message = "Вершин: ${draft.size}. Минимум 3."
        } else message = "Максимум 40 вершин в зоне."
    })
    DisposableEffect(map) {
        if (map == null) onDispose { }
        else {
            val listener = MapLibreMap.OnMapClickListener { point ->
                currentOnClick(point)
                true
            }
            map.addOnMapClickListener(listener)
            onDispose { map.removeOnMapClickListener(listener) }
        }
    }

    // No animation/camera reset during polygon edits: only annotations refresh.
    LaunchedEffect(map, zones, draft.toList(), rule) {
        if (map == null) return@LaunchedEffect
        map.clear()
        zones.filter { it.enabled }.forEach { zone ->
            map.addPolygon(PolygonOptions().addAll(
                zone.vertices.map { LatLng(it.latitude, it.longitude) }
            ).fillColor(if (zone.rule == ParkingRule.ALLOWED)
                AndroidColor.argb(75, 0, 159, 88) else AndroidColor.argb(80, 220, 55, 55)
            ).strokeColor(if (zone.rule == ParkingRule.ALLOWED)
                AndroidColor.rgb(0, 130, 73) else AndroidColor.rgb(185, 25, 25)))
        }
        if (draft.size >= 2) {
            map.addPolyline(PolylineOptions()
                .addAll(draft.map { LatLng(it.latitude, it.longitude) } +
                    if (draft.size >= 3) listOf(LatLng(draft.first().latitude,
                        draft.first().longitude)) else emptyList())
                .width(4.5f).color(if (rule == ParkingRule.ALLOWED)
                    AndroidColor.rgb(0, 137, 78) else AndroidColor.rgb(201, 42, 42)))
        }
        draft.forEachIndexed { index, point ->
            // MapLibre SDK 12.3's annotation API supports markers, not addCircle.
            map.addMarker(MarkerOptions()
                .position(LatLng(point.latitude, point.longitude))
                .title("Вершина ${index + 1}"))
        }
    }

    Column(Modifier.fillMaxSize().background(PremiumTheme.Paper)) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.ArrowBack, "Назад", tint = PremiumTheme.GreenDark)
            }
            Column(Modifier.weight(1f)) {
                Text("Администратор · учебные зоны", fontSize = 17.sp,
                    color = PremiumTheme.Text, fontWeight = FontWeight.Bold)
                Text("Редактор на этом устройстве, без публикации", fontSize = 11.sp,
                    color = PremiumTheme.Muted)
            }
            TextButton(onClick = { listVisible = true }) {
                Text("Зоны (${zones.size})")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = rule == ParkingRule.ALLOWED, onClick = {
                rule = ParkingRule.ALLOWED
            }, label = { Text("Разрешено") },
                leadingIcon = { Icon(Icons.Outlined.CheckCircle, null,
                    Modifier.size(16.dp)) })
            FilterChip(selected = rule == ParkingRule.FORBIDDEN, onClick = {
                rule = ParkingRule.FORBIDDEN
            }, label = { Text("Запрещено") },
                leadingIcon = { Icon(Icons.Outlined.DoNotDisturb, null,
                    Modifier.size(16.dp)) })
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { handle.view }, modifier = Modifier.fillMaxSize())
            if (handle.loading) {
                Surface(Modifier.align(Alignment.Center),
                    color = Color.White, shape = RoundedCornerShape(14.dp)) {
                    Text("Загружаем карту…", Modifier.padding(12.dp))
                }
            }
            Surface(Modifier.align(Alignment.TopStart).padding(12.dp),
                color = Color.White.copy(alpha = .95f),
                shape = RoundedCornerShape(11.dp)) {
                Text("1. Передвигайте карту. 2. Нажимайте точки контура.",
                    Modifier.padding(10.dp), fontSize = 11.sp,
                    color = PremiumTheme.Text)
            }
            Column(Modifier.align(Alignment.TopEnd).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FilledIconButton(onClick = { map?.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.zoomIn()) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White, contentColor = PremiumTheme.GreenDark)) {
                    Icon(Icons.Outlined.Add, "Приблизить")
                }
                FilledIconButton(onClick = { map?.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.zoomOut()) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White, contentColor = PremiumTheme.GreenDark)) {
                    Icon(Icons.Outlined.Remove, "Отдалить")
                }
            }
        }
        Surface(color = Color.White, shadowElevation = 5.dp,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(60) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Название зоны") },
                    placeholder = { Text("Например, Парковка на проспекте") })
                Text(message, fontSize = 11.sp, color = PremiumTheme.Muted, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        if (draft.isNotEmpty()) draft.removeAt(draft.lastIndex)
                    }, enabled = draft.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text("Отменить точку", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = {
                        draft.clear(); editId = null; enabled = true
                        title = ""; message = "Начните рисовать новую зону."
                    }, modifier = Modifier.weight(1f)) {
                        Text("Очистить", fontSize = 11.sp)
                    }
                }
                Button(modifier = Modifier.fillMaxWidth(),
                    enabled = draft.size >= 3 && title.isNotBlank(),
                    onClick = {
                        val vertices = draft.toList()
                        val id = editId ?: UUID.randomUUID().toString()
                        val updated = LocalParkingZone(id, title.trim(), rule,
                            vertices, enabled)
                        val combined = if (editId == null) zones + updated
                            else zones.map { if (it.id == editId) updated else it }
                        if (validZonePoints(vertices) && store.save(combined)) {
                            draft.clear(); editId = null; title = ""; enabled = true
                            message = "Учебная зона сохранена только на этом телефоне."
                        } else message = "Контур некорректен или превышен лимит 30 зон."
                    }) {
                    Text(if (editId != null) "Сохранить изменения"
                        else "Сохранить учебную зону")
                }
            }
        }
    }

    if (listVisible) {
        ModalBottomSheet(onDismissRequest = { listVisible = false },
            containerColor = PremiumTheme.Paper) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp).padding(bottom = 38.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Управление зонами", fontSize = 23.sp,
                    fontWeight = FontWeight.Bold, color = PremiumTheme.Text)
                Text("Все зоны доступны только на этом устройстве. " +
                    "Запрет имеет приоритет при пересечении с разрешённой зоной.",
                    fontSize = 12.sp, color = PremiumTheme.Muted)
                if (zones.isEmpty()) Text("Пока нет нарисованных зон.")
                zones.forEach { zone ->
                    Surface(color = Color.White,
                        shape = RoundedCornerShape(15.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (zone.rule == ParkingRule.ALLOWED)
                                    Icons.Outlined.CheckCircle
                                    else Icons.Outlined.DoNotDisturb,
                                    null, tint = if (zone.rule == ParkingRule.ALLOWED)
                                        PremiumTheme.GreenDark else Color(0xFFB72B2B))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(zone.title, color = PremiumTheme.Text,
                                        fontWeight = FontWeight.Bold)
                                    Text("${zone.vertices.size} точек · " +
                                        if (zone.rule == ParkingRule.ALLOWED) "разрешено"
                                        else "запрещено", fontSize = 11.sp)
                                }
                                Switch(checked = zone.enabled, onCheckedChange = { state ->
                                    store.save(zones.map {
                                        if (it.id == zone.id) it.copy(enabled = state) else it
                                    })
                                })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = {
                                    editId = zone.id; title = zone.title
                                    rule = zone.rule; enabled = zone.enabled
                                    draft.clear(); draft.addAll(zone.vertices)
                                    map?.animateCamera(
                                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                            LatLng(zone.vertices[0].latitude,
                                                zone.vertices[0].longitude), 15.0))
                                    listVisible = false
                                    message = "Редактируйте точки и сохраните изменения."
                                }, modifier = Modifier.weight(1f)) {
                                    Text("Изменить")
                                }
                                OutlinedButton(onClick = {
                                    pendingDeleteId = zone.id
                                }, modifier = Modifier.weight(1f)) {
                                    Text("Удалить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(onDismissRequest = { pendingDeleteId = null },
            title = { Text("Удалить учебную зону?") },
            text = { Text("Действие нельзя отменить. Другие зоны не затрагиваются.") },
            confirmButton = {
                TextButton(onClick = {
                    store.save(zones.filterNot { it.id == id })
                    if (editId == id) {
                        editId = null; title = ""; draft.clear()
                    }
                    pendingDeleteId = null
                }) { Text("Удалить") }
            }, dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Отмена")
                }
            })
    }
}
