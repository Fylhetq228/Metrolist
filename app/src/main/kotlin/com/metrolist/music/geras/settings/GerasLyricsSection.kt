package com.metrolist.music.geras.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.geras.GerasDropStyleKey
import com.metrolist.music.geras.GerasKickPulseKey
import com.metrolist.music.geras.GerasLyricsBlurKey
import com.metrolist.music.geras.GerasLyricsSizeKey
import com.metrolist.music.geras.GerasLyricsStyleKey
import com.metrolist.music.geras.lyrics.GerasLyricsColors
import com.metrolist.music.geras.lyrics.KItem
import com.metrolist.music.geras.lyrics.LineView
import com.metrolist.music.geras.lyrics.activeIndex
import com.metrolist.music.geras.lyrics.buildKaraoke
import com.metrolist.music.geras.ui.GerasSwitch
import com.metrolist.music.geras.ui.gerasBounce
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** Lyrics look: a looping live preview on top, every choice below changes it at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GerasLyricsSection() {
    val scheme = MaterialTheme.colorScheme
    var style by rememberPreference(GerasLyricsStyleKey, 0)
    var size by rememberPreference(GerasLyricsSizeKey, 1)
    var blur by rememberPreference(GerasLyricsBlurKey, true)
    var drop by rememberPreference(GerasDropStyleKey, 1)
    var kick by rememberPreference(GerasKickPulseKey, false)
    var sceneBackdrop by rememberPreference(com.metrolist.music.geras.GerasSceneBackdropKey, true)
    var sceneDust by rememberPreference(com.metrolist.music.geras.GerasSceneDustKey, true)
    var sceneCards by rememberPreference(com.metrolist.music.geras.GerasSceneCardsKey, true)
    var sceneKeywords by rememberPreference(com.metrolist.music.geras.GerasSceneKeywordsKey, true)

    val l1 = stringResource(R.string.geras_ly_preview_1)
    val l2 = stringResource(R.string.geras_ly_preview_2)
    val l3 = stringResource(R.string.geras_ly_preview_3)
    val items = remember(l1, l2, l3) {
        buildKaraoke(listOf(LyricsEntry(600, l1), LyricsEntry(3_400, l2), LyricsEntry(6_200, l3), LyricsEntry(9_000, "")), synced = true)
    }
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var start = -1L
        while (isActive) {
            withFrameMillis {
                if (start < 0) start = it
                clock.longValue = (it - start) % 9_600
            }
        }
    }
    val active by remember(items) { androidx.compose.runtime.derivedStateOf { activeIndex(items, clock.longValue) } }
    val colors = GerasLyricsColors(
        active = Color.White,
        inactive = Color.White.copy(alpha = 0.32f),
        backing = scheme.primary,
        accent = scheme.primary,
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.34f), Color(0xFF14110F), Color(0xFF0B0A09))))
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            items.forEachIndexed { i, item ->
                if (item is KItem.Line) {
                    LineView(
                        line = item.line,
                        isActive = i == active,
                        isPast = active >= 0 && i < active,
                        distance = if (active < 0) i + 1 else abs(i - active),
                        synced = true,
                        clock = clock,
                        colors = colors,
                        respectAgents = false,
                        style = style,
                        size = size,
                        depthBlur = blur,
                        onClick = {},
                        onLongClick = {},
                    )
                }
            }
        }

        Label(stringResource(R.string.geras_ly_style))
        Chips(
            listOf(R.string.geras_ly_style_glow, R.string.geras_ly_style_fill, R.string.geras_ly_style_bounce, R.string.geras_ly_style_calm, R.string.geras_ly_style_neon),
            style,
        ) { style = it }

        Label(stringResource(R.string.geras_ly_size))
        Chips(listOf(R.string.geras_ly_size_s, R.string.geras_ly_size_m, R.string.geras_ly_size_l), size) { size = it }

        Label(stringResource(R.string.geras_ly_drop))
        Chips(listOf(R.string.geras_ly_drop_off, R.string.geras_ly_drop_punch, R.string.geras_ly_drop_wave, R.string.geras_ly_drop_hype), drop) { drop = it }
        Text(
            stringResource(R.string.geras_ly_drop_hint),
            fontSize = 12.5.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )

        Column(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surfaceContainer),
        ) {
            Toggle(stringResource(R.string.geras_ly_kick), stringResource(R.string.geras_ly_kick_hint), kick) { kick = it }
            Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(scheme.onSurface.copy(alpha = 0.07f)))
            Toggle(stringResource(R.string.geras_ly_blur), stringResource(R.string.geras_ly_blur_hint), blur) { blur = it }
        }

        Label(stringResource(R.string.geras_scene))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surfaceContainer),
        ) {
            Toggle(stringResource(R.string.geras_scene_backdrop), stringResource(R.string.geras_scene_backdrop_hint), sceneBackdrop) { sceneBackdrop = it }
            Toggle(stringResource(R.string.geras_scene_dust), stringResource(R.string.geras_scene_dust_hint), sceneDust) { sceneDust = it }
            Toggle(stringResource(R.string.geras_scene_cards), stringResource(R.string.geras_scene_cards_hint), sceneCards) { sceneCards = it }
            Toggle(stringResource(R.string.geras_scene_keywords), stringResource(R.string.geras_scene_keywords_hint), sceneKeywords) { sceneKeywords = it }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(labels: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, res ->
            val on = i == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(40.dp)
                    .gerasBounce(0.94f)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) scheme.primary else scheme.surfaceContainerHigh)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(res), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) scheme.onPrimary else scheme.onSurface)
            }
        }
    }
}

@Composable
private fun Toggle(title: String, hint: String, on: Boolean, change: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { change(!on) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Text(hint, fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        }
        GerasSwitch(checked = on, onCheckedChange = change, accent = scheme.primary, track = scheme.onSurface.copy(alpha = 0.15f))
    }
}
