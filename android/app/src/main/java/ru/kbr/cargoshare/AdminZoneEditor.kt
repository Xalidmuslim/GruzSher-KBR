package ru.kbr.cargoshare

import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.PointF
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import org.maplibre.android.annotations.IconFactory
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
    var message by remember { mutableStateOf("Нажимайте карту для добавления вершин. Зажмите вершину, чтобы сдвинуть.") }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var beforeDrag by remember { mutableStateOf<List<ZonePoint>?>(null) }
    var lastDragFrame by remember { mutableLongStateOf(0L) }
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


    // Native MapLibre annotations in 12.x are not draggable by simply setting
    // MarkerOptions.draggable. Detect a LONG PRESS near the coordinate handle
    // and move that vertex via the map's screen↔geo projection. Normal taps,
    // pinches and single-finger panning remain handled by MapLibre.
    DisposableEffect(handle.view, map) {
        val view = handle.view
        if (map == null) {
            onDispose { }
        } else {
            val detector = GestureDetector(view.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean = true
                    override fun onLongPress(e: MotionEvent) {
                        val threshold = 48f * view.resources.displayMetrics.density
                        val target = draft.indexOfFirst { point ->
                            val screenPoint = map.projection.toScreenLocation(
                                LatLng(point.latitude, point.longitude))
                            val deltaX = screenPoint.x - e.x
                            val deltaY = screenPoint.y - e.y
                            deltaX * deltaX + deltaY * deltaY <= threshold * threshold
                        }
                        if (target >= 0) {
                            beforeDrag = draft.toList()
                            dragIndex = target
                            lastDragFrame = 0L
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            map.uiSettings.isScrollGesturesEnabled = false
                            message = "Перемещаем вершину ${target + 1}. Отпустите палец, чтобы применить."
                        }
                    }
                })
            view.setOnTouchListener { v, event ->
                detector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_MOVE -> {
                        if (dragIndex >= 0 && dragIndex < draft.size) {
                            val nowMs = SystemClock.uptimeMillis()
                            if (nowMs - lastDragFrame >= 40L) {
                                val updated = map.projection.fromScreenLocation(
                                    PointF(event.x, event.y))
                                draft[dragIndex] = ZonePoint(
                                    updated.latitude.coerceIn(-90.0, 90.0),
                                    updated.longitude.coerceIn(-180.0, 180.0))
                                lastDragFrame = nowMs
                            }
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (dragIndex >= 0 && dragIndex < draft.size &&
                            event.actionMasked == MotionEvent.ACTION_UP) {
                            val updated = map.projection.fromScreenLocation(
                                PointF(event.x, event.y))
                            draft[dragIndex] = ZonePoint(updated.latitude, updated.longitude)
                            message = "Вершина ${dragIndex + 1} перемещена. Сохраните изменения."
                        }
                        if (dragIndex >= 0) {
                            dragIndex = -1
                            map.uiSettings.isScrollGesturesEnabled = true
                        }
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                dragIndex >= 0
            }
            onDispose {
                dragIndex = -1
                map.uiSettings.isScrollGesturesEnabled = true
                view.setOnTouchListener(null)
            }
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
        if (draft.size >= 3) {
            map.addPolygon(PolygonOptions().addAll(draft.map {
                LatLng(it.latitude, it.longitude)
            }).fillColor(if (rule == ParkingRule.ALLOWED)
                AndroidColor.argb(35, 0, 144, 87) else AndroidColor.argb(38, 215, 57, 60))
                .strokeColor(AndroidColor.TRANSPARENT))
        }
        if (draft.size >= 2) {
            map.addPolyline(PolylineOptions()
                .addAll(draft.map { LatLng(it.latitude, it.longitude) } +
                    if (draft.size >= 3) listOf(LatLng(draft.first().latitude,
                        draft.first().longitude)) else emptyList())
                .width(4.5f).color(if (rule == ParkingRule.ALLOWED)
                    AndroidColor.rgb(0, 137, 78) else AndroidColor.rgb(201, 42, 42)))
        }
        val icons = IconFactory.getInstance(context)
        draft.forEachIndexed { index, point ->
            map.addMarker(MarkerOptions()
                .position(LatLng(point.latitude, point.longitude))
                .icon(icons.fromBitmap(adminVertexIcon(index + 1, rule)))
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
                Text("Редактор парковочных зон", fontSize = 17.sp,
                    color = PremiumTheme.Text, fontWeight = FontWeight.Bold)
                Text("Администратор · локальный учебный проект", fontSize = 11.sp,
                    color = PremiumTheme.Muted)
            }
            FilledTonalButton(onClick = { listVisible = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = PremiumTheme.Mint)) {
                Icon(Icons.Outlined.Layers, null, Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("${zones.size} зон", fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White)
            .padding(horizontal = 14.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.weight(1f))
            Text("${draft.size}/40", color = PremiumTheme.Muted, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { handle.view }, modifier = Modifier.fillMaxSize())
            if (handle.loading) {
                Surface(Modifier.align(Alignment.Center),
                    color = Color.White, shape = RoundedCornerShape(14.dp)) {
                    Text("Загружаем карту…", Modifier.padding(12.dp))
                }
            }
            Surface(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 11.dp, end = 72.dp),
                color = Color.White.copy(alpha = .94f), shadowElevation = 2.dp,
                shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EditLocationAlt, null, Modifier.size(18.dp),
                        tint = PremiumTheme.GreenDark)
                    Spacer(Modifier.width(7.dp))
                    Text("Касание — точка · удержание — перемещение",
                        fontSize = 10.sp, color = PremiumTheme.Text, maxLines = 2)
                }
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
        Surface(color = Color.White, shadowElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(60) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Название зоны") },
                    placeholder = { Text("Например, Парковка на проспекте") })
                Text(message, fontSize = 11.sp, color = PremiumTheme.Muted, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        if (beforeDrag != null) {
                            draft.clear(); draft.addAll(beforeDrag!!)
                            beforeDrag = null
                            message = "Последнее перемещение отменено."
                        } else if (draft.isNotEmpty()) draft.removeAt(draft.lastIndex)
                    }, enabled = draft.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Undo, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Отменить", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = {
                        draft.clear(); editId = null; enabled = true; beforeDrag = null
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
                            draft.clear(); editId = null; title = ""; enabled = true; beforeDrag = null
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
                                    draft.clear(); draft.addAll(zone.vertices); beforeDrag = null
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

/** Premium numbered vertex handles instead of MapLibre's red teardrop pins. */
private fun adminVertexIcon(number: Int, rule: ParkingRule): Bitmap {
    val size = 74
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(37f, 37f, 31f, paint)
    paint.color = if (rule == ParkingRule.ALLOWED)
        AndroidColor.rgb(0, 111, 81) else AndroidColor.rgb(183, 37, 46)
    canvas.drawCircle(37f, 37f, 25f, paint)
    paint.color = AndroidColor.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 26f
    paint.isFakeBoldText = true
    canvas.drawText(number.toString(), 37f,
        37f - (paint.ascent() + paint.descent()) / 2f, paint)
    return bitmap
}
