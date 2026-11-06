package com.metrolist.music.geras.player

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.geras.audio.GerasEqualizerSheet
import com.metrolist.music.geras.audio.GerasSpeedSheet
import com.metrolist.music.geras.lyrics.GerasLyricsColors
import com.metrolist.music.geras.lyrics.LocalGerasLyricsColors
import com.metrolist.music.geras.video.GerasClipView
import com.metrolist.music.geras.video.rememberClipSource
import com.metrolist.music.extensions.toggleRepeatMode
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.menu.PlayerMenu
import com.metrolist.music.ui.menu.TempoPitchDialog
import com.metrolist.music.ui.player.InlineLyricsView
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.ui.utils.ShowMediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class GerasDownloadState { NONE, ACTIVE, DONE }

@Immutable
data class GerasPlayerUi(
    val current: MediaMetadata?,
    val previous: MediaMetadata?,
    val next: MediaMetadata?,
    val artistAvatar: String?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val repeatMode: Int,
    val shuffle: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val queueLeft: Int,
    val liked: Boolean,
    val download: GerasDownloadState,
    /** 0..100, or negative when unknown. */
    val downloadPercent: Float,
    val speed: Float,
    val showLyrics: Boolean,
    val interactive: Boolean,
    val clipAvailable: Boolean = false,
    val showClip: Boolean = false,
)

class GerasPlayerActions(
    val onSearch: () -> Unit = {},
    val onQueue: () -> Unit = {},
    val onMenu: () -> Unit = {},
    val onArtist: (String) -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onRepeat: () -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onLike: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onToggleLyrics: () -> Unit = {},
    val onEqualizer: () -> Unit = {},
    val onSpeed: () -> Unit = {},
    val onClip: () -> Unit = {},
)

/**
 * Portrait full-screen player. Hosted inside Metrolist's BottomSheetPlayer, which still owns
 * the sheet, the queue sheet, casting and position polling; this wires player state into
 * the stateless [GerasPlayerLayout].
 */
