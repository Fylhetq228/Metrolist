package com.metrolist.music.geras.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AppBarHeight
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.viewmodels.HomeViewModel

private enum class WaveMode { USUAL, DISCOVER }

/**
 * "My wave" block at the top of Home: a loose collage of covers from what you play, a big
 * play button that starts an endless YTM radio seeded from your taste, and recent history.
 */
@Composable
fun GerasHomeHeader(viewModel: HomeViewModel) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val discover by viewModel.dailyDiscover.collectAsStateWithLifecycle()
    val latest by database.latestEvent().collectAsStateWithLifecycle(initialValue = null)
    val eventCount by database.eventCount().collectAsStateWithLifecycle(initialValue = 0)
    var mode by rememberSaveable { mutableStateOf(WaveMode.USUAL) }
    val scheme = MaterialTheme.colorScheme

    val covers = remember(quickPicks, discover, mode) {
        val list = if (mode == WaveMode.DISCOVER) {
            discover.orEmpty().mapNotNull { it.recommendation.thumbnail }
        } else {
            quickPicks.orEmpty().mapNotNull { it.song.thumbnailUrl }
        }
        list.distinct().take(4).map { it.resize(400, 400) }
    }

    val startWave: () -> Unit = {
        val seed = when (mode) {
            WaveMode.USUAL -> quickPicks?.randomOrNull()?.toMediaMetadata()
            WaveMode.DISCOVER -> discover?.randomOrNull()?.seed?.toMediaMetadata()
        } ?: latest?.song?.toMediaMetadata()
        seed?.let { playerConnection.playQueue(YouTubeQueue.radio(it)) }
    }

    // Home hides Metrolist's top bar; pull the header up into the space reserved for it.
    Column(
        Modifier
            .layout { measurable, constraints ->
                val shift = AppBarHeight.roundToPx()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
                    placeable.place(0, -shift)
                }
            }
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = stringResource(R.string.geras_my_wave),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp, lineHeight = 50.sp),
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            HeaderIcon(R.drawable.history) { navController.navigate("history") }
            HeaderIcon(R.drawable.stats) { navController.navigate("stats") }
            HeaderIcon(R.drawable.settings) { navController.navigate("settings") }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(top = 12.dp),
        ) {
            // Soft swoosh behind the collage.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = 40.dp, y = (-10).dp)
                    .size(width = 360.dp, height = 46.dp)
                    .rotate(-28f)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, scheme.primary.copy(alpha = 0.22f), Color.Transparent))),
            )
            val floatAnim = rememberInfiniteTransition(label = "waveFloat")
            val bob by floatAnim.animateFloat(0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "bob")
            Cover(covers.getOrNull(0), 120.dp, Modifier.align(Alignment.TopStart).offset(y = 10.dp).graphicsLayer { translationY = bob * 6f })
            Cover(covers.getOrNull(1), 66.dp, Modifier.align(Alignment.TopEnd).offset(x = (-14).dp).graphicsLayer { translationY = -bob * 5f })
            Cover(covers.getOrNull(2), 60.dp, Modifier.align(Alignment.Center).offset(x = 38.dp, y = (-6).dp).graphicsLayer { translationY = bob * 4f }, circle = true)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-6).dp)
                    .size(76.dp)
                    .shadow(18.dp, RoundedCornerShape(50), spotColor = scheme.primary)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primary)
                    .clickable(onClick = startWave),
            ) {
                Icon(painterResource(R.drawable.play), null, tint = scheme.onPrimary, modifier = Modifier.size(36.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 18.dp),
            ) {
                ModeChip(stringResource(R.string.geras_wave_usual), mode == WaveMode.USUAL) { mode = WaveMode.USUAL }
                ModeChip(stringResource(R.string.geras_wave_discover), mode == WaveMode.DISCOVER) { mode = WaveMode.DISCOVER }
            }
        }

        val recent = latest?.song
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surfaceContainer)
                .clickable { navController.navigate("history") }
                .padding(8.dp),
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceVariant),
            ) {
                AsyncImage(model = recent?.song?.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.history), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.geras_recent), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = scheme.onSurface)
                }
                Text(
                    text = pluralStringResource(R.plurals.geras_tracks, eventCount, eventCount),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeaderIcon(icon: Int, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 2.dp, top = 6.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) scheme.onSurface.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) scheme.onSurface else scheme.onSurfaceVariant)
    }
}

@Composable
private fun Cover(url: String?, size: Dp, modifier: Modifier, circle: Boolean = false) {
    val shape = if (circle) RoundedCornerShape(50) else RoundedCornerShape(size * 0.16f)
    Box(
        modifier
            .size(size)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
