package com.metrolist.music.geras.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.move
import com.metrolist.music.extensions.toggleRepeatMode
import com.metrolist.music.geras.ui.GerasPill
import com.metrolist.music.geras.ui.gerasBounce
import com.metrolist.music.ui.component.BottomSheet
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.utils.makeTimeString
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Queue sheet in the Geras style: drag to reorder, swipe to remove, tap to jump. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GerasQueue(state: BottomSheetState, modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val scheme = MaterialTheme.colorScheme
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val shuffle by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeat by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val title by playerConnection.queueTitle.collectAsStateWithLifecycle()

    val windows = remember { mutableStateListOf<Timeline.Window>() }
    LaunchedEffect(queueWindows) {
        windows.clear()
        windows.addAll(queueWindows)
    }
    val currentUid = queueWindows.getOrNull(currentIndex)?.uid
    val listState = rememberLazyListState()
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        dragInfo = (dragInfo?.first ?: from.index) to to.index
        windows.move(from.index.coerceIn(0, windows.lastIndex), to.index.coerceIn(0, windows.lastIndex))
    }
    LaunchedEffect(reorder.isAnyItemDragging) {
        if (!reorder.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                val f = from.coerceIn(0, queueWindows.lastIndex)
                val t = to.coerceIn(0, queueWindows.lastIndex)
                if (!player.shuffleModeEnabled) {
                    player.moveMediaItem(f, t)
                } else {
                    player.setShuffleOrder(
                        DefaultShuffleOrder(queueWindows.map { it.firstPeriodIndex }.toMutableList().move(f, t).toIntArray(), System.currentTimeMillis()),
                    )
                }
            }
            dragInfo = null
        }
    }
    // Open on the current track.
    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded && currentIndex >= 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
    }

    val totalSec = queueWindows.drop(currentIndex.coerceAtLeast(0)).sumOf { it.mediaItem.metadata?.duration?.coerceAtLeast(0) ?: 0 }

    BottomSheet(
        state = state,
        modifier = modifier,
        background = { Box(Modifier.fillMaxSize().background(scheme.surfaceContainer)) },
        collapsedContent = {},
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(scheme.surfaceContainer)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(scheme.onSurface.copy(alpha = 0.25f)))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.geras_queue_title), style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp), color = scheme.onSurface)
                    Text(
                        listOfNotNull(title, stringResource(R.string.geras_sc_tracks_count, (queueWindows.size - currentIndex.coerceAtLeast(0)).coerceAtLeast(0)), makeTimeString(totalSec * 1000L))
                            .joinToString(" · "),
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .gerasBounce(0.9f)
                        .size(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surfaceContainerHigh)
                        .clickable { state.collapseSoft() },
                ) {
                    Icon(painterResource(R.drawable.expand_more), null, tint = scheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                GerasPill(stringResource(R.string.geras_queue_shuffle), shuffle, { player.shuffleModeEnabled = !player.shuffleModeEnabled })
                GerasPill(
                    stringResource(
                        when (repeat) {
                            Player.REPEAT_MODE_ONE -> R.string.geras_queue_repeat_one
                            Player.REPEAT_MODE_ALL -> R.string.geras_queue_repeat_all
                            else -> R.string.geras_queue_repeat
                        },
                    ),
                    repeat != Player.REPEAT_MODE_OFF,
                    { player.toggleRepeatMode() },
                )
                GerasPill(stringResource(R.string.geras_queue_clear), false, {
                    val from = player.currentMediaItemIndex + 1
                    if (from < player.mediaItemCount) player.removeMediaItems(from, player.mediaItemCount)
                })
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(windows, key = { _, w -> w.uid.hashCode() }) { index, window ->
                    ReorderableItem(reorder, key = window.uid.hashCode()) { dragging ->
                        val isCurrent = window.uid == currentUid
                        val played = currentIndex >= 0 && index < currentIndex && !dragging
                        val dismiss = rememberSwipeToDismissBoxState()
                        LaunchedEffect(dismiss.currentValue) {
                            if (dismiss.currentValue != SwipeToDismissBoxValue.Settled && !isCurrent) {
                                player.removeMediaItem(window.firstPeriodIndex)
                            }
                        }
                        SwipeToDismissBox(
                            state = dismiss,
                            enableDismissFromStartToEnd = false,
                            gesturesEnabled = !isCurrent,
                            backgroundContent = {
                                Box(
                                    contentAlignment = Alignment.CenterEnd,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp, vertical = 3.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFFE5645A).copy(alpha = 0.85f))
                                        .padding(end = 20.dp),
                                ) {
                                    Icon(painterResource(R.drawable.delete), null, tint = Color.White)
                                }
                            },
                        ) {
                            val bg by animateColorAsState(
                                when {
                                    dragging -> scheme.surfaceContainerHighest
                                    isCurrent -> scheme.primary.copy(alpha = 0.14f)
                                    else -> scheme.surfaceContainer
                                },
                                label = "qbg",
                            )
                            val meta = window.mediaItem.metadata
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                                    .graphicsLayer {
                                        val s = if (dragging) 1.03f else 1f
                                        scaleX = s
                                        scaleY = s
                                        alpha = if (played) 0.45f else 1f
                                    }
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(bg)
                                    .clickable {
                                        if (isCurrent) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            player.seekToDefaultPosition(window.firstPeriodIndex)
                                            player.playWhenReady = true
                                        }
                                    }
                                    .padding(8.dp),
                            ) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerHigh)) {
                                    AsyncImage(meta?.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    if (isCurrent) {
                                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                                            Icon(painterResource(R.drawable.graphic_eq), null, tint = Color.White, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        meta?.title.orEmpty(),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) scheme.primary else scheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        (meta?.artists?.joinToString { it.name }.orEmpty()) + (meta?.duration?.takeIf { it > 0 }?.let { " · " + makeTimeString(it * 1000L) } ?: ""),
                                        fontSize = 13.sp,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(40.dp).draggableHandle(),
                                ) {
                                    Icon(painterResource(R.drawable.drag_handle), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
