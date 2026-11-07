package com.metrolist.music.geras.source.soundcloud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.geras.settings.GerasTopBar
import com.metrolist.music.geras.source.ItemKind
import com.metrolist.music.geras.source.soundcloud.SoundCloudSource.toMetadata
import com.metrolist.music.geras.ui.gerasRaised
import com.metrolist.music.playback.queues.ListQueue

/** SoundCloud playlist or artist: header, play/shuffle, track list. */
@Composable
fun SoundCloudCollectionScreen(navController: NavController, kind: ItemKind, url: String) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val scheme = MaterialTheme.colorScheme
    val state by produceState<Result<SoundCloudSource.Collection>?>(null, kind, url) {
        value = SoundCloudSource.loadCollection(kind, url)
    }
    val bottom = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

    fun play(collection: SoundCloudSource.Collection, start: Int, shuffle: Boolean) {
        val items = collection.tracks.map { it.toMetadata().toMediaItem() }
        if (items.isEmpty()) return
        playerConnection.playQueue(ListQueue(title = collection.title, items = if (shuffle) items.shuffled() else items, startIndex = if (shuffle) 0 else start))
    }

    Box(Modifier.fillMaxSize()) {
        when (val result = state) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.Center), strokeWidth = 2.dp)
            else -> result.fold(
                onSuccess = { collection ->
                    LazyColumn(contentPadding = PaddingValues(top = 96.dp, bottom = bottom + 16.dp)) {
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                                val shape = if (kind == ItemKind.ARTIST) RoundedCornerShape(50) else RoundedCornerShape(22.dp)
                                Box(Modifier.size(200.dp).gerasRaised(shape, scheme.surfaceContainerHigh, elevation = 18.dp)) {
                                    AsyncImage(collection.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(collection.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurface, textAlign = TextAlign.Center)
                                if (collection.subtitle.isNotBlank()) {
                                    Text(collection.subtitle, fontSize = 14.sp, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    "SoundCloud · " + stringResource(R.string.geras_sc_tracks_count, collection.tracks.size),
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PillButton(R.drawable.play, stringResource(R.string.geras_play), primary = true) { play(collection, 0, false) }
                                    PillButton(R.drawable.shuffle, stringResource(R.string.geras_shuffle), primary = false) { play(collection, 0, true) }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                        itemsIndexed(collection.tracks, key = { i, t -> "$i${t.id}" }) { index, track ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { play(collection, index, false) }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                            ) {
                                Box(Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerHigh)) {
                                    AsyncImage(track.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.subtitle, fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                },
                onFailure = {
                    Text(
                        stringResource(R.string.geras_sc_unavailable),
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                },
            )
        }
        GerasTopBar(title = "SoundCloud", onBack = navController::navigateUp)
    }
}

@Composable
private fun PillButton(icon: Int, text: String, primary: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(46.dp)
            .gerasRaised(RoundedCornerShape(50), if (primary) scheme.onSurface else scheme.surfaceContainerHigh, elevation = 8.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
    ) {
        Icon(painterResource(icon), null, tint = if (primary) scheme.surface else scheme.onSurface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, color = if (primary) scheme.surface else scheme.onSurface)
    }
}