@Composable
fun GerasPlayerContent(
    state: BottomSheetState,
    queueState: BottomSheetState,
    navController: NavController,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    onSeek: (Long) -> Unit,
    themeMode: GerasThemeMode,
    isListenTogetherGuest: Boolean,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val downloadUtil = LocalDownloadUtil.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val shuffle by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()

    val previous = remember(queueWindows, currentWindowIndex) {
        queueWindows.getOrNull(currentWindowIndex - 1)?.mediaItem?.metadata
    }
    val next = remember(queueWindows, currentWindowIndex) {
        queueWindows.getOrNull(currentWindowIndex + 1)?.mediaItem?.metadata
    }

    val avatar = rememberArtistAvatar(mediaMetadata?.artists?.firstOrNull()?.id)
    val speed = rememberPlaybackSpeed(player)
    val (download, downloadPercent) = rememberDownloadProgress(mediaMetadata?.id)
    val (settledColors, colors) = rememberGerasPalette(mediaMetadata?.thumbnailUrl, themeMode)
    val lyricsColors = remember(settledColors) {
        GerasLyricsColors(
            active = settledColors.content,
            inactive = settledColors.content.copy(alpha = 0.3f),
            backing = settledColors.accent,
            accent = settledColors.accent,
        )
    }

    var showSpeedSheet by rememberSaveable { mutableStateOf(false) }
    var showEqSheet by rememberSaveable { mutableStateOf(false) }

    // Clip (music video) mode. For audio-only tracks the current queue item is swapped for its
    // video counterpart so picture and sound share one timeline, like YTM's Song/Video switch.
    var clipMode by rememberSaveable { mutableStateOf(false) }
    var swappedFrom by remember { mutableStateOf<MediaMetadata?>(null) }
    val clipSource = rememberClipSource(mediaMetadata)
    val swapTo: (MediaMetadata) -> Unit = { target ->
        val index = player.currentMediaItemIndex
        val pos = player.currentPosition
        if (index >= 0) {
            player.replaceMediaItem(index, target.toMediaItem())
            player.seekTo(index, pos)
        }
    }
    LaunchedEffect(mediaMetadata?.id) {
        val original = swappedFrom ?: return@LaunchedEffect
        val current = mediaMetadata ?: return@LaunchedEffect
        if (current.id != original.id && !current.isVideoSong) swappedFrom = null
        if (current.isVideoSong && current.title != original.title) swappedFrom = null
    }
    LaunchedEffect(clipMode, clipSource) {
        val source = clipSource ?: return@LaunchedEffect
        val current = mediaMetadata ?: return@LaunchedEffect
        if (clipMode && source.isCounterpart && !isListenTogetherGuest) {
            swappedFrom = current
            swapTo(current.copy(id = source.videoId, musicVideoType = MUSIC_VIDEO_TYPE_OMV, setVideoId = null))
        }
    }

    val downloadedText = stringResource(R.string.geras_already_downloaded)
    val openFromPlayer: (String) -> Unit = { route ->
        state.collapseSoft()
        navController.navigate(route)
    }

    if (showSpeedSheet) GerasSpeedSheet(colors = colors, onDismiss = { showSpeedSheet = false })
    if (showEqSheet) {
        GerasEqualizerSheet(
            colors = colors,
            onDismiss = { showEqSheet = false },
            onOpenAutoEq = {
                showEqSheet = false
                openFromPlayer("equalizer")
            },
        )
    }

    val ui = GerasPlayerUi(
        current = mediaMetadata,
        previous = previous,
        next = next,
        artistAvatar = avatar,
        isPlaying = isPlaying,
        isBuffering = playbackState == Player.STATE_BUFFERING,
        repeatMode = repeatMode,
        shuffle = shuffle,
        canSkipPrevious = canSkipPrevious,
        canSkipNext = canSkipNext,
        queueLeft = (queueWindows.size - currentWindowIndex - 1).coerceAtLeast(0),
        liked = if (currentSong?.song?.isEpisode == true) currentSong?.song?.inLibrary != null else currentSong?.song?.liked == true,
        download = download,
        downloadPercent = downloadPercent,
        speed = speed,
        showLyrics = showLyrics,
        interactive = !isListenTogetherGuest,
        clipAvailable = clipSource != null || swappedFrom != null,
        showClip = clipMode && mediaMetadata?.isVideoSong == true,
    )

    val actions = remember(mediaMetadata, download, playbackState, clipMode, swappedFrom) {
        GerasPlayerActions(
            onSearch = { openFromPlayer(Screens.Search.route) },
            onQueue = { queueState.expandSoft() },
            onMenu = {
                val metadata = mediaMetadata ?: return@GerasPlayerActions
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = metadata,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = { bottomSheetPageState.show { ShowMediaInfo(metadata.id) } },
                        onDismiss = menuState::dismiss,
                    )
                }
            },
            onArtist = { id -> openFromPlayer("artist/$id") },
            onSeek = onSeek,
            onPlayPause = {
                if (playbackState == Player.STATE_ENDED) {
                    player.seekTo(0, 0)
                    player.playWhenReady = true
                } else {
                    playerConnection.togglePlayPause()
                }
            },
            onPrevious = playerConnection::seekToPrevious,
            onNext = playerConnection::seekToNext,
            onRepeat = { player.toggleRepeatMode() },
            onShuffle = { player.shuffleModeEnabled = !player.shuffleModeEnabled },
            onLike = playerConnection::toggleLike,
            onDownload = {
                val metadata = mediaMetadata ?: return@GerasPlayerActions
                when (download) {
                    GerasDownloadState.DONE -> Toast.makeText(context, downloadedText, Toast.LENGTH_SHORT).show()
                    GerasDownloadState.ACTIVE ->
                        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, metadata.id, false)
                    GerasDownloadState.NONE -> downloadUtil.download(metadata)
                }
            },
            onToggleLyrics = onToggleLyrics,
            onEqualizer = { showEqSheet = true },
            onSpeed = { showSpeedSheet = true },
            onClip = {
                if (clipMode) {
                    swappedFrom?.let { original ->
                        if (mediaMetadata?.isVideoSong == true) swapTo(original)
                    }
                    swappedFrom = null
                    clipMode = false
                } else {
                    clipMode = true
                }
            },
        )
    }

    GerasPlayerLayout(
        ui = ui,
        colors = colors,
        actions = actions,
        positionProvider = positionProvider,
        durationProvider = durationProvider,
        sheetProgress = { state.progress },
        clip = {
            mediaMetadata?.let { GerasClipView(videoId = it.id, mainPlayer = player, colors = colors, modifier = Modifier.fillMaxSize()) }
        },
        lyrics = {
            androidx.compose.runtime.CompositionLocalProvider(LocalGerasLyricsColors provides lyricsColors) {
            InlineLyricsView(
                mediaMetadata = mediaMetadata,
                showLyrics = true,
                positionProvider = positionProvider,
            )
            }
        },
        modifier = modifier,
    )
}

