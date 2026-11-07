package com.metrolist.music.geras.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.geras.GerasFontKey
import com.metrolist.music.geras.GerasGlassBlurKey
import com.metrolist.music.geras.GerasGlassEnabledKey
import com.metrolist.music.geras.GerasGlassOpacityKey
import com.metrolist.music.geras.GerasThemeKey
import com.metrolist.music.geras.theme.GerasFonts
import com.metrolist.music.geras.theme.GerasThemePreset
import com.metrolist.music.geras.theme.gerasColorScheme
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import java.util.Locale

const val GerasSettingsRoute = "settings/geras"

@Composable
fun GerasSettings(navController: NavController) {
    val (theme, onThemeChange) = rememberPreference(GerasThemeKey, "COVER")
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val (glass, onGlassChange) = rememberPreference(GerasGlassEnabledKey, true)
    val (opacity, onOpacityChange) = rememberPreference(GerasGlassOpacityKey, 0.55f)
    val (blur, onBlurChange) = rememberPreference(GerasGlassBlurKey, 22f)
    val (font, onFontChange) = rememberPreference(GerasFontKey, "MANROPE")
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        GerasSettingsGroup(
            title = stringResource(R.string.geras_theme_mode),
            items = listOf(
                DarkMode.AUTO to (R.string.geras_mode_system to R.string.geras_mode_system_hint),
                DarkMode.ON to (R.string.geras_mode_dark to R.string.geras_mode_dark_hint),
                DarkMode.OFF to (R.string.geras_mode_light to R.string.geras_mode_light_hint),
            ).map { (mode, texts) ->
                Material3SettingsItem(
                    title = { Text(stringResource(texts.first)) },
                    description = { Text(stringResource(texts.second)) },
                    trailingContent = if (darkMode == mode) {
                        { Icon(painterResource(R.drawable.check), null, tint = scheme.primary) }
                    } else {
                        {}
                    },
                    onClick = { onDarkModeChange(mode) },
                )
            },
        )

        GerasSettingsGroup(
            title = stringResource(R.string.geras_theme),
            items = GerasThemePreset.entries.map { it.name }.map { name ->
                val preset = GerasThemePreset.entries.firstOrNull { it.name == name }
                Material3SettingsItem(
                    leadingContent = { Swatch(preset) },
                    title = { Text(presetLabel(name)) },
                    trailingContent = if (theme == name || (name == "COVER" && GerasThemePreset.entries.none { it.name == theme })) {
                        { Icon(painterResource(R.drawable.check), null, tint = scheme.primary) }
                    } else {
                        {}
                    },
                    onClick = { onThemeChange(name) },
                )
            },
        )

        GerasSettingsGroup(
            title = stringResource(R.string.geras_glass),
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.geras_glass)) },
                    description = { Text(stringResource(if (glass) R.string.geras_glass_on else R.string.geras_glass_off)) },
                    trailingContent = { Switch(checked = glass, onCheckedChange = onGlassChange) },
                    onClick = { onGlassChange(!glass) },
                ),
                Material3SettingsItem(
                    title = {
                        Column {
                            Row {
                                Text(stringResource(R.string.geras_glass_opacity), modifier = Modifier.weight(1f))
                                Text("${(opacity * 100).toInt()}%", color = scheme.onSurfaceVariant)
                            }
                            Slider(value = opacity, onValueChange = onOpacityChange, valueRange = 0.1f..0.95f, enabled = glass)
                        }
                    },
                    enabled = glass,
                ),
                Material3SettingsItem(
                    title = {
                        Column {
                            Row {
                                Text(stringResource(R.string.geras_glass_blur), modifier = Modifier.weight(1f))
                                Text(String.format(Locale.US, "%.0fpx", blur), color = scheme.onSurfaceVariant)
                            }
                            Slider(value = blur, onValueChange = onBlurChange, valueRange = 0f..40f, enabled = glass)
                        }
                    },
                    enabled = glass,
                ),
            ),
        )

        GerasSettingsGroup(
            title = stringResource(R.string.geras_font),
            items = GerasFonts.choices.map { (id, family) ->
                val label = if (id == "SYSTEM") stringResource(R.string.geras_font_system) else id.lowercase().replaceFirstChar { it.uppercase() }
                Material3SettingsItem(
                    title = { Text(label, fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                    description = { Text(stringResource(R.string.geras_font_sample), fontFamily = family) },
                    trailingContent = if (font == id || (id == "MANROPE" && GerasFonts.choices.none { it.first == font })) {
                        { Icon(painterResource(R.drawable.check), null, tint = scheme.primary) }
                    } else {
                        {}
                    },
                    onClick = { onFontChange(id) },
                )
            },
        )
        Spacer(Modifier.height(24.dp))
    }

    GerasTopBar(title = stringResource(R.string.geras_appearance), onBack = navController::navigateUp)
}

@Composable
private fun presetLabel(name: String): String = when (name) {
    "COVER" -> stringResource(R.string.geras_theme_cover)
    "NEUTRAL" -> "Neutral"
    "AMOLED" -> "AMOLED"
    else -> name.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun Swatch(preset: GerasThemePreset?) {
    val (a, b) = when (preset) {
        null -> Color(0xFF8E7CC3) to Color(0xFF3B3150)
        GerasThemePreset.AMOLED -> Color.White to Color.Black
        GerasThemePreset.COVER -> Color(0xFFC0392B) to Color(0xFF2A1410)
        else -> gerasColorScheme(preset.hue, preset.saturation, true, false).let { it.primary to it.surfaceContainerHigh }
    }
    Box(
        Modifier
            .size(width = 28.dp, height = 18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.horizontalGradient(0.5f to a, 0.5f to b)),
    )
}

/** Back button in a circle and the title in a capsule, as in the reference design. */
@Composable
fun GerasTopBar(title: String, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(scheme.background, scheme.background.copy(alpha = 0f))))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(scheme.surfaceContainerHigh)
                .clickable(onClick = onBack),
        ) {
            Icon(painterResource(R.drawable.arrow_back), null, tint = scheme.onSurface, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(50))
                .background(scheme.surfaceContainerHigh),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = scheme.onSurface)
        }
    }
}
