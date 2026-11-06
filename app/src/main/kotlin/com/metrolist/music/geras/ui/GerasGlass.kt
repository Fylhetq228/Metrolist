package com.metrolist.music.geras.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.geras.GerasGlassBlurKey
import com.metrolist.music.geras.GerasGlassEnabledKey
import com.metrolist.music.geras.GerasGlassOpacityKey
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.utils.rememberPreference
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Shared backdrop for every glass surface in the activity. */
object GerasGlass {
    val state = HazeState()
}

/** Marks the content that glass surfaces blur. Applied to the main content in MainActivity. */
fun Modifier.gerasGlassSource(): Modifier = hazeSource(GerasGlass.state)

/**
 * "Liquid glass": blurred backdrop, a tint from the theme, a hairline highlight on the
 * edge and a faint top sheen. Falls back to a plain translucent fill when disabled.
 */
@Composable
fun Modifier.gerasGlass(shape: Shape, tint: Color = MaterialTheme.colorScheme.surfaceContainer): Modifier {
    val enabled by rememberPreference(GerasGlassEnabledKey, true)
    val opacity by rememberPreference(GerasGlassOpacityKey, 0.55f)
    val blur by rememberPreference(GerasGlassBlurKey, 22f)
    val edge = MaterialTheme.colorScheme.onSurface
    val base = this
        .clip(shape)
        .then(
            if (enabled) {
                Modifier.hazeEffect(
                    state = GerasGlass.state,
                    style = HazeStyle(
                        backgroundColor = tint,
                        tints = listOf(HazeTint(tint.copy(alpha = opacity))),
                        blurRadius = blur.dp,
                        noiseFactor = 0.04f,
                    ),
                )
            } else {
                Modifier.background(tint.copy(alpha = 0.96f))
            },
        )
        .background(
            Brush.verticalGradient(
                0f to edge.copy(alpha = 0.07f),
                0.5f to Color.Transparent,
            ),
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(edge.copy(alpha = 0.16f), edge.copy(alpha = 0.04f))),
            shape = shape,
        )
    return base
}

@Composable
fun GerasNavBar(
    navigationItems: List<Screens>,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens) -> Unit,
    onItemLongClick: (Screens) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(62.dp)
                .gerasGlass(RoundedCornerShape(50)),
        ) {
            navigationItems.forEach { screen ->
                val selected = isSelected(screen)
                val bg by animateFloatAsState(if (selected) 1f else 0f, spring(), label = "navSel")
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 64.dp, height = 46.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f * bg))
                        .pointerInput(screen) {
                            detectTapGestures(
                                onTap = { onItemClick(screen) },
                                onLongPress = { onItemLongClick(screen) },
                            )
                        },
                ) {
                    Icon(
                        painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f + 0.45f * bg),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun GerasMiniPlayer(
    positionState: MutableLongState,
    durationState: MutableLongState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val canNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val canPrev by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val liked = currentSong?.song?.liked == true
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val scheme = MaterialTheme.colorScheme

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(58.dp)
                .gerasGlass(RoundedCornerShape(50), tint = scheme.surfaceContainerHigh)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .pointerInput(canNext, canPrev) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + amount * 0.6f) }
                        },
                        onDragEnd = {
                            val v = offset.value
                            when {
                                v < -size.width * 0.18f && canNext -> playerConnection.seekToNext()
                                v > size.width * 0.18f && canPrev -> playerConnection.seekToPrevious()
                            }
                            scope.launch { offset.animateTo(0f, spring()) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    )
                }
                .padding(start = 7.dp, end = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { translationX = offset.value * 0.4f }
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = metadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                ProgressRing(positionState, durationState, scheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = offset.value
                        alpha = 1f - (abs(offset.value) / 400f).coerceIn(0f, 0.7f)
                    },
            ) {
                Text(
                    text = metadata?.title.orEmpty(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata?.artists?.joinToString { it.name }.orEmpty(),
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MiniButton(if (liked) R.drawable.favorite else R.drawable.favorite_border, if (liked) scheme.error else scheme.onSurface) {
                playerConnection.toggleLike()
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { playerConnection.togglePlayPause() },
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
                    label = "miniPlay",
                ) { playing ->
                    Icon(
                        painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = scheme.onSurface,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(position: MutableLongState, duration: MutableLongState, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val d = duration.longValue
        val p = if (d > 0) (position.longValue.toFloat() / d).coerceIn(0f, 1f) else 0f
        val stroke = 2.5.dp.toPx()
        drawArc(Color.Black.copy(alpha = 0.35f), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
        drawArc(color, -90f, 360f * p, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
    }
}

@Composable
private fun MiniButton(icon: Int, tint: Color, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
