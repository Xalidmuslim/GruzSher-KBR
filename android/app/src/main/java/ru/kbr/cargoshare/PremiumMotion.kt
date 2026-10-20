package ru.kbr.cargoshare

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Calm, non-bouncy, premium motion for the native Android UI.
 *
 * Compose schedules animations on choreographer vsync: on a real 120 Hz device
 * it can render at 120 fps when the GPU/CPU can keep the ~8.33 ms frame budget.
 * A duration must NEVER be implemented by frame counting or sleep().
 *
 * Keep animations in graphicsLayer (alpha/translation/scale) rather than
 * repeatedly measuring layout or rebuilding MapView/MapLibre surfaces.
 */
internal object PremiumMotion {
    const val SCREEN_MS = 640
    const val CARD_MS = 520
    const val GALLERY_MS = 620
    const val TOUCH_MS = 380
    val Smooth: Easing = CubicBezierEasing(0.20f, 0.0f, 0.20f, 1.0f)
}

/** Lightweight GPU-layer entrance: no dual-screen composition or map relayout. */
@Composable
internal fun PremiumEntrance(
    key: Any?,
    modifier: Modifier = Modifier,
    riseDp: Float = 9f,
    content: @Composable BoxScope.() -> Unit
) {
    val progress = remember(key) { Animatable(0f) }
    val density = LocalDensity.current.density
    LaunchedEffect(key) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(PremiumMotion.SCREEN_MS, easing = PremiumMotion.Smooth)
        )
    }
    Box(modifier.graphicsLayer {
        alpha = 0.78f + progress.value * 0.22f
        translationY = (1f - progress.value) * riseDp * density
    }, content = content)
}

/** Small matte emphasis rather than a sharp jump on selected truck cards. */
@Composable
internal fun Modifier.premiumSelected(selected: Boolean): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.015f else 1f,
        animationSpec = tween(PremiumMotion.CARD_MS, easing = PremiumMotion.Smooth),
        label = "truck-card-emphasis"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
