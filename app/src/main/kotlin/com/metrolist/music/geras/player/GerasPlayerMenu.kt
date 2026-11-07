package com.metrolist.music.geras.player

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.geras.source.soundcloud.SoundCloudSource
import com.metrolist.music.geras.ui.GerasActionTile
import com.metrolist.music.geras.ui.GerasMenuCard
import com.metrolist.music.geras.ui.GerasMenuRow
import com.metrolist.music.geras.ui.gerasBounce
import com.metrolist.music.geras.ui.gerasPop
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.menu.AddToPlaylistDialog

/** Track menu of the Geras player: only what makes sense while listening. */
@Composable
fun GerasPlayerMenu(
    mediaMetadata: MediaMetadata,
    playerBottomSheetState: BottomSheetState,
    onShowDetails: () -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scheme = MaterialTheme.colorScheme
    val song by database.song(mediaMetadata.id).collectAsStateWithLifecycle(initialValue = null)
    val liked = song?.song?.liked == true
    val inLibrary = song?.song?.inLibrary != null
    var choosePlaylist by rememberSaveable { mutableStateOf(false) }
    val isSc = SoundCloudSource.isScId(mediaMetadata.id)
    val waveStarted = stringResource(R.string.geras_wave_started)

    AddToPlaylistDialog(
        isVisible = choosePlaylist,
        onGetSong = {
            database.withTransaction { insert(mediaMetadata) }
            listOf(mediaMetadata.id)
        },
        onGetSongIds = { listOf(mediaMetadata.id) },
        onDismiss = { choosePlaylist = false },
    )

    fun open(route: String) {
        playerBottomSheetState.collapseSoft()
        navController.navigate(route)
        onDismiss()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(scheme.surfaceContainerHigh)) {
                AsyncImage(mediaMetadata.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(mediaMetadata.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(mediaMetadata.artists.joinToString { it.name }, fontSize = 14.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .gerasPop(liked)
                    .gerasBounce(0.85f)
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHigh)
                    .clickable { playerConnection.toggleLike() },
            ) {
                Icon(
                    painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                    null,
                    tint = if (liked) scheme.error else scheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            GerasActionTile(
                icon = { Icon(painterResource(R.drawable.geras_wave), null) },
                text = stringResource(R.string.geras_wave_by_track),
                onClick = {
                    playerConnection.startRadioSeamlessly()
                    Toast.makeText(context, waveStarted, Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                background = scheme.primary,
                content = scheme.onPrimary,
                modifier = Modifier.weight(1f),
            )
            GerasActionTile(
                icon = { Icon(painterResource(R.drawable.playlist_add), null) },
                text = stringResource(R.string.geras_menu_to_playlist),
                onClick = { choosePlaylist = true },
                modifier = Modifier.weight(1f),
            )
            GerasActionTile(
                icon = { Icon(painterResource(R.drawable.share), null) },
                text = stringResource(R.string.geras_menu_share),
                onClick = {
                    val link = if (isSc) {
                        "https://api.soundcloud.com/tracks/" + mediaMetadata.id.removePrefix(SoundCloudSource.ID_PREFIX)
                    } else {
                        "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${mediaMetadata.title} — ${mediaMetadata.artists.joinToString { it.name }}\n$link")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))

        val rows = buildList {
            mediaMetadata.artists.firstOrNull { it.id != null }?.let { artist ->
                add(
                    GerasMenuRow(
                        icon = { Icon(painterResource(R.drawable.artist), null) },
                        title = { Text(stringResource(R.string.geras_menu_artist)) },
                        description = { Text(artist.name) },
                        trailing = null,
                        onClick = { open("artist/${artist.id}") },
                    ),
                )
            }
            mediaMetadata.album?.let { album ->
                add(
                    GerasMenuRow(
                        icon = { Icon(painterResource(R.drawable.album), null) },
                        title = { Text(stringResource(R.string.geras_menu_album)) },
                        description = { Text(album.title) },
                        trailing = null,
                        onClick = { open("album/${album.id}") },
                    ),
                )
            }
            add(
                GerasMenuRow(
                    icon = { Icon(painterResource(if (inLibrary) R.drawable.library_add_check else R.drawable.library_add), null) },
                    title = { Text(stringResource(if (inLibrary) R.string.geras_menu_remove_library else R.string.geras_menu_add_library)) },
                    description = null,
                    trailing = null,
                    onClick = {
                        playerConnection.toggleLibrary()
                        onDismiss()
                    },
                ),
            )
        }
        GerasMenuCard(
            rows + GerasMenuRow(
                icon = { Icon(painterResource(R.drawable.geras_dislike), null) },
                title = { Text(stringResource(R.string.geras_menu_dislike)) },
                description = { Text(stringResource(R.string.geras_menu_dislike_hint)) },
                trailing = null,
                onClick = {
                    com.metrolist.music.geras.wave.GerasWave.dislike(mediaMetadata.id, mediaMetadata.artists.firstOrNull()?.name)
                    if (playerConnection.player.hasNextMediaItem()) playerConnection.seekToNext()
                    Toast.makeText(context, R.string.geras_dislike_done, Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            ),
        )
        Spacer(Modifier.height(12.dp))
        GerasMenuCard(
            listOf(
                GerasMenuRow(
                    icon = { Icon(painterResource(R.drawable.bedtime), null) },
                    title = { Text(stringResource(R.string.geras_sleep_title)) },
                    description = null,
                    trailing = null,
                    onClick = {
                        onDismiss()
                        onSleep()
                    },
                ),
                GerasMenuRow(
                    icon = { Icon(painterResource(R.drawable.speed), null) },
                    title = { Text(stringResource(R.string.geras_settings_speed)) },
                    description = { Text(stringResource(R.string.geras_menu_speed_hint)) },
                    trailing = null,
                    onClick = {
                        onDismiss()
                        onSpeed()
                    },
                ),
                GerasMenuRow(
                    icon = { Icon(painterResource(R.drawable.info), null) },
                    title = { Text(stringResource(R.string.geras_menu_details)) },
                    description = null,
                    trailing = null,
                    onClick = {
                        onDismiss()
                        onShowDetails()
                    },
                ),
            ),
        )
    }
}
