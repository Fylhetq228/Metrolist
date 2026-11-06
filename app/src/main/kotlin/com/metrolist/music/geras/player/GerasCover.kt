package com.metrolist.music.geras.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Square artwork that follows the finger horizontally: release past a quarter of its width
 * (or fling) to skip, neighbours slide in from the sides. Double tap on the left/right
 * thirds seeks by [seekStepMs].
 */
@Composable
fun GerasCover(
    current: MediaMetadata?,
    previous: MediaMetadata?,
    next: MediaMetadata?,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    enabled: Boolean,
    colors: GerasPlayerColors,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
    seekStepMs: Long = 10_000L,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offset = remember { Animatable(0f) }
    val latestOnNext by rememberUpdatedState(onSkipNext)
    val latestOnPrev by rememberUpdatedState(onSkipPrevious)
    val latestCanNext by rememberUpdatedState(canSkipNext && next != null)
    val latestCanPrev by rememberUpdatedState(canSkipPrevious)

    LaunchedEffect(current?.id) { offset.snapTo(0f) }

    var seekHint by remember { mutableIntStateOf(0) }
    var seekHintKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekHintKey) {
        if (seekHint != 0) {
            delay(650)
            seekHint = 0
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, maxHeight.value).dp
        val sidePx = with(density) { side.toPx() }
        val gapPx = with(density) { 24.dp.toPx() }
        val step = sidePx + gapPx

        Box(
            modifier = Modifier
                .size(side)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onHorizontalDrag = { change, amount ->
                            tracker.addPointerInputChange(change)
                            val blocked = (amount < 0 && !latestCanNext && offset.value <= 0f) ||
                                (amount > 0 && !latestCanPrev && offset.value >= 0f)
                            val applied = if (blocked) amount * 0.25f else amount
                            scope.launch { offset.snapTo((offset.value + applied).coerceIn(-step, step)) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().x
                            val value = offset.value
                            val goNext = latestCanNext && (value < -sidePx * 0.25f || (velocity < -1200f && value < 0f))
                            val goPrev = latestCanPrev && (value > sidePx * 0.25f || (velocity > 1200f && value > 0f))
                            scope.launch {
                                when {
                                    goNext -> {
                                        offset.animateTo(-step, tween(220))
                                        latestOnNext()
                                        delay(700)
                                        if (offset.value != 0f) offset.snapTo(0f)
                                    }
                                    goPrev -> {
                                        offset.animateTo(step, tween(220))
                                        latestOnPrev()
                                        delay(700)
                                        if (offset.value != 0f) offset.snapTo(0f)
                                    }
                                    else -> offset.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                    )
                                }
                            }
                        },
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = { pos ->
                            val third = size.width / 3f
                            val dir = when {
                                pos.x < third -> -1
                                pos.x > third * 2 -> 1
                                else -> 0
                            }
                            if (dir != 0) {
                                onSeekBy(dir * seekStepMs)
                                seekHint = dir
                                seekHintKey++
                            }
                        },
                    )
                },
        ) {
            CoverImage(previous, colors, Modifier.graphicsLayer {
                translationX = offset.value - step
                alpha = (offset.value / sidePx * 3f).coerceIn(0f, 1f)
            })
            CoverImage(
                current,
                colors,
                Modifier.graphicsLayer {
                    translationX = offset.value
                    val f = 1f - (abs(offset.value) / step).coerceIn(0f, 1f) * 0.08f
                    scaleX = f
                    scaleY = f
                },
                elevated = true,
            )
            CoverImage(next, colors, Modifier.graphicsLayer {
                translationX = offset.value + step
                alpha = (-offset.value / sidePx * 3f).coerceIn(0f, 1f)
            })

            AnimatedVisibility(
                visible = seekHint != 0,
                enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f),
                exit = fadeOut(tween(250)),
                modifier = Modifier
                    .align(if (seekHint < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 20.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = if (seekHint < 0) "−${seekStepMs / 1000}" else "+${seekStepMs / 1000}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverImage(
    item: MediaMetadata?,
    colors: GerasPlayerColors,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (elevated) Modifier.shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .clip(shape)
            .background(colors.surface.compositeOver(colors.background)),
    ) {
        if (item?.thumbnailUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