@Composable
fun GerasPlayerLayout(
    ui: GerasPlayerUi,
    colors: GerasPlayerColors,
    actions: GerasPlayerActions,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    sheetProgress: () -> Float,
    lyrics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    clip: @Composable () -> Unit = {},
) {
    var scrubPosition by remember { mutableStateOf<Long?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to colors.backgroundTop, 0.55f to colors.background, 1f to colors.background)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal))
                .padding(horizontal = 20.dp),
        ) {
            TopBar(colors, ui, actions)

            Spacer(Modifier.height(16.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val pane = when {
                    ui.showLyrics -> 1
                    ui.showClip -> 2
                    else -> 0
                }
                AnimatedContent(
                    targetState = pane,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "coverLyricsClip",
                ) { target ->
                    if (target == 1) {
                        lyrics()
                    } else if (target == 2) {
                        clip()
                    } else {
                        GerasCover(
                            current = ui.current,
                            previous = ui.previous,
                            next = ui.next,
                            canSkipPrevious = ui.canSkipPrevious,
                            canSkipNext = ui.canSkipNext,
                            enabled = ui.interactive,
                            colors = colors,
                            onSkipNext = actions.onNext,
                            onSkipPrevious = actions.onPrevious,
                            onSeekBy = { delta ->
                                val target = (positionProvider() + delta).coerceIn(0L, durationProvider().coerceAtLeast(0L))
                                actions.onSeek(target)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // Grows out of the mini player while the sheet is dragged up.
                                    val eased = FastOutSlowInEasing.transform(sheetProgress().coerceIn(0f, 1f))
                                    val scale = 0.35f + 0.65f * eased
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0.1f, 1f)
                                },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            TitleBlock(ui.current, ui.artistAvatar, colors, actions.onArtist)

            Spacer(Modifier.height(14.dp))

            GerasSeekBar(
                positionProvider = positionProvider,
                durationProvider = durationProvider,
                onSeek = actions.onSeek,
                enabled = ui.interactive,
                colors = colors,
                onScrub = { scrubPosition = it },
            )
            GerasTimeRow(
                positionProvider = positionProvider,
                durationProvider = durationProvider,
                scrubPosition = scrubPosition,
                next = ui.next,
                colors = colors,
                onNextClick = actions.onQueue,
            )

            Spacer(Modifier.height(16.dp))

            ControlsCard(ui, colors, actions)

            Spacer(Modifier.height(14.dp))

            BottomBar(ui, colors, actions)

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Capsule(
    colors: GerasPlayerColors,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surface)
            .padding(horizontal = 4.dp),
    ) { content() }
}

@Composable
private fun RoundIcon(
    icon: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun TopBar(colors: GerasPlayerColors, ui: GerasPlayerUi, actions: GerasPlayerActions) {
    val queueLeft = ui.queueLeft
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = ui.clipAvailable,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (ui.showClip) colors.accent else colors.surface)
                    .clickable(enabled = ui.interactive, onClick = actions.onClip),
            ) {
                Icon(
                    painter = painterResource(R.drawable.slow_motion_video),
                    contentDescription = stringResource(R.string.geras_clip),
                    tint = if (ui.showClip) colors.onAccent else colors.content,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.surface),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = actions.onSearch)
                    .padding(start = 14.dp, end = 4.dp),
            ) {
                Icon(painterResource(R.drawable.search), null, tint = colors.content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search), color = colors.contentMuted, fontSize = 14.sp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = actions.onQueue)
                    .padding(start = 4.dp, end = 10.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(22.dp)
                        .widthIn(min = 22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.accent)
                        .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = queueLeft.toString(),
                        color = colors.onAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        RoundIcon(
            icon = R.drawable.more_horiz,
            tint = colors.content,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(50))
                .background(colors.surface),
            size = 40.dp,
            iconSize = 20.dp,
            onClick = actions.onMenu,
        )
    }
}

