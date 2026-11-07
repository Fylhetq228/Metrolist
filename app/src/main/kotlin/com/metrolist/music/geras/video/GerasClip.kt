package com.metrolist.music.geras.video

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import com.metrolist.music.geras.player.GerasPlayerColors
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

private val counterpartCache = ConcurrentHashMap<String, String>()
private const val NO_COUNTERPART = ""

/**
 * Video id that can be shown for [metadata]: the track itself when it is already a music
 * video, otherwise its YTM counterpart (looked up once and cached). Null when there is none.
 */
@Composable
fun rememberClipSource(metadata: MediaMetadata?): ClipSource? {
    val source by produceState<ClipSource?>(initialValue = null, metadata?.id) {
        value = null
        if (metadata == null || metadata.isEpisode || metadata.uploadEntityId != null) return@produceState
        if (com.metrolist.music.geras.source.soundcloud.SoundCloudSource.isScId(metadata.id)) return@produceState
        if (metadata.isVideoSong) {
            value = ClipSource(metadata.id, isCounterpart = false)
            return@produceState
        }
        val cached = counterpartCache[metadata.id]
        val counterpart = cached ?: withContext(Dispatchers.IO) {
            YouTube.musicVideoCounterpart(metadata.id).getOrNull()
        }.also { counterpartCache[metadata.id] = it ?: NO_COUNTERPART }
        value = counterpart?.takeIf { it != NO_COUNTERPART }?.let { ClipSource(it, isCounterpart = true) }
    }
    return source
}

data class ClipSource(val videoId: String, val isCounterpart: Boolean)

private sealed interface ClipState {
    data object Loading : ClipState
    data object Ready : ClipState
    data class Failed(val message: String?) : ClipState
}

/**
 * Muted video-only player kept in step with the main (audio) player. Audio, effects and the
 * queue stay in MusicService; this only draws the picture while the clip view is on screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun GerasClipView(
    videoId: String,
    mainPlayer: Player,
    colors: GerasPlayerColors,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipPlayer = remember {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
            }
    }
    DisposableEffect(clipPlayer) { onDispose { clipPlayer.release() } }

    var state by remember(videoId) { mutableStateOf<ClipState>(ClipState.Loading) }
    var aspect by remember(videoId) { mutableFloatStateOf(16f / 9f) }

    DisposableEffect(clipPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) state = ClipState.Ready
            }

            override fun onPlayerError(error: PlaybackException) {
                state = ClipState.Failed(error.message)
            }
        }
        clipPlayer.addListener(listener)
        onDispose { clipPlayer.removeListener(listener) }
    }

    LaunchedEffect(videoId) {
        state = ClipState.Loading
        val stream = withContext(Dispatchers.IO) { InnerTubeXPlayer.clipStream(videoId) }
            .getOrElse {
                state = ClipState.Failed(it.message)
                return@LaunchedEffect
            }
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(stream.headers)
            .setAllowCrossProtocolRedirects(true)
        val source = ProgressiveMediaSource.Factory(dataSource)
            .createMediaSource(MediaItem.fromUri(stream.url))
        stream.width?.let { w -> stream.height?.takeIf { it > 0 }?.let { h -> aspect = w.toFloat() / h } }
        clipPlayer.setMediaSource(source, mainPlayer.currentPosition)
        clipPlayer.prepare()
    }

    // Follow the main player: play state, speed, and position whenever drift gets noticeable.
    LaunchedEffect(videoId, clipPlayer) {
        while (isActive) {
            val mainPlaying = mainPlayer.isPlaying
            if (clipPlayer.playWhenReady != mainPlaying) clipPlayer.playWhenReady = mainPlaying
            val speed = mainPlayer.playbackParameters.speed
            if (abs(clipPlayer.playbackParameters.speed - speed) > 0.001f) {
                clipPlayer.playbackParameters = PlaybackParameters(speed)
            }
            if (clipPlayer.playbackState == Player.STATE_READY) {
                val target = mainPlayer.currentPosition
                val drift = clipPlayer.currentPosition - target
                val tolerance = if (mainPlaying) 250L else 60L
                if (abs(drift) > tolerance) clipPlayer.seekTo(target + if (mainPlaying) 120L else 0L)
            }
            delay(if (mainPlaying) 400L else 150L)
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.coerceIn(0.5f, 2.4f))
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx -> TextureView(ctx).also(clipPlayer::setVideoTextureView) },
                onRelease = { clipPlayer.clearVideoTextureView(it) },
                modifier = Modifier.fillMaxSize(),
            )
            when (val s = state) {
                ClipState.Loading -> CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
                is ClipState.Failed -> Text(
                    text = stringResource(R.string.geras_clip_unavailable),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
                ClipState.Ready -> Unit
            }
        }
    }
}
