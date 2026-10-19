package ru.kbr.cargoshare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/**
 * Fully drawn local map markers. No raster cutouts or stock pin overlays.
 * Coordinates/amounts displayed by the caller remain demo-only until linked to fleet telemetry.
 */
fun truckMapMarker(context: Context, hourlyRub: Int? = null, selected: Boolean = false): Drawable {
    val scale = context.resources.displayMetrics.density
    val width = 106f
    val height = 77f
    val bitmap = Bitmap.createBitmap((width * scale).roundToInt(), (height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val green = Color.rgb(24, 79, 63)
    val strong = if (selected) Color.rgb(18, 113, 83) else green
    val white = Color.WHITE

    // Tariff chip above the vehicle. Compact enough not to obscure streets.
    if (hourlyRub != null) {
        paint.color = strong
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(5f, 1f, 101f, 29f), 14f, 14f, paint)
        paint.color = white
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12.5f
        paint.isFakeBoldText = true
        canvas.drawText("${hourlyRub} ₽/ч", 53f, 19.5f, paint)
    }

    // Pin body with a visually distinct truck rather than OSMDroid's hand-shaped default.
    val offset = if (hourlyRub == null) -12f else 0f
    val cy = 51f + offset
    paint.color = if (selected) Color.rgb(224, 241, 232) else white
    canvas.drawCircle(53f, cy, 20.5f, paint)
    paint.color = strong
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawCircle(53f, cy, 19f, paint)
    paint.style = Paint.Style.FILL

    // Truck glyph stays upright; only the demo pickup points are distributed.
    // Cargo box and cab, drawn as a single cohesive truck icon.
    canvas.drawRoundRect(RectF(39f, cy - 8f, 54f, cy + 4f), 2f, 2f, paint)
    canvas.drawRoundRect(RectF(55f, cy - 4f, 65f, cy + 4f), 2f, 2f, paint)
    val cab = Path().apply {
        moveTo(57f, cy - 8f)
        lineTo(61.5f, cy - 8f)
        lineTo(66f, cy - 3f)
        lineTo(66f, cy + 4f)
        lineTo(56f, cy + 4f)
        close()
    }
    canvas.drawPath(cab, paint)
    paint.color = white
    canvas.drawRect(59f, cy - 6.5f, 61f, cy - 4f, paint)
    paint.color = strong
    canvas.drawCircle(45f, cy + 5.5f, 3.2f, paint)
    canvas.drawCircle(60f, cy + 5.5f, 3.2f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Static solid-yellow 3D-style person for phone GPS on both fleet maps.
 * Flat, fully opaque colour planes create volume: no gradients, dark outlines,
 * black badge, semi-transparent person or direction-based rotation.
 * The bitmap background is transparent to leave map tiles unobstructed.
 */
fun phoneLocationMarker(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap(
        (36f * density).roundToInt().coerceAtLeast(1),
        (42f * density).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap).apply { scale(density, density) }
    val yellow = Color.rgb(255, 211, 24)
    val highlight = Color.rgb(255, 239, 122)
    val side = Color.rgb(232, 173, 0)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // All limbs and body are solid, rounded, and have an offset yellow side
    // face to provide subtle volume instead of translucent gradients.
    paint.color = side
    paint.strokeWidth = 7f
    canvas.drawLine(15.7f, 26f, 12f, 36.5f, paint)
    canvas.drawLine(21f, 26f, 24.7f, 36.5f, paint)
    canvas.drawLine(14.4f, 18.2f, 8.6f, 25.2f, paint)
    canvas.drawLine(22.6f, 18.2f, 28.4f, 25.2f, paint)
    paint.color = yellow
    paint.strokeWidth = 6.2f
    canvas.drawLine(14.8f, 25.5f, 11.5f, 36f, paint)
    canvas.drawLine(20f, 25.5f, 23.8f, 36f, paint)
    canvas.drawLine(14f, 18.4f, 8.7f, 24.4f, paint)
    canvas.drawLine(21f, 18.4f, 26.7f, 24.4f, paint)

    // Rounded extruded torso. Shaded right plane has a crisp, solid edge.
    paint.color = side
    canvas.drawRoundRect(RectF(12f, 15.2f, 24.2f, 29.8f), 5f, 5f, paint)
    paint.color = yellow
    canvas.drawRoundRect(RectF(11.8f, 15f, 21.6f, 29.2f), 4.8f, 4.8f, paint)
    paint.color = highlight
    canvas.drawRoundRect(RectF(13f, 16.4f, 15.4f, 25.6f), 1.2f, 1.2f, paint)

    // Solid spherical head: a small opaque highlight and side shading.
    paint.color = side
    canvas.drawCircle(18.8f, 9.2f, 6.7f, paint)
    paint.color = yellow
    canvas.drawCircle(17.3f, 8.6f, 6.5f, paint)
    paint.color = highlight
    canvas.drawCircle(15.1f, 6.9f, 2.2f, paint)
    return bitmap
}

/** A distinctly different moving pedestrian icon for the offline approach simulation. */
fun walkingMapMarker(context: Context): Drawable {
    val scale = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap((44 * scale).roundToInt(), (44 * scale).roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap).apply { scale(scale, scale) }
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 79, 63) }
    canvas.drawCircle(22f, 22f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    canvas.drawCircle(22f, 22f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 79, 63); style = Paint.Style.STROKE; strokeWidth = 2.5f
    })
    canvas.drawCircle(22f, 13f, 3.5f, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 3f
    p.strokeCap = Paint.Cap.ROUND
    canvas.drawLine(22f, 18f, 22f, 28f, p)
    canvas.drawLine(22f, 20f, 15f, 25f, p)
    canvas.drawLine(22f, 20f, 29f, 23f, p)
    canvas.drawLine(22f, 28f, 16f, 35f, p)
    canvas.drawLine(22f, 28f, 29f, 35f, p)
    return BitmapDrawable(context.resources, bitmap)
}

/** A parking zone icon, clearly separate from the vehicle on the demonstration map. */
fun returnZoneMapMarker(context: Context): Drawable {
    val scale = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap((44 * scale).roundToInt(), (44 * scale).roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap).apply { scale(scale, scale) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.WHITE
    canvas.drawCircle(22f, 22f, 20f, paint)
    paint.color = Color.rgb(24, 79, 63)
    paint.strokeWidth = 2.5f
    paint.style = Paint.Style.STROKE
    canvas.drawCircle(22f, 22f, 18f, paint)
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 22f
    paint.isFakeBoldText = true
    canvas.drawText("P", 22f, 30f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

/** Small, accurate hit target for nearby vehicles; prices shown in the detail sheet. */
fun compactTruckMarker(context: Context, selected: Boolean = false): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (49f * density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(density, density)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val green = if (selected) Color.rgb(20, 118, 88) else Color.rgb(24, 79, 63)
    p.color = if (selected) Color.rgb(228, 246, 235) else Color.WHITE
    canvas.drawCircle(24.5f, 24.5f, 22f, p)
    p.color = green
    p.style = Paint.Style.STROKE
    p.strokeWidth = if (selected) 3.5f else 2.5f
    canvas.drawCircle(24.5f, 24.5f, 21f, p)
    p.style = Paint.Style.FILL
    canvas.drawRoundRect(RectF(11f, 17.5f, 26f, 30f), 2f, 2f, p)
    canvas.drawRoundRect(RectF(27f, 22f, 38f, 30f), 1.8f, 1.8f, p)
    val wind = Path().apply {
        moveTo(29f, 18f)
        lineTo(33.5f, 18f)
        lineTo(38f, 23f)
        lineTo(38f, 30f)
        lineTo(27f, 30f)
        close()
    }
    canvas.drawPath(wind, p)
    p.color = Color.WHITE
    canvas.drawRoundRect(RectF(30f, 19.3f, 32.5f, 23f), 0.5f, 0.5f, p)
    p.color = green
    canvas.drawCircle(16f, 32.5f, 3.4f, p)
    canvas.drawCircle(33f, 32.5f, 3.4f, p)
    return BitmapDrawable(context.resources, bitmap)
}
