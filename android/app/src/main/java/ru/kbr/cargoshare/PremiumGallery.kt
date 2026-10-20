package ru.kbr.cargoshare

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Native pager owns horizontal gestures; buttons and thumbnail taps use the same page state. */
@Composable
fun PremiumGallery(car: Vehicle) {
    val views = remember { listOf("hero", "rear", "cargo", "front", "left", "right") }
    val pager = rememberPagerState(pageCount = { views.size })
    val fullPager = rememberPagerState(pageCount = { views.size })
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(car.id) { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("gruzsher_preferences", 0)
    }
    var favourite by remember(car.id) {
        mutableStateOf(prefs.getBoolean("favourite_${car.id}", false))
    }
    LaunchedEffect(car.id) {
        pager.scrollToPage(0)
        fullPager.scrollToPage(0)
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            fullPager.scrollToPage(pager.currentPage)
            zoom = 1f; panX = 0f; panY = 0f
        } else {
            pager.scrollToPage(fullPager.currentPage)
        }
    }
    LaunchedEffect(fullPager.currentPage) {
        zoom = 1f; panX = 0f; panY = 0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.fillMaxWidth().height(193.dp).clip(RoundedCornerShape(19.dp))) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                TruckArtwork(car, Modifier.fillMaxSize().clickable { expanded = true }, views[page])
            }
            IconButton(onClick = {
                scope.launch { pager.animateScrollToPage((pager.currentPage + 5) % views.size) }
            }, modifier = Modifier.align(Alignment.CenterStart)
                .padding(5.dp).background(Color.White, RoundedCornerShape(50.dp))) {
                Icon(Icons.Outlined.ChevronLeft, "Предыдущее фото")
            }
            IconButton(onClick = {
                scope.launch { pager.animateScrollToPage((pager.currentPage + 1) % views.size) }
            }, modifier = Modifier.align(Alignment.CenterEnd)
                .padding(5.dp).background(Color.White, RoundedCornerShape(50.dp))) {
                Icon(Icons.Outlined.ChevronRight, "Следующее фото")
            }
            Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),
                color = Color.Black.copy(alpha = .6f), shape = RoundedCornerShape(18.dp)) {
                Text("${pager.currentPage + 1} / ${views.size}", color = Color.White,
                    fontSize = 11.sp, modifier = Modifier.padding(8.dp))
            }
            Row(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = {
                    favourite = !favourite
                    prefs.edit().putBoolean("favourite_${car.id}", favourite).apply()
                }, modifier = Modifier.background(Color.White, RoundedCornerShape(50.dp))) {
                    Icon(if (favourite) Icons.Outlined.Favorite
                        else Icons.Outlined.FavoriteBorder, "Избранное", tint = PremiumTheme.Green)
                }
                IconButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT,
                            "${car.name} · учебный каталог ГрузШер КБР · ${car.hourlyPriceRub} ₽/ч. Не является реальным предложением аренды.")
                    }, "Поделиться"))
                }, modifier = Modifier.background(Color.White, RoundedCornerShape(50.dp))) {
                    Icon(Icons.Outlined.Share, "Поделиться")
                }
            }
            TextButton(onClick = { expanded = true },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .background(Color.White.copy(alpha = .9f), RoundedCornerShape(18.dp))) {
                Text("Открыть галерею", fontSize = 10.sp)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(views.size) { n ->
                Surface(onClick = {
                    scope.launch { pager.animateScrollToPage(n) }
                }, shape = RoundedCornerShape(9.dp),
                    border = if (pager.currentPage == n) {
                        androidx.compose.foundation.BorderStroke(2.dp, PremiumTheme.Green)
                    } else null) {
                    TruckArtwork(car, Modifier.size(68.dp, 46.dp)
                        .padding(if (pager.currentPage == n) 2.dp else 0.dp), views[n])
                }
            }
        }
    }
    if (expanded) Dialog(
        onDismissRequest = { expanded = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = fullPager, modifier = Modifier.fillMaxSize(),
                userScrollEnabled = zoom <= 1.02f) { page ->
                AsyncImage(
                    artworkSource(car, views[page]),
                    "${car.name}, фото ${page + 1}. Проведите пальцем для следующего фото",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(page) {
                            // Process multi-touch only. Single-finger swipes remain
                            // owned by HorizontalPager, even in the fullscreen gallery.
                            awaitEachGesture {
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val factor = event.calculateZoom()
                                        val offset = event.calculatePan()
                                        zoom = (zoom * factor).coerceIn(1f, 4f)
                                        val limit = (zoom - 1f) * 260f
                                        panX = (panX + offset.x).coerceIn(-limit, limit)
                                        panY = (panY + offset.y).coerceIn(-limit, limit)
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer(scaleX = zoom, scaleY = zoom,
                            translationX = panX, translationY = panY)
                )
            }
            IconButton(onClick = { expanded = false },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()) {
                Icon(Icons.Outlined.Close, "Закрыть галерею", tint = Color.White)
            }
            Text("${fullPager.currentPage + 1} / ${views.size} · Сгенерированное демофото",
                color = Color.White, modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding().padding(20.dp))
        }
    }
}