@Composable
private fun TitleBlock(
    mediaMetadata: MediaMetadata?,
    avatar: String?,
    colors: GerasPlayerColors,
    onArtistClick: (String) -> Unit,
) {
    val artistId = mediaMetadata?.artists?.firstOrNull()?.id
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = mediaMetadata?.title.orEmpty(),
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "title",
        ) { title ->
            Text(
                text = title,
                color = colors.content,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(initialDelayMillis = 2500, velocity = 30.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(enabled = artistId != null) { artistId?.let(onArtistClick) }
                .padding(end = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surface),
            ) {
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                color = colors.contentMuted,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ControlsCard(ui: GerasPlayerUi, colors: GerasPlayerColors, actions: GerasPlayerActions) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(colors.surface)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        RoundIcon(
            icon = if (ui.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
            tint = if (ui.repeatMode == Player.REPEAT_MODE_OFF) colors.contentMuted else colors.accent,
            enabled = ui.interactive,
            onClick = actions.onRepeat,
        )
        RoundIcon(
            R.drawable.skip_previous, colors.content, actions.onPrevious,
            size = 52.dp, iconSize = 30.dp, enabled = ui.interactive && ui.canSkipPrevious,
        )

        val playScale by animateFloatAsState(if (ui.isPlaying) 1f else 0.94f, label = "playScale")
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                }
                .clip(RoundedCornerShape(50))
                .background(colors.content)
                .clickable(enabled = ui.interactive, onClick = actions.onPlayPause),
        ) {
            AnimatedContent(
                targetState = ui.isPlaying,
                transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
                label = "playPause",
            ) { playing ->
                Icon(
                    painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = colors.background,
                    modifier = Modifier.size(34.dp),
                )
            }
            if (ui.isBuffering) {
                CircularProgressIndicator(
                    color = colors.background.copy(alpha = 0.5f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(66.dp),
                )
            }
        }

        RoundIcon(
            R.drawable.skip_next, colors.content, actions.onNext,
            size = 52.dp, iconSize = 30.dp, enabled = ui.interactive && ui.canSkipNext,
        )
        RoundIcon(
            icon = R.drawable.shuffle,
            tint = if (ui.shuffle) colors.accent else colors.contentMuted,
            enabled = ui.interactive,
            onClick = actions.onShuffle,
        )
    }
}

@Composable
private fun BottomBar(ui: GerasPlayerUi, colors: GerasPlayerColors, actions: GerasPlayerActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Capsule(colors) {
            RoundIcon(
                icon = if (ui.liked) R.drawable.favorite else R.drawable.favorite_border,
                tint = if (ui.liked) MaterialTheme.colorScheme.error else colors.content,
                onClick = actions.onLike,
            )
            DownloadButton(ui.download, ui.downloadPercent, colors, actions.onDownload)
        }
        Spacer(Modifier.weight(1f))
        Capsule(colors) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (ui.showLyrics) colors.accent else Color.Transparent)
                    .clickable(onClick = actions.onToggleLyrics),
            ) {
                Text(
                    text = "T",
                    color = if (ui.showLyrics) colors.onAccent else colors.content,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            RoundIcon(R.drawable.equalizer, colors.content, actions.onEqualizer)
            SpeedChip(ui.speed, colors, actions.onSpeed)
        }
    }
}

@Composable
private fun SpeedChip(speed: Float, colors: GerasPlayerColors, onClick: () -> Unit) {
    val changed = kotlin.math.abs(speed - 1f) > 0.001f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 2.dp, end = 4.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (changed) colors.accent else colors.content.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
    ) {
        Text(
            text = "%.2fx".format(java.util.Locale.US, speed),
            color = if (changed) colors.onAccent else colors.content,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DownloadButton(
    state: GerasDownloadState,
    percent: Float,
    colors: GerasPlayerColors,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        when (state) {
            GerasDownloadState.ACTIVE -> {
                if (percent < 0f) {
                    CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                } else {
                    val animated by animateFloatAsState((percent / 100f).coerceIn(0f, 1f), label = "dl")
                    CircularProgressIndicator(
                        progress = { animated },
                        color = colors.accent,
                        trackColor = colors.contentMuted.copy(alpha = 0.2f),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.dp)).background(colors.accent))
            }
            GerasDownloadState.DONE ->
                Icon(painterResource(R.drawable.offline), null, tint = colors.accent, modifier = Modifier.size(22.dp))
            GerasDownloadState.NONE ->
                Icon(painterResource(R.drawable.download), null, tint = colors.content, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun rememberPlaybackSpeed(player: Player): Float {
    var speed by remember(player) { mutableFloatStateOf(player.playbackParameters.speed) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                speed = playbackParameters.speed
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return speed
}

@Composable
private fun rememberDownloadProgress(id: String?): Pair<GerasDownloadState, Float> {
    val downloadUtil = LocalDownloadUtil.current
    val download by downloadUtil.getDownload(id.orEmpty()).collectAsStateWithLifecycle(initialValue = null)
    val state = when (download?.state) {
        Download.STATE_COMPLETED -> GerasDownloadState.DONE
        Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> GerasDownloadState.ACTIVE
        else -> GerasDownloadState.NONE
    }
    var percent by remember(id) { mutableFloatStateOf(-1f) }
    LaunchedEffect(id, state) {
        while (state == GerasDownloadState.ACTIVE) {
            val p = downloadUtil.downloadManager.currentDownloads
                .firstOrNull { it.request.id == id }
                ?.percentDownloaded
            percent = if (p == null || p == C.PERCENTAGE_UNSET.toFloat()) -1f else p
            delay(400)
        }
    }
    return state to percent
}

private val avatarCache = ConcurrentHashMap<String, String>()

@Composable
private fun rememberArtistAvatar(artistId: String?): String? {
    val database = LocalDatabase.current
    val avatar by produceState(initialValue = artistId?.let { avatarCache[it] }, artistId) {
        if (artistId == null) {
            value = null
            return@produceState
        }
        avatarCache[artistId]?.let {
            value = it
            return@produceState
        }
        val url = withContext(Dispatchers.IO) {
            runCatching { database.artist(artistId).first()?.artist?.thumbnailUrl }.getOrNull()
                ?: runCatching { YouTube.artist(artistId).getOrNull()?.artist?.thumbnail }.getOrNull()
        }
        if (url != null) avatarCache[artistId] = url
        value = url
    }
    return avatar
}
