package com.metrolist.music.geras.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.metrolist.music.R

/** Theme presets from the "Appearance" screen; [COVER] follows the playing track's artwork. */
enum class GerasThemePreset(val hue: Float, val saturation: Float) {
    COVER(25f, 0.35f),
    NEUTRAL(30f, 0.04f),
    AMOLED(30f, 0.03f),
    SKY(212f, 0.45f),
    MINT(158f, 0.38f),
    VIOLET(266f, 0.42f),
    BLOSSOM(338f, 0.45f),
    SAND(36f, 0.40f),
    AQUA(186f, 0.42f),
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

object GerasFonts {
    val text = FontFamily(
        variable(R.font.geras_manrope, 400),
        variable(R.font.geras_manrope, 500),
        variable(R.font.geras_manrope, 600),
        variable(R.font.geras_manrope, 700),
        variable(R.font.geras_manrope, 800),
    )
    val display = FontFamily(
        variable(R.font.geras_unbounded, 600),
        variable(R.font.geras_unbounded, 800),
        variable(R.font.geras_unbounded, 900),
    )
}

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

/** Pulls any seed towards a warm, muted version of itself (same idea as the player palette). */
fun seedFromColor(color: Color): Pair<Float, Float> {
    val out = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), out)
    var diff = 25f - out[0]
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return ((out[0] + diff * 0.15f + 360f) % 360f) to (out[1] * 0.7f).coerceIn(0.08f, 0.5f)
}

