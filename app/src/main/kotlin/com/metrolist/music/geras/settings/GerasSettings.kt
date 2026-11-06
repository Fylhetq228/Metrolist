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
import com.metrolist.music.geras.GerasHomeEnabledKey
import com.metrolist.music.geras.GerasLyricsEnabledKey
import com.metrolist.music.geras.GerasNavStyleKey
import com.metrolist.music.geras.GerasPlayerEnabledKey
import com.metrolist.music.geras.GerasThemeKey
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
    val (playerEnabled, onPlayerEnabledChange) = rememberPreference(GerasPlayerEnabledKey, true)
    val (lyricsEnabled, onLyricsEnabledChange) = rememberPreference(GerasLyricsEnabledKey, true)
    val (homeEnabled, onHomeEnabledChange) = rememberPreference(GerasHomeEnabledKey, true)
    val (theme, onThemeChange) = rememberPreference(GerasThemeKey, "COVER")
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val (navStyle, onNavStyleChange) = rememberPreference(GerasNavStyleKey, "FLOATING")
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
                        null
                    },
                    onClick = { onDarkModeChange(mode) },
                )
            },
        )

        GerasSettingsGroup(
            title = stringResource(R.string.geras_theme),
            items = (GerasThemePreset.entries.map { it.name } + "MATERIAL").map { name ->
                val preset = GerasThemePreset.entries.firstOrNull { it.name == name }
                Material3SettingsItem(
                    leadingContent = { Swatch(preset) },
                    title = { Text(presetLabel(name)) },
                    trailingContent = if (theme == name) {
                        { Icon(painterResource(R.drawable.check), null, tint = scheme.primary) }
                    } else {
                        null
                    },
                    onClick = { onThemeChange(name) },
                )
            },
        )

        Text(
            stringResource(R.string.geras_tab_style),
            fontSize = 13.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("STANDARD" to R.string.geras_tabs_standard, "FLOATING" to R.string.geras_tabs_floating).forEach { (id, label) ->
                val selected = navStyle == id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceContainer)
                        .then(if (selected) Modifier.border(1.dp, scheme.primary, RoundedCornerShape(18.dp)) else Modifier)
                        .clickable { onNavStyleChange(id) }
                        .padding(vertical = 14.dp),
                ) {
                    Icon(painterResource(R.drawable.tab), null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(label), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) scheme.onSurface else scheme.onSurfaceVariant)
                }
            }
        }

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
            items = listOf("MANROPE" to "Manrope", "SYSTEM" to stringResource(R.string.geras_font_system)).map { (id, label) ->
                Material3SettingsItem(
                    title = { Text(label) },
                    trailingContent = if (font == id) {
                        { Icon(painterResource(R.drawable.check), null, tint = scheme.primary) }
                    } else {
                        null
                    },
                    onClick = { onFontChange(id) },
                )
            },
        )

        GerasSettingsGroup(
            title = stringResource(R.string.geras_settings_player_section),
            items = listOf(
                toggle(R.drawable.play, R.string.geras_player_title, R.string.geras_player_description, playerEnabled, onPlayerEnabledChange),
                toggle(R.drawable.lyrics, R.string.geras_lyrics_title, R.string.geras_lyrics_description, lyricsEnabled, onLyricsEnabledChange),
                toggle(R.drawable.home_outlined, R.string.geras_home_title, R.string.geras_home_description, homeEnabled, onHomeEnabledChange),
            ),
        )
        Spacer(Modifier.height(24.dp))
    }

    GerasTopBar(title = stringResource(R.string.geras_appearance), onBack = navController::navigateUp)
}

@Composable
private fun toggle(icon: Int, title: Int, description: Int, checked: Boolean, onChange: (Boolean) -> Unit) =
    Material3SettingsItem(
        icon = painterResource(icon),
        title = { Text(stringResource(title)) },
        description = { Text(stringResource(description)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        onClick = { onChange(!checked) },
    )

@Composable
private fun presetLabel(name: String): String = when (name) {
    "COVER" -> stringResource(R.string.geras_theme_cover)
    "MATERIAL" -> stringResource(R.string.geras_theme_material)
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
