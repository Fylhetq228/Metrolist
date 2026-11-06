package com.metrolist.music.geras.player

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class GerasThemeMode { LIGHT, DARK, AMOLED }

@Immutable
data class GerasPlayerColors(
    val backgroundTop: Color,
    val background: Color,
    val surface: Color,
    val content: Color,
    val contentMuted: Color,
    val accent: Color,
    val onAccent: Color,
)

private val hueCache = java.util.concurrent.ConcurrentHashMap<String, FloatArray>()

/** Hue/saturation of the artwork, or null while unknown. Result is cached per URL. */
private suspend fun extractSeed(context: Context, url: String): FloatArray? {
    hueCache[url]?.let { return it }
    return withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(96, 96)
            .allowHardware(false)
            .build()
        val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
            ?: return@withContext null
        val palette = Palette.from(bitmap).maximumColorCount(12).generate()
        val swatch = palette.mutedSwatch
            ?: palette.darkMutedSwatch
            ?: palette.dominantSwatch
            ?: palette.vibrantSwatch
            ?: return@withContext null
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(swatch.rgb, hsl)
        hsl.also { hueCache[url] = it }
    }
}

/** Pulls the hue towards orange and caps saturation so every cover gives a warm, muted tint. */
private fun warm(hsl: FloatArray): Pair<Float, Float> {
    val hue = hsl[0]
    val target = 30f
    var diff = target - hue
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    val warmedHue = (hue + diff * 0.18f + 360f) % 360f
    val sat = (hsl[1] * 0.75f).coerceIn(0.06f, 0.38f)
    return warmedHue to sat
}

private fun hsl(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val argb = ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))
    return Color(argb).copy(alpha = alpha)
}

fun gerasColorsFor(seed: FloatArray?, mode: GerasThemeMode): GerasPlayerColors {
    val (h, s) = seed?.let(::warm) ?: (30f to 0.08f)
    return when (mode) {
        GerasThemeMode.DARK -> GerasPlayerColors(
            backgroundTop = hsl(h, s * 0.85f, 0.20f),
            background = hsl(h, s * 0.6f, 0.09f),
            surface = hsl(h, s * 0.5f, 0.92f, alpha = 0.08f),
            content = hsl(h, 0.12f, 0.95f),
            contentMuted = hsl(h, 0.10f, 0.95f, alpha = 0.6f),
            accent = hsl(h, (s + 0.12f).coerceAtMost(0.5f), 0.78f),
            onAccent = hsl(h, s * 0.6f, 0.10f),
        )
        GerasThemeMode.AMOLED -> GerasPlayerColors(
            backgroundTop = hsl(h, s * 0.8f, 0.12f),
            background = Color.Black,
            surface = hsl(h, s * 0.5f, 0.92f, alpha = 0.07f),
            content = hsl(h, 0.10f, 0.94f),
            contentMuted = hsl(h, 0.10f, 0.94f, alpha = 0.55f),
            accent = hsl(h, (s + 0.12f).coerceAtMost(0.5f), 0.76f),
            onAccent = Color.Black,
        )
        GerasThemeMode.LIGHT -> GerasPlayerColors(
            backgroundTop = hsl(h, s * 0.9f, 0.86f),
            background = hsl(h, s * 0.55f, 0.95f),
            surface = hsl(h, s * 0.6f, 0.12f, alpha = 0.06f),
            content = hsl(h, 0.20f, 0.10f),
            contentMuted = hsl(h, 0.15f, 0.10f, alpha = 0.6f),
            accent = hsl(h, (s + 0.15f).coerceAtMost(0.55f), 0.30f),
            onAccent = hsl(h, 0.2f, 0.97f),
        )
    }
}

@Composable
fun rememberGerasPlayerColors(thumbnailUrl: String?, mode: GerasThemeMode): GerasPlayerColors {
    val context = LocalContext.current
    var seed by remember { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl != null) extractSeed(context, thumbnailUrl)?.let { seed = it }
    }
    val target = remember(seed, mode) { gerasColorsFor(seed, mode) }
    val spec = tween<Color>(durationMillis = 900)
    val backgroundTop by animateColorAsState(target.backgroundTop, spec, label = "bgTop")
    val background by animateColorAsState(target.background, spec, label = "bg")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val content by animateColorAsState(target.content, spec, label = "content")
    val contentMuted by animateColorAsState(target.contentMuted, spec, label = "contentMuted")
    val accent by animateColorAsState(target.accent, spec, label = "accent")
    val onAccent by animateColorAsState(target.onAccent, spec, label = "onAccent")
    return GerasPlayerColors(backgroundTop, background, surface, content, contentMuted, accent, onAccent)
}
