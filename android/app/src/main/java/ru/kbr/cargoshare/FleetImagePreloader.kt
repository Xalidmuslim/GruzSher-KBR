package ru.kbr.cargoshare

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision

/**
 * Warm the most likely first-screen assets AFTER initial composition.
 *
 * Requests only *enqueue* decoding. Coil's shared image loader runs decode
 * work on its worker dispatchers and keeps bounded decoded bitmaps in the
 * in-memory cache. This does not read image bytes synchronously on the UI
 * thread and does not create one extra ImageLoader or one GPU layer per image.
 *
 * Do not predecode all 19 full-size gallery images: that would penalize the
 * cold start, GPU upload, RAM and background battery use.
 */
internal object FleetImagePreloader {
    private val aboveFoldImages = listOf(
        "live_banner.webp",
        "gazelle_hero.webp",
        "transit_hero.webp",
        "largus_hero.webp"
    )

    fun enqueueAboveTheFold(context: Context) {
        val appContext = context.applicationContext
        val loader = appContext.imageLoader
        for (asset in aboveFoldImages) {
            val request = ImageRequest.Builder(appContext)
                .data("file:///android_asset/premium/$asset")
                .size(620, 400)
                .precision(Precision.INEXACT)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .allowHardware(true)
                .build()
            loader.enqueue(request)
        }
    }
}