fun gerasColorScheme(hue: Float, sat: Float, dark: Boolean, amoled: Boolean): ColorScheme {
    val h = hue
    val s = sat
    val accentSat = (s + 0.25f).coerceIn(0.2f, 0.75f)
    return if (dark) {
        val base = if (amoled) 0.0f else 0.075f
        fun surf(l: Float) = if (amoled) hsl(h, s * 0.35f, l * 0.75f) else hsl(h, s * 0.5f, l)
        darkColorScheme(
            primary = hsl(h, accentSat, 0.80f),
            onPrimary = hsl(h, 0.45f, 0.14f),
            primaryContainer = hsl(h, s * 0.8f, 0.28f),
            onPrimaryContainer = hsl(h, accentSat, 0.90f),
            inversePrimary = hsl(h, accentSat, 0.40f),
            secondary = hsl(h, s * 0.7f, 0.76f),
            onSecondary = hsl(h, s * 0.5f, 0.15f),
            secondaryContainer = hsl(h, s * 0.6f, 0.22f),
            onSecondaryContainer = hsl(h, s * 0.6f, 0.88f),
            tertiary = hsl(h + 35f, accentSat * 0.8f, 0.78f),
            onTertiary = hsl(h + 35f, 0.4f, 0.15f),
            tertiaryContainer = hsl(h + 35f, s * 0.6f, 0.26f),
            onTertiaryContainer = hsl(h + 35f, 0.5f, 0.9f),
            background = if (amoled) Color.Black else hsl(h, s * 0.5f, base),
            onBackground = hsl(h, 0.12f, 0.93f),
            surface = if (amoled) Color.Black else hsl(h, s * 0.5f, base),
            onSurface = hsl(h, 0.12f, 0.93f),
            surfaceVariant = surf(0.17f),
            onSurfaceVariant = hsl(h, 0.10f, 0.70f),
            surfaceTint = hsl(h, accentSat, 0.80f),
            inverseSurface = hsl(h, 0.1f, 0.92f),
            inverseOnSurface = hsl(h, s * 0.4f, 0.12f),
            outline = hsl(h, s * 0.3f, 0.42f),
            outlineVariant = hsl(h, s * 0.4f, 0.24f),
            scrim = Color.Black,
            surfaceBright = surf(0.20f),
            surfaceDim = if (amoled) Color.Black else hsl(h, s * 0.5f, 0.06f),
            surfaceContainerLowest = if (amoled) Color.Black else hsl(h, s * 0.5f, 0.055f),
            surfaceContainerLow = surf(0.095f),
            surfaceContainer = surf(0.115f),
            surfaceContainerHigh = surf(0.14f),
            surfaceContainerHighest = surf(0.17f),
        )
    } else {
        lightColorScheme(
            primary = hsl(h, accentSat, 0.42f),
            onPrimary = Color.White,
            primaryContainer = hsl(h, accentSat, 0.86f),
            onPrimaryContainer = hsl(h, 0.5f, 0.15f),
            inversePrimary = hsl(h, accentSat, 0.80f),
            secondary = hsl(h, s * 0.6f, 0.38f),
            onSecondary = Color.White,
            secondaryContainer = hsl(h, s * 0.6f, 0.88f),
            onSecondaryContainer = hsl(h, s * 0.5f, 0.15f),
            tertiary = hsl(h + 35f, accentSat * 0.8f, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(h + 35f, s * 0.6f, 0.88f),
            onTertiaryContainer = hsl(h + 35f, 0.5f, 0.15f),
            background = hsl(h, s * 0.5f, 0.965f),
            onBackground = hsl(h, 0.2f, 0.10f),
            surface = hsl(h, s * 0.5f, 0.965f),
            onSurface = hsl(h, 0.2f, 0.10f),
            surfaceVariant = hsl(h, s * 0.5f, 0.90f),
            onSurfaceVariant = hsl(h, 0.12f, 0.35f),
            surfaceTint = hsl(h, accentSat, 0.42f),
            inverseSurface = hsl(h, 0.15f, 0.15f),
            inverseOnSurface = hsl(h, 0.1f, 0.94f),
            outline = hsl(h, s * 0.3f, 0.55f),
            outlineVariant = hsl(h, s * 0.4f, 0.82f),
            scrim = Color.Black,
            surfaceBright = hsl(h, s * 0.5f, 0.985f),
            surfaceDim = hsl(h, s * 0.5f, 0.90f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, s * 0.5f, 0.945f),
            surfaceContainer = hsl(h, s * 0.5f, 0.925f),
            surfaceContainerHigh = hsl(h, s * 0.5f, 0.905f),
            surfaceContainerHighest = hsl(h, s * 0.5f, 0.885f),
        )
    }
}

fun gerasTypography(): Typography {
    val t = Typography()
    fun TextStyle.text(weight: FontWeight? = null) = copy(fontFamily = GerasFonts.text, fontWeight = weight ?: fontWeight)
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = GerasFonts.display, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        displayMedium = t.displayMedium.copy(fontFamily = GerasFonts.display, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        displaySmall = t.displaySmall.copy(fontFamily = GerasFonts.display, fontWeight = FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.text(FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.text(FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.text(FontWeight.Bold),
        titleLarge = t.titleLarge.text(FontWeight.Bold),
        titleMedium = t.titleMedium.text(FontWeight.SemiBold),
        titleSmall = t.titleSmall.text(FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.text(FontWeight.Medium),
        bodyMedium = t.bodyMedium.text(FontWeight.Medium),
        bodySmall = t.bodySmall.text(FontWeight.Medium),
        labelLarge = t.labelLarge.text(FontWeight.SemiBold),
        labelMedium = t.labelMedium.text(FontWeight.SemiBold),
        labelSmall = t.labelSmall.text(FontWeight.SemiBold),
    )
}

private val typography by lazy { gerasTypography() }
private val systemTypography by lazy {
    val t = Typography()
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = GerasFonts.display, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        displayMedium = t.displayMedium.copy(fontFamily = GerasFonts.display, fontWeight = FontWeight.Black),
    )
}

/**
 * Replaces Metrolist's Material You theme: warm muted palette from the cover (or a preset),
 * Manrope for text and Unbounded for display titles. [coverColor] is the colour Metrolist
 * already extracts from the playing track.
 */
@Composable
fun GerasAppTheme(
    darkTheme: Boolean,
    pureBlack: Boolean,
    coverColor: Color,
    preset: GerasThemePreset,
    content: @Composable () -> Unit,
) {
    val (targetH, targetS) = remember(coverColor, preset) {
        if (preset == GerasThemePreset.COVER) seedFromColor(coverColor) else preset.hue to preset.saturation
    }
    val hue by animateFloatAsState(targetH, tween(800), label = "themeHue")
    val sat by animateFloatAsState(targetS, tween(800), label = "themeSat")
    val amoled = darkTheme && (pureBlack || preset == GerasThemePreset.AMOLED)
    val scheme = gerasColorScheme(hue, sat, darkTheme, amoled)
    val font by com.metrolist.music.utils.rememberPreference(com.metrolist.music.geras.GerasFontKey, "MANROPE")
    MaterialTheme(colorScheme = scheme, typography = if (font == "SYSTEM") systemTypography else typography, content = content)
}
