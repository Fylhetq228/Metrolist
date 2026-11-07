package com.metrolist.music.geras.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.geras.settings.GerasTopBar
import com.metrolist.music.geras.ui.gerasAppear
import com.metrolist.music.geras.ui.gerasBounce
import com.metrolist.music.geras.ui.gerasRaised
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.utils.rememberPreference
import java.time.LocalDateTime

const val GerasProfileRoute = "geras/profile"

private enum class Period(val label: Int) {
    WEEK(R.string.geras_period_week), MONTH(R.string.geras_period_month), YEAR(R.string.geras_period_year), ALL(R.string.geras_period_all);

    fun from(): LocalDateTime = when (this) {
        WEEK -> LocalDateTime.now().minusWeeks(1)
        MONTH -> LocalDateTime.now().minusMonths(1)
        YEAR -> LocalDateTime.now().minusYears(1)
        ALL -> LocalDateTime.of(2000, 1, 1, 0, 0)
    }
}

/** Your listening at a glance: time, top artists and tracks for a period, and the year recap. */
@Composable
fun GerasProfileScreen(navController: NavController) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scheme = MaterialTheme.colorScheme
    val name by rememberPreference(AccountNameKey, "")
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    val from = remember(period) { period.from() }
    val now = remember(period) { LocalDateTime.now() }
    val playTime by remember(from) { database.getTotalPlayTimeInRange(from, now) }.collectAsState(initial = null)
    val artists by remember(from) { database.mostPlayedArtists(from, limit = 8) }.collectAsState(initial = emptyList())
    val songs by remember(from) { database.mostPlayedSongs(from, limit = 10) }.collectAsState(initial = emptyList())
    val liked by remember { database.likedSongsCount() }.collectAsState(initial = 0)
    val bottom = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 88.dp, bottom = bottom + 24.dp),
        ) {
            // Header.
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().gerasAppear(0)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .gerasRaised(RoundedCornerShape(50), scheme.primary, elevation = 18.dp)
                        .background(Brush.verticalGradient(listOf(scheme.primary, scheme.primaryContainer))),
                ) {
                    if (name.isNotBlank()) {
                        Text(name.trim().take(1).uppercase(), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary)
                    } else {
                        Icon(painterResource(R.drawable.person), null, tint = scheme.onPrimary, modifier = Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    name.ifBlank { stringResource(R.string.geras_profile_listener) },
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp),
                    color = scheme.onSurface,
                )
            }

            // Period switch.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .gerasAppear(1)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainer)
                    .padding(4.dp),
            ) {
                Period.entries.forEach { p ->
                    val selected = p == period
                    val bg by animateColorAsState(if (selected) scheme.surfaceContainerHighest else scheme.surfaceContainer, label = "period")
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .gerasBounce(0.93f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .clickable { period = p },
                    ) {
                        Text(stringResource(p.label), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) scheme.onSurface else scheme.onSurfaceVariant)
                    }
                }
            }

            // Stat tiles.
            val minutes = ((playTime ?: 0L) / 60_000L).toInt()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.gerasAppear(2).padding(horizontal = 16.dp)) {
                StatTile(minutes, stringResource(R.string.geras_profile_minutes), Modifier.weight(1f))
                StatTile(songs.size, stringResource(R.string.geras_profile_tracks), Modifier.weight(1f))
                StatTile(liked, stringResource(R.string.geras_profile_liked), Modifier.weight(1f))
            }

            if (artists.isNotEmpty()) {
                Header(stringResource(R.string.geras_profile_top_artists), Modifier.gerasAppear(3))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.gerasAppear(3).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                ) {
                    artists.forEachIndexed { i, a ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(88.dp)
                                .gerasBounce(0.93f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { navController.navigate("artist/${a.id}") },
                        ) {
                            Box {
                                Box(Modifier.size(84.dp).clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHigh)) {
                                    AsyncImage(a.artist.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.align(Alignment.BottomEnd).size(26.dp).clip(RoundedCornerShape(50)).background(scheme.primary),
                                ) { Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(a.artist.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (songs.isNotEmpty()) {
                Header(stringResource(R.string.geras_profile_top_tracks), Modifier.gerasAppear(4))
                Column(Modifier.gerasAppear(4)) {
                    songs.forEachIndexed { i, song -> TrackRow(i, song) { playTop(playerConnection, songs, i) } }
                }
            }
            if (artists.isEmpty() && songs.isEmpty()) {
                Text(
                    stringResource(R.string.geras_profile_empty),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp).gerasAppear(3),
                )
            }

            // Recap and detailed stats.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .gerasAppear(5)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .gerasBounce(0.96f)
                    .gerasRaised(RoundedCornerShape(24.dp), scheme.primaryContainer, elevation = 10.dp)
                    .background(Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.35f), scheme.primaryContainer)))
                    .clickable { navController.navigate("wrapped") }
                    .padding(18.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.geras_recap), style = MaterialTheme.typography.displaySmall.copy(fontSize = 22.sp), color = scheme.onPrimaryContainer)
                    Text(stringResource(R.string.geras_recap_hint), fontSize = 13.sp, color = scheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
                Icon(painterResource(R.drawable.navigate_next), null, tint = scheme.onPrimaryContainer)
            }
            Text(
                stringResource(R.string.geras_profile_details),
                color = scheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .gerasBounce(0.95f)
                    .clip(RoundedCornerShape(50))
                    .clickable { navController.navigate("stats") }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
        GerasTopBar(title = stringResource(R.string.geras_profile_title), onBack = navController::navigateUp)
    }
}

private fun playTop(playerConnection: com.metrolist.music.playback.PlayerConnection?, songs: List<Song>, index: Int) {
    playerConnection?.playQueue(ListQueue(title = null, items = songs.map { it.toMediaItem() }, startIndex = index))
}

@Composable
private fun Header(text: String, modifier: Modifier) {
    Text(
        text,
        fontSize = 20.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
    )
}

@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animated by animateIntAsState(value, tween(900), label = "stat")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainer)
            .padding(14.dp),
    ) {
        Text(
            "%,d".format(animated).replace(',', ' '),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = scheme.onSurface,
            maxLines = 1,
            style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
        Text(label, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrackRow(index: Int, song: Song, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .gerasBounce(0.97f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        AnimatedContent(index, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "rank") { i ->
            Text("${i + 1}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        }
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerHigh)) {
            AsyncImage(song.song.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.song.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists.joinToString { it.name }, fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
